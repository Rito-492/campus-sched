package com.campus.events.activity;

import com.campus.events.CampusEventsApplication;
import com.campus.events.auth.SessionService;
import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 真PostgreSQL、真实认证/幂等/SchedulingPort；CM两个未交付端口仅在本测试上下文替身。 */
@SpringBootTest(classes=CampusEventsApplication.class)
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(ActivityRegistrationIT.Ports.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ActivityRegistrationIT {
    static final Instant NOW=Instant.parse("2030-01-01T00:00:00Z");
    static final String SCHEMA="an_"+UUID.randomUUID().toString().replace("-","");
    static String url,user,password;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ActivityService service;
    @Autowired ActivityPort port;
    @Autowired AccountPort accounts;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired SessionService sessions;
    @Autowired MockMvc http;
    @Autowired CommandExecutor commands;
    @Autowired SchedulingPort scheduling;
    @Autowired TestEvents events;
    UUID org,owner,reviewer,admin,student,venue,equipment;
    String loginPassword;
    static String required(String key) {
        var value=System.getenv(key);
        if(value==null||value.isBlank()) throw new IllegalStateException("AN真实库测试缺少环境变量 "+key);
        return value;
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        url=required("TEST_DATABASE_URL"); user=required("TEST_DB_USERNAME"); password=required("TEST_DB_PASSWORD");
        if(!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/campus_test")) throw new IllegalStateException("仅允许本机campus_test独立schema");
        new JdbcTemplate(new DriverManagerDataSource(url,user,password)).execute("CREATE SCHEMA "+SCHEMA);
        properties.add("spring.datasource.url",()->url+"?currentSchema="+SCHEMA);
        properties.add("spring.datasource.username",()->user); properties.add("spring.datasource.password",()->password);
        properties.add("spring.flyway.schemas",()->SCHEMA); properties.add("spring.flyway.default-schema",()->SCHEMA);
        properties.add("app.demo-seed",()->"false");
    }
    @AfterAll void cleanup() {
        if(url!=null && SCHEMA.matches("an_[0-9a-f]{32}")) new JdbcTemplate(new DriverManagerDataSource(url,user,password)).execute("DROP SCHEMA "+SCHEMA+" CASCADE");
    }
    @TestConfiguration static class Ports {
        @Bean @Primary Clock anIntegrationClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
        @Bean ChangePort changePort(JdbcTemplate db) { return new ChangePort() {
            public UUID pendingId(UUID id) {
                var rows=db.query("SELECT id FROM change_request WHERE activity_id=? AND status IN ('AWAITING_CONFIRMATIONS','PENDING_APPROVER','PENDING_RESOURCE')",(rs,n)->rs.getObject(1,UUID.class),id);
                return rows.isEmpty()?null:rows.getFirst();
            }
            public boolean hasPending(UUID id) { return pendingId(id)!=null; }
            public void cancelPending(UUID id,UUID actor,String reason) {
                db.query("SELECT id FROM change_request WHERE activity_id=? FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),id);
                db.update("UPDATE change_request SET status='CANCELLED_BY_ACTIVITY' WHERE activity_id=? AND status IN ('AWAITING_CONFIRMATIONS','PENDING_APPROVER','PENDING_RESOURCE')",id);
            }
        }; }
        @Bean TestEvents eventPort(JdbcTemplate db,ObjectMapper mapper) { return new TestEvents(db,mapper); }
    }
    static class TestEvents implements EventPort {
        final JdbcTemplate db; final ObjectMapper mapper; volatile boolean fail;
        TestEvents(JdbcTemplate db,ObjectMapper mapper) { this.db=db; this.mapper=mapper; }
        public UUID append(EventDraft event) {
            UUID id=UUID.randomUUID();
            db.update("INSERT INTO audit_log(id,activity_id,actor_id,event_type,version_no,summary) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),event.activityId(),event.actorId(),event.eventType(),event.versionNo(),event.summary());
            try { db.update("INSERT INTO outbox_event(id,activity_id,version_no,event_type,payload_json,status,attempts,next_attempt_at) VALUES (?,?,?,?,?::jsonb,'PENDING',0,now())",
                    id,event.activityId(),event.versionNo(),event.eventType(),mapper.writeValueAsString(Map.of("recipientIds",event.recipientIds(),"title",event.title(),"body",event.body()))); }
            catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
            if(fail) throw new IllegalStateException("测试事件失败注入");
            return id;
        }
    }
    @BeforeEach void seed() {
        events.fail=false; jdbc.execute("TRUNCATE organization,resource CASCADE");
        org=UUID.randomUUID(); jdbc.update("INSERT INTO organization(id,name) VALUES (?,?)",org,"AN独立测试组织");
        loginPassword=UUID.randomUUID().toString();
        owner=account("TEACHER","APPLICANT"); reviewer=account("TEACHER","APPROVER"); admin=account("TEACHER","ADMIN"); student=account("STUDENT","APPLICANT");
        venue=UUID.randomUUID(); equipment=UUID.randomUUID();
        jdbc.update("INSERT INTO resource(id,kind,name,category,capacity,quantity,open_from,open_to) VALUES (?,'VENUE','场地','VENUE',50,1,'08:00','22:00'),(?,'EQUIPMENT','设备','PROJECTOR',NULL,2,'08:00','22:00')",venue,equipment);
        jdbc.update("INSERT INTO user_approval_scope(user_id,organization_id,resource_category) VALUES (?,?,'VENUE'),(?,?,'PROJECTOR')",reviewer,org,reviewer,org);
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'RESOURCE_CONFIRM')",admin);
        jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?),(?,?)",admin,venue,admin,equipment);
    }
    UUID account(String identity,String role) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity) VALUES (?,?,?,?,?,?)",id,org,id.toString(),"AN测试账号",encoder.encode(loginPassword),identity);
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)",id,role); return id;
    }
    Actor actor(UUID id) { return accounts.get(id); }
    ActivityInput input() { return new ActivityInput("AN测试活动","安排",10,1,List.of(student),new Plan(NOW.plusSeconds(3600),NOW.plusSeconds(7200),0,0,venue,List.of(new EquipmentNeed(equipment,1)))); }
    ActivityView create() { return tx.execute(s->service.create(actor(owner),input())); }
    ActivityView submit(ActivityView a) { return tx.execute(s->service.submit(actor(owner),a.id(),new VersionCommand(a.lockVersion()))); }
    ActivityView approved(ActivityView a) { return tx.execute(s->service.decide(actor(reviewer),a.id(),new ApprovalCommand(a.lockVersion(),Decision.APPROVE,""))); }
    ActivityView confirmed(ActivityView a) { return tx.execute(s->service.confirm(actor(admin),a.id(),new VersionCommand(a.lockVersion()))); }
    ActivityView open() {
        var a=confirmed(approved(submit(create()))); return tx.execute(s->service.publish(actor(owner),a.id(),new VersionCommand(a.lockVersion())));
    }
    int count(String table,UUID activity) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE activity_id=?",Integer.class,activity); }
    ApiException error(Runnable action,String code) { var ex=assertThrows(ApiException.class,action::run); assertEquals(code,ex.code()); return ex; }
    @Test void draftHttpUsesSharedReceiptAndPersistsImmutableVersion() throws Exception {
        String token=sessions.login(new LoginInput(owner.toString(),loginPassword)).token();
        var requestJson=mapper.writeValueAsString(input()); UUID key=UUID.randomUUID();
        var first=http.perform(post("/api/v1/activities").header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(requestJson)).andReturn().getResponse();
        assertEquals(201,first.getStatus()); var envelope=mapper.readTree(first.getContentAsString()); assertTrue(envelope.has("requestId")); assertFalse(envelope.get("data").has("data"));
        var replay=http.perform(post("/api/v1/activities").header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(requestJson)).andReturn().getResponse();
        assertEquals(envelope,mapper.readTree(replay.getContentAsString()));
        UUID id=UUID.fromString(envelope.at("/data/id").asText()); assertEquals(1,count("activity_version",id));
        var edited=tx.execute(s->service.edit(actor(owner),id,new ActivityEdit(0,input()))); assertEquals(2,edited.versionNo());
        error(()->tx.execute(s->service.edit(actor(owner),id,new ActivityEdit(0,input()))),"STALE_VERSION");
        assertEquals(2,count("activity_version",id)); assertEquals(2,port.get(id).activity().versionNo());
    }
    @Test void fullLifecycleBindsApprovalAndPersistsReservationsAndPrivacy() {
        var pending=submit(create()); assertEquals(ActivityStatus.PENDING_APPROVER,pending.status()); assertEquals(0,count("reservation",pending.id()));
        var approved=approved(pending); assertEquals(0,count("reservation",pending.id()));
        assertEquals(1,jdbc.queryForObject("SELECT version_no FROM activity_approval WHERE activity_id=?",Integer.class,pending.id()));
        var scheduled=confirmed(approved); assertEquals(2,count("reservation",scheduled.id()));
        var opened=tx.execute(s->service.publish(actor(owner),scheduled.id(),new VersionCommand(scheduled.lockVersion())));
        assertEquals(ActivityStatus.OPEN,opened.status()); assertEquals(List.of(),service.get(actor(student),opened.id()).input().staffUserIds());
        assertEquals(List.of(student),port.get(opened.id()).activity().input().staffUserIds());
        assertEquals(1,((Map<?,?>)service.list(actor(reviewer),"review",null).body()).get("data") instanceof List<?> list ? list.size():-1);
    }
    @Test void rejectReturnsToDraftAndWithdrawCreatesNewVersion() {
        var pending=submit(create()); var rejected=tx.execute(s->service.decide(actor(reviewer),pending.id(),new ApprovalCommand(pending.lockVersion(),Decision.REJECT,"需修改")));
        assertEquals(ActivityStatus.DRAFT,rejected.status()); assertEquals(1,rejected.versionNo());
        var again=submit(rejected); var withdrawn=tx.execute(s->service.withdraw(actor(owner),again.id(),new ReasonCommand(again.lockVersion(),"重写")));
        assertEquals(ActivityStatus.DRAFT,withdrawn.status()); assertEquals(2,withdrawn.versionNo()); assertEquals(2,count("activity_version",withdrawn.id()));
    }
    @Test void qualificationAndCurrentScopeAreRecheckedWithoutPartialReservation() {
        var draft=create(); jdbc.update("UPDATE resource SET allow_teacher=false WHERE id=?",equipment);
        error(()->submit(draft),"RESOURCE_INELIGIBLE"); assertEquals(ActivityStatus.DRAFT,port.get(draft.id()).activity().status());
        jdbc.update("UPDATE resource SET allow_teacher=true WHERE id=?",equipment); var approved=approved(submit(draft));
        jdbc.update("DELETE FROM user_resource_scope WHERE user_id=? AND resource_id=?",admin,equipment);
        error(()->confirmed(approved),"FORBIDDEN"); assertEquals(0,count("reservation",approved.id()));
        jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?)",admin,equipment);
        jdbc.update("UPDATE app_user SET identity='STUDENT' WHERE id=?",owner); jdbc.update("UPDATE resource SET allow_student=false WHERE id=?",venue);
        error(()->confirmed(approved),"RESOURCE_INELIGIBLE"); assertEquals(0,count("reservation",approved.id()));
    }
    @Test void onlyOneConcurrentStudentGetsLastPlace() throws Exception {
        var a=open(); var students=new ArrayList<UUID>(); for(int i=0;i<20;i++) students.add(account("STUDENT","APPLICANT"));
        var ready=new CountDownLatch(20); var start=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(20);
        try {
            var futures=students.stream().map(id->pool.submit(()->{ Actor actor=actor(id); ready.countDown(); assertTrue(start.await(20,TimeUnit.SECONDS));
                try { tx.execute(s->service.registration(actor,a.id(),new RegistrationCommand(RegistrationAction.JOIN,1))); return "OK"; }
                catch(ApiException e) { return e.code(); } })).toList();
            assertTrue(ready.await(20,TimeUnit.SECONDS)); start.countDown(); var outcomes=new ArrayList<String>();
            for(var future:futures) outcomes.add(future.get(40,TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(outcomes,"OK")); assertEquals(19,Collections.frequency(outcomes,"CAPACITY_FULL"));
            assertEquals(1,port.get(a.id()).activity().registrationCount()); assertEquals(a.lockVersion()+1,port.get(a.id()).activity().lockVersion());
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    @Test void repeatJoinAndWithdrawAreIdempotentAndFreezeRejectsBoth() {
        var a=open(); var join=new RegistrationCommand(RegistrationAction.JOIN,1);
        tx.execute(s->service.registration(actor(student),a.id(),join)); tx.execute(s->service.registration(actor(student),a.id(),join));
        assertEquals(a.lockVersion()+1,port.get(a.id()).activity().lockVersion());
        pending(a.id());
        error(()->tx.execute(s->service.registration(actor(student),a.id(),join)),"CHANGE_PENDING");
        error(()->tx.execute(s->service.registration(actor(student),a.id(),new RegistrationCommand(RegistrationAction.WITHDRAW,1))),"CHANGE_PENDING");
        jdbc.update("UPDATE change_request SET status='WITHDRAWN' WHERE activity_id=?",a.id());
        tx.execute(s->service.registration(actor(student),a.id(),new RegistrationCommand(RegistrationAction.WITHDRAW,1)));
        tx.execute(s->service.registration(actor(student),a.id(),new RegistrationCommand(RegistrationAction.WITHDRAW,1)));
        assertEquals(0,port.get(a.id()).activity().registrationCount()); assertEquals(a.lockVersion()+2,port.get(a.id()).activity().lockVersion());
    }
    void pending(UUID id) {
        try { jdbc.update("INSERT INTO change_request(id,activity_id,base_version_no,target_plan_json,impact_json,reason,status,created_by) VALUES (?,?,1,?::jsonb,'{}'::jsonb,'测试改期','PENDING_RESOURCE',?)",UUID.randomUUID(),id,mapper.writeValueAsString(input().plan()),owner); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    @Test void cancelClosesPendingReleasesAllAndCannotResurrect() {
        var a=open(); tx.execute(s->service.registration(actor(student),a.id(),new RegistrationCommand(RegistrationAction.JOIN,1))); pending(a.id());
        var current=port.get(a.id()).activity(); var cancelled=tx.execute(s->service.cancel(actor(owner),a.id(),new ReasonCommand(current.lockVersion(),"取消活动")));
        assertEquals(ActivityStatus.CANCELLED,cancelled.status()); assertEquals(0,cancelled.registrationCount());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE'",Integer.class,a.id()));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM activity_registration WHERE activity_id=?",String.class,a.id()));
        assertEquals("CANCELLED_BY_ACTIVITY",jdbc.queryForObject("SELECT status FROM change_request WHERE activity_id=?",String.class,a.id()));
        assertEquals(cancelled.lockVersion(),tx.execute(s->service.cancel(actor(owner),a.id(),new ReasonCommand(0,"重试"))).lockVersion());
        error(()->tx.execute(s->{ port.lock(a.id()); return port.activatePlan(a.id(),1,input().plan(),owner); }),"INVALID_STATE");
    }
    @Test void eventFailureRollsBackActivityReservationsAndReceipt() {
        var approved=approved(submit(create())); events.fail=true;
        UUID key=UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->commands.execute(actor(admin),key,"POST /api/v1/activities/"+approved.id()+"/resource-confirm","{\"expectedLockVersion\":"+approved.lockVersion()+"}",
                ()->Responses.success(200,service.confirm(actor(admin),approved.id(),new VersionCommand(approved.lockVersion())))));
        assertEquals(ActivityStatus.APPROVED_UNRESERVED,port.get(approved.id()).activity().status()); assertEquals(0,count("reservation",approved.id()));
        assertEquals(2,count("outbox_event",approved.id()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?",Integer.class,admin,key.toString()));
    }
    @Test void adapterRequiresTransactionAndExplicitLockAndCopiesMetadata() {
        var a=open(); tx.execute(s->service.registration(actor(student),a.id(),new RegistrationCommand(RegistrationAction.JOIN,1)));
        assertThrows(IllegalTransactionStateException.class,()->port.lock(a.id()));
        assertThrows(IllegalStateException.class,()->tx.execute(s->port.activatePlan(a.id(),1,input().plan(),owner)));
        tx.executeWithoutResult(s->{ port.lock(a.id()); var old=port.get(a.id()).activity();
            var target=new Plan(NOW.plusSeconds(10800),NOW.plusSeconds(14400),0,0,venue,List.of());
            accounts.lock(owner); assertTrue(scheduling.lockAndCheck(a.id(),target,10,Identity.TEACHER).feasible());
            int next=port.activatePlan(a.id(),1,target,owner); scheduling.replaceReservations(a.id(),next,target);
            assertEquals(2,next); assertEquals(old.input().title(),port.get(a.id()).activity().input().title());
        });
        assertEquals(2,port.get(a.id()).activity().effectiveVersionNo());
        assertEquals(2,jdbc.queryForObject("SELECT version_no FROM activity_registration WHERE activity_id=?",Integer.class,a.id()));
    }
    @Test void latestApprovalScopeAndSelfApprovalAreEnforcedInRealDatabase() {
        var a=submit(create()); Actor stale=actor(reviewer);
        jdbc.update("DELETE FROM user_approval_scope WHERE user_id=? AND resource_category='PROJECTOR'",reviewer);
        error(()->tx.execute(s->service.decide(stale,a.id(),new ApprovalCommand(a.lockVersion(),Decision.APPROVE,""))),"FORBIDDEN");
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'APPROVER')",owner);
        jdbc.update("INSERT INTO user_approval_scope(user_id,organization_id,resource_category) VALUES (?,?,'VENUE'),(?,?,'PROJECTOR')",owner,org,owner,org);
        error(()->tx.execute(s->service.decide(actor(owner),a.id(),new ApprovalCommand(a.lockVersion(),Decision.APPROVE,""))),"FORBIDDEN");
        assertEquals(0,count("activity_approval",a.id())); assertEquals(ActivityStatus.PENDING_APPROVER,port.get(a.id()).activity().status());
    }
    @Test void failedActivationRollsBackVersionPointerAndActiveRegistrationVersion() {
        var a=open(); tx.execute(s->service.registration(actor(student),a.id(),new RegistrationCommand(RegistrationAction.JOIN,1)));
        var before=port.get(a.id()).activity();
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{ port.lock(a.id()); port.activatePlan(a.id(),1,input().plan(),owner); throw new IllegalStateException("测试改期回滚"); }));
        assertEquals(1,port.get(a.id()).activity().versionNo()); assertEquals(before.lockVersion(),port.get(a.id()).activity().lockVersion());
        assertEquals(1,count("activity_version",a.id())); assertEquals(1,jdbc.queryForObject("SELECT version_no FROM activity_registration WHERE activity_id=?",Integer.class,a.id()));
    }
    @Test void cancellationEventFailureRollsBackChangeRegistrationAndReservationsTogether() {
        var a=open(); tx.execute(s->service.registration(actor(student),a.id(),new RegistrationCommand(RegistrationAction.JOIN,1))); pending(a.id());
        var before=port.get(a.id()).activity(); events.fail=true;
        assertThrows(IllegalStateException.class,()->tx.execute(s->service.cancel(actor(owner),a.id(),new ReasonCommand(before.lockVersion(),"测试回滚"))));
        var after=port.get(a.id()).activity(); assertEquals(ActivityStatus.OPEN,after.status()); assertEquals(before.lockVersion(),after.lockVersion());
        assertEquals(1,after.registrationCount()); assertNotNull(after.activeChangeId());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE'",Integer.class,a.id()));
    }
    @RepeatedTest(10) void cancellationAndPlanActivationCannotResurrectActivity() throws Exception {
        var a=open(); var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var activation=pool.submit(()->{ assertTrue(gate.await(10,TimeUnit.SECONDS));
                try { tx.executeWithoutResult(s->{ port.lock(a.id());
                    List.of(owner,admin).stream().sorted(Comparator.comparing(UUID::toString)).forEach(accounts::lock);
                    var plan=new Plan(NOW.plusSeconds(10800),NOW.plusSeconds(14400),0,0,venue,List.of(new EquipmentNeed(equipment,1)));
                    var check=scheduling.lockAndCheck(a.id(),plan,10,Identity.TEACHER); assertTrue(check.feasible());
                    int version=port.activatePlan(a.id(),1,plan,admin); scheduling.replaceReservations(a.id(),version,plan);
                }); return "APPLIED"; } catch(ApiException e) { return e.code(); }
            });
            var cancellation=pool.submit(()->{ assertTrue(gate.await(10,TimeUnit.SECONDS));
                try { tx.execute(s->service.cancel(actor(owner),a.id(),new ReasonCommand(a.lockVersion(),"竞争取消"))); return "CANCELLED"; }
                catch(ApiException e) { return e.code(); }
            });
            gate.countDown(); assertTrue(Set.of("APPLIED","INVALID_STATE").contains(activation.get(30,TimeUnit.SECONDS)));
            var result=cancellation.get(30,TimeUnit.SECONDS); assertTrue(Set.of("CANCELLED","STALE_VERSION").contains(result),result);
            if(result.equals("STALE_VERSION")) { var current=port.get(a.id()).activity(); tx.execute(s->service.cancel(actor(owner),a.id(),new ReasonCommand(current.lockVersion(),"重读后取消"))); }
            assertEquals(ActivityStatus.CANCELLED,port.get(a.id()).activity().status());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE'",Integer.class,a.id()));
        } finally { gate.countDown(); pool.shutdownNow(); }
    }
    @Test void completeHttpWorkflowUsesAllActivityRoutes() throws Exception {
        var created=request("POST","",owner,input(),201); UUID id=UUID.fromString(created.at("/data/id").asText());
        request("GET","?scope=mine",owner,null,200); request("GET","/"+id,owner,null,200);
        request("PATCH","/"+id,owner,new ActivityEdit(0,input()),200);
        request("POST","/"+id+"/submit",owner,new VersionCommand(1),200);
        request("POST","/"+id+"/withdraw",owner,new ReasonCommand(2,"重新提交"),200);
        request("POST","/"+id+"/submit",owner,new VersionCommand(3),200);
        request("POST","/"+id+"/approver-decision",reviewer,new ApprovalCommand(4,Decision.APPROVE,""),200);
        request("POST","/"+id+"/resource-confirm",admin,new VersionCommand(5),200);
        request("POST","/"+id+"/publish",owner,new VersionCommand(6),200);
        request("PUT","/"+id+"/registration",student,new RegistrationCommand(RegistrationAction.JOIN,3),200);
        request("GET","/"+id+"/registrations",owner,null,200); request("GET","/"+id+"/registrations",student,null,403);
        request("PUT","/"+id+"/registration",student,new RegistrationCommand(RegistrationAction.WITHDRAW,3),200);
        request("POST","/"+id+"/cancel",owner,new ReasonCommand(9,"结束测试"),200);
        assertEquals(ActivityStatus.CANCELLED,port.get(id).activity().status());
    }
    @Test void waitingActivityLockReadsVersionCommittedWhileItWasWaiting() throws Exception {
        var a=open(); var pool=Executors.newFixedThreadPool(2); var changed=new CountDownLatch(1); var commit=new CountDownLatch(1);
        try {
            var writer=pool.submit(()->tx.executeWithoutResult(s->{ port.lock(a.id()); accounts.lock(owner);
                assertTrue(scheduling.lockAndCheck(a.id(),input().plan(),10,Identity.TEACHER).feasible());
                int next=port.activatePlan(a.id(),1,input().plan(),owner); scheduling.replaceReservations(a.id(),next,input().plan()); changed.countDown();
                try { assertTrue(commit.await(15,TimeUnit.SECONDS)); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(changed.await(10,TimeUnit.SECONDS));
            var reader=pool.submit(()->tx.execute(s->port.lock(a.id()).activity().versionNo()));
            Awaitility.await().atMost(10,TimeUnit.SECONDS).until(()->jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%FROM activity%'",Integer.class)>0);
            commit.countDown(); writer.get(15,TimeUnit.SECONDS); assertEquals(2,reader.get(15,TimeUnit.SECONDS));
        } finally { commit.countDown(); pool.shutdownNow(); }
    }
    com.fasterxml.jackson.databind.JsonNode request(String method,String path,UUID actor,Object body,int expected) throws Exception {
        return request(method,path,actor,body,expected,UUID.randomUUID());
    }
    com.fasterxml.jackson.databind.JsonNode request(String method,String path,UUID actor,Object body,int expected,UUID key) throws Exception {
        String token=sessions.login(new LoginInput(actor.toString(),loginPassword)).token();
        var builder=MockMvcRequestBuilders.request(HttpMethod.valueOf(method),"/api/v1/activities"+path)
                .header("Authorization","Bearer "+token);
        if(body!=null) builder.header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(body));
        var response=http.perform(builder).andReturn().getResponse();
        var json=mapper.readTree(response.getContentAsString()); assertEquals(expected,response.getStatus(),json.toString()); return json;
    }
    void replay(String method,String path,UUID actor,Object body) throws Exception {
        UUID key=UUID.randomUUID();
        var first=request(method,path,actor,body,200,key);
        // 重放必须恢复原信封，包括 requestId；不能重新执行业务后生成另一个成功响应。
        assertEquals(first,request(method,path,actor,body,200,key));
    }
    @Test void everyWriteRouteReplaysWithoutDuplicatingVersionsReservationsOrEvents() throws Exception {
        var a=create(); String path="/"+a.id();
        replay("PATCH",path,owner,new ActivityEdit(0,input()));
        replay("POST",path+"/submit",owner,new VersionCommand(1));
        replay("POST",path+"/withdraw",owner,new ReasonCommand(2,"重建"));
        replay("POST",path+"/submit",owner,new VersionCommand(3));
        replay("POST",path+"/approver-decision",reviewer,new ApprovalCommand(4,Decision.APPROVE,""));
        replay("POST",path+"/resource-confirm",admin,new VersionCommand(5));
        replay("POST",path+"/publish",owner,new VersionCommand(6));
        replay("PUT",path+"/registration",student,new RegistrationCommand(RegistrationAction.JOIN,3));
        assertEquals(1,port.get(a.id()).activity().registrationCount());
        replay("PUT",path+"/registration",student,new RegistrationCommand(RegistrationAction.WITHDRAW,3));
        int eventsBefore=count("outbox_event",a.id());
        replay("POST",path+"/cancel",owner,new ReasonCommand(9,"结束"));
        assertEquals(eventsBefore+1,count("outbox_event",a.id()));
        assertEquals(10,port.get(a.id()).activity().lockVersion());
        assertEquals(3,count("activity_version",a.id()));
        assertEquals(1,count("activity_approval",a.id()));
        assertEquals(2,count("reservation",a.id()));
        assertEquals(1,count("activity_registration",a.id()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE'",Integer.class,a.id()));
    }
    @Test void registrationKeyCannotBeReusedForAnotherBodyOrRoute() throws Exception {
        var a=open(); UUID key=UUID.randomUUID(); String path="/"+a.id();
        var join=new RegistrationCommand(RegistrationAction.JOIN,1);
        var original=request("PUT",path+"/registration",student,join,200,key);
        var changed=request("PUT",path+"/registration",student,new RegistrationCommand(RegistrationAction.WITHDRAW,1),409,key);
        assertEquals("IDEMPOTENCY_KEY_REUSED",changed.at("/error/code").asText());
        var otherRoute=request("POST",path+"/cancel",student,new ReasonCommand(0,"不同路由"),409,key);
        assertEquals("IDEMPOTENCY_KEY_REUSED",otherRoute.at("/error/code").asText());
        assertEquals(original,request("PUT",path+"/registration",student,join,200,key));
        assertEquals(1,port.get(a.id()).activity().registrationCount());
        assertEquals(a.lockVersion()+1,port.get(a.id()).activity().lockVersion());
    }
    @Test void pastPlanCannotAcquireReservationsAtConfirmation() throws Exception {
        var past=new ActivityInput("过去安排","",10,1,List.of(),new Plan(NOW.minusSeconds(3600),NOW,0,0,venue,List.of()));
        var created=request("POST","",owner,past,201);
        UUID id=UUID.fromString(created.at("/data/id").asText());
        request("POST","/"+id+"/submit",owner,new VersionCommand(0),200);
        request("POST","/"+id+"/approver-decision",reviewer,new ApprovalCommand(1,Decision.APPROVE,""),200);
        int before=count("outbox_event",id);
        var rejected=request("POST","/"+id+"/resource-confirm",admin,new VersionCommand(2),409);
        assertEquals("INVALID_STATE",rejected.at("/error/code").asText());
        assertEquals("活动已经开始",rejected.at("/error/message").asText());
        assertEquals(ActivityStatus.APPROVED_UNRESERVED,port.get(id).activity().status());
        assertEquals(2,port.get(id).activity().lockVersion());
        assertEquals(0,count("reservation",id)); assertEquals(before,count("outbox_event",id));
    }
    @Test void invalidCapacityCreateAndEditLeaveNoPartialState() throws Exception {
        var a=create();
        for(int capacity:List.of(0,-1,11)) {
            var invalid=new ActivityInput("非法容量","",10,capacity,List.of(),input().plan());
            var createError=request("POST","",owner,invalid,400);
            var editError=request("PATCH","/"+a.id(),owner,new ActivityEdit(0,invalid),400);
            for(var response:List.of(createError,editError)) {
                assertEquals("VALIDATION_ERROR",response.at("/error/code").asText());
                assertFalse(response.at("/error/message").asText().isBlank());
                assertTrue(response.hasNonNull("requestId")); assertFalse(response.has("data"));
            }
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity",Integer.class));
        assertEquals(1,count("activity_version",a.id())); assertEquals(0,port.get(a.id()).activity().lockVersion());
        assertEquals(0,count("outbox_event",a.id()));
    }
    @Test void mineHttpUnionsOwnershipStaffAndEveryRegistrationHistoryWithoutDuplicates() throws Exception {
        UUID outsider=account("STUDENT","APPLICANT");
        var owned=create(); // owner 分支。
        var staffActivity=tx.execute(s->service.create(actor(outsider),new ActivityInput("工作人员活动","",10,1,List.of(owner),input().plan())));
        var historyActivity=tx.execute(s->service.create(actor(outsider),new ActivityInput("历史报名活动","",10,1,List.of(),input().plan())));
        jdbc.update("INSERT INTO activity_registration(activity_id,user_id,status,version_no) VALUES (?,?,'WITHDRAWN',1),(?,?,'ACTIVE',1)",historyActivity.id(),owner,owned.id(),owner);
        create(); // 另一场本人活动仍应显示。
        tx.execute(s->service.create(actor(outsider),new ActivityInput("无关活动","",10,1,List.of(),input().plan())));
        var result=request("GET","?scope=mine",owner,null,200);
        var ids=new HashSet<String>(); result.get("data").forEach(item->ids.add(item.get("id").asText()));
        assertEquals(4,result.get("data").size()); assertEquals(4,ids.size());
        assertTrue(ids.containsAll(List.of(owned.id().toString(),staffActivity.id().toString(),historyActivity.id().toString())));
        var forbidden=request("GET","?scope=mine",admin,null,403);
        assertEquals("FORBIDDEN",forbidden.at("/error/code").asText());
        var filtered=request("GET","?scope=mine&status=OPEN",owner,null,200); assertEquals(0,filtered.get("data").size());
    }
    @Test void cancelledRegistrantCanReadDetailsAndMyListButNotRosterOrUnrelatedPrivateActivities() throws Exception {
        UUID participant=account("STUDENT","APPLICANT"); var a=open();
        tx.execute(s->service.registration(actor(participant),a.id(),new RegistrationCommand(RegistrationAction.JOIN,1)));
        var current=port.get(a.id()).activity(); tx.execute(s->service.cancel(actor(owner),a.id(),new ReasonCommand(current.lockVersion(),"取消")));
        var detail=request("GET","/"+a.id(),participant,null,200);
        assertEquals("CANCELLED",detail.at("/data/status").asText()); assertEquals("CANCELLED",detail.at("/data/myRegistrationStatus").asText());
        assertEquals(0,detail.at("/data/input/staffUserIds").size());
        var mine=request("GET","?scope=mine",participant,null,200); assertEquals(a.id().toString(),mine.at("/data/0/id").asText());
        request("GET","/"+a.id()+"/registrations",participant,null,403);
        var privateDraft=create(); request("GET","/"+privateDraft.id(),participant,null,403);
    }
    @Test void httpPublishPastActivityAndRiskAreRejectedWithErrorEnvelopesAndNoMutation() throws Exception {
        var a=confirmed(approved(submit(create())));
        jdbc.update("INSERT INTO resource_fault(id,resource_id,start_at,end_at,unavailable_quantity,reason,created_by) VALUES (?,?,?, ?,1,'发布风险测试',?)",
                UUID.randomUUID(),venue,java.sql.Timestamp.from(input().plan().startAt()),java.sql.Timestamp.from(input().plan().endAt()),admin);
        var risk=request("POST","/"+a.id()+"/publish",owner,new VersionCommand(a.lockVersion()),409);
        assertEquals("RESOURCE_CONFLICT",risk.at("/error/code").asText()); assertTrue(risk.at("/error/details").isObject());
        assertTrue(risk.hasNonNull("requestId")); assertFalse(risk.has("data")); assertFalse(risk.at("/error/message").asText().isBlank());
        assertEquals(a.lockVersion(),port.get(a.id()).activity().lockVersion()); assertEquals(ActivityStatus.SCHEDULED,port.get(a.id()).activity().status());
        // 仅在独立测试表中追加过去版本，不修改现有不可变版本。
        var past=new ActivityInput("过去活动","",10,1,List.of(),new Plan(NOW.minusSeconds(3600),NOW,0,0,venue,List.of()));
        UUID pastId=UUID.randomUUID();
        tx.executeWithoutResult(s->{ jdbc.update("INSERT INTO activity(id,organization_id,owner_id,status,version_no,effective_version_no,lock_version) VALUES (?,?,?,'SCHEDULED',1,1,0)",pastId,org,owner);
            try { jdbc.update("INSERT INTO activity_version(activity_id,version_no,input_json,created_by) VALUES (?,1,?::jsonb,?)",pastId,mapper.writeValueAsString(past),owner); }
            catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        });
        var started=request("POST","/"+pastId+"/publish",owner,new VersionCommand(0),409);
        assertEquals("INVALID_STATE",started.at("/error/code").asText()); assertEquals("活动已经开始",started.at("/error/message").asText());
        assertEquals(0,count("outbox_event",pastId));
    }
    @Test void pendingChangeAllowsPublishAndRemainsVisibleButFreezesRegistration() throws Exception {
        var a=confirmed(approved(submit(create()))); pending(a.id());
        var published=request("POST","/"+a.id()+"/publish",owner,new VersionCommand(a.lockVersion()),200);
        assertEquals("OPEN",published.at("/data/status").asText()); assertTrue(published.at("/data/activeChangeId").isTextual());
        var frozen=request("PUT","/"+a.id()+"/registration",student,new RegistrationCommand(RegistrationAction.JOIN,1),409);
        assertEquals("CHANGE_PENDING",frozen.at("/error/code").asText()); assertTrue(frozen.at("/error/details").isObject()); assertFalse(frozen.has("data"));
        var publicList=request("GET","?scope=public",student,null,200); assertEquals(a.id().toString(),publicList.at("/data/0/id").asText());
        var reviewList=request("GET","?scope=review",reviewer,null,200); assertEquals(a.id().toString(),reviewList.at("/data/0/id").asText());
    }
    @Test void resourceConfirmConflictHttpIncludesStructuredConflictsWithoutPartialReservation() throws Exception {
        var first=confirmed(approved(submit(create()))); var second=approved(submit(create()));
        var conflict=request("POST","/"+second.id()+"/resource-confirm",admin,new VersionCommand(second.lockVersion()),409);
        assertEquals("RESOURCE_CONFLICT",conflict.at("/error/code").asText()); assertFalse(conflict.at("/error/message").asText().isBlank());
        assertTrue(conflict.hasNonNull("requestId")); assertFalse(conflict.has("data"));
        var conflicts=conflict.at("/error/details/conflicts"); assertTrue(conflicts.isArray()); assertTrue(conflicts.size()>0);
        assertEquals("VENUE_OCCUPIED",conflicts.get(0).get("code").asText()); assertEquals(venue.toString(),conflicts.get(0).get("resourceId").asText());
        assertTrue(conflicts.get(0).hasNonNull("startsAt")); assertTrue(conflicts.get(0).hasNonNull("endsAt"));
        assertEquals(0,count("reservation",second.id())); assertEquals(2,count("reservation",first.id()));
        assertEquals(ActivityStatus.APPROVED_UNRESERVED,port.get(second.id()).activity().status());
    }
}
