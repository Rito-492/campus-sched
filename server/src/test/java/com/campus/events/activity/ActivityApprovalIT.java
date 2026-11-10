package com.campus.events.activity;

import com.campus.events.CampusEventsApplication;
import com.campus.events.auth.SessionService;
import com.campus.events.contract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.junit.jupiter.api.Assertions.*;

/** API-18空意见回归，使用真实Account/Activity/Scheduling/Change/Event生产端口和PostgreSQL。 */
@SpringBootTest(classes=CampusEventsApplication.class)
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(ActivityApprovalIT.FixedTime.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActivityApprovalIT {
    static final Instant NOW=Instant.parse("2030-01-01T00:00:00Z");
    static final String SCHEMA="an_"+UUID.randomUUID().toString().replace("-", "");
    static String url,username,password;
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock approvalClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        url=required("TEST_DATABASE_URL"); username=required("TEST_DB_USERNAME"); password=required("TEST_DB_PASSWORD");
        if(!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/campus_test"))
            throw new IllegalStateException("仅允许本机campus_test随机schema");
        properties.add("spring.datasource.url",()->url+"?currentSchema="+SCHEMA);
        properties.add("spring.datasource.username",()->username);
        properties.add("spring.datasource.password",()->password);
        properties.add("spring.flyway.schemas",()->SCHEMA);
        properties.add("spring.flyway.default-schema",()->SCHEMA);
        properties.add("app.demo-seed",()->"false");
    }
    static String required(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank()) throw new IllegalStateException("缺少"+name);
        return value;
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc http;
    @Autowired PasswordEncoder encoder;
    @Autowired SessionService sessions;
    @Autowired ThreadPoolTaskScheduler scheduler;
    UUID owner,reviewer,admin,venue;
    String ownerToken,reviewerToken,adminToken;
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE organization,resource CASCADE");
        UUID org=UUID.randomUUID(); owner=UUID.randomUUID(); reviewer=UUID.randomUUID(); admin=UUID.randomUUID(); venue=UUID.randomUUID();
        jdbc.update("INSERT INTO organization(id,name) VALUES (?,?)",org,"AN审批回归");
        String loginPassword=UUID.randomUUID().toString();
        for(UUID id:List.of(owner,reviewer,admin)) {
            jdbc.update("INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity) VALUES (?,?,?,?,?,'TEACHER')",
                    id,org,id.toString(),"审批回归账号",encoder.encode(loginPassword));
        }
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'APPLICANT'),(?,'APPROVER'),(?,'ADMIN')",owner,reviewer,admin);
        jdbc.update("INSERT INTO resource(id,kind,name,category,capacity,quantity,open_from,open_to) VALUES (?,'VENUE','审批场地','VENUE',20,1,'08:00','22:00')",venue);
        jdbc.update("INSERT INTO user_approval_scope(user_id,organization_id,resource_category) VALUES (?,?,'VENUE')",reviewer,org);
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'RESOURCE_CONFIRM')",admin);
        jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?)",admin,venue);
        ownerToken=sessions.login(new LoginInput(owner.toString(),loginPassword)).token();
        reviewerToken=sessions.login(new LoginInput(reviewer.toString(),loginPassword)).token();
        adminToken=sessions.login(new LoginInput(admin.toString(),loginPassword)).token();
    }
    @AfterAll void cleanup() {
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.shutdown();
        if(url!=null && SCHEMA.matches("an_[0-9a-f]{32}"))
            new JdbcTemplate(new DriverManagerDataSource(url,username,password)).execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");
    }
    JsonNode request(String path,String token,Object body,UUID key,int status) throws Exception {
        var response=http.perform(MockMvcRequestBuilders.request(HttpMethod.POST,"/api/v1/activities"+path)
                .header("Authorization","Bearer "+token).header("Idempotency-Key",key)
                .contentType("application/json").content(mapper.writeValueAsBytes(body))).andReturn().getResponse();
        JsonNode json=mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(status,response.getStatus(),json.toString());
        assertTrue(json.hasNonNull("requestId"));
        return json;
    }
    UUID pending() throws Exception {
        var plan=new Plan(NOW.plusSeconds(3600),NOW.plusSeconds(7200),0,0,venue,List.of());
        var created=request("",ownerToken,new ActivityInput("空意见审批","",10,1,List.of(),plan),UUID.randomUUID(),201);
        UUID id=UUID.fromString(created.at("/data/id").asText());
        request("/"+id+"/submit",ownerToken,new VersionCommand(0),UUID.randomUUID(),200);
        return id;
    }
    @ParameterizedTest @ValueSource(strings={"", " \t ", "同意开展"})
    void approvalPersistsOriginalCommentAndAllowsFirstReservation(String comment) throws Exception {
        UUID id=pending(),key=UUID.randomUUID();
        var command=new ApprovalCommand(1,Decision.APPROVE,comment);
        var approved=request("/"+id+"/approver-decision",reviewerToken,command,key,200);
        assertEquals("APPROVED_UNRESERVED",approved.at("/data/status").asText());
        assertEquals(comment,jdbc.queryForObject("SELECT comment FROM activity_approval WHERE activity_id=?",String.class,id));
        String summary=comment.isBlank()?"活动审批通过":comment;
        assertEquals(summary,jdbc.queryForObject("SELECT summary FROM audit_log WHERE activity_id=? AND event_type='ACTIVITY_APPROVED'",String.class,id));
        assertEquals(summary,jdbc.queryForObject("SELECT payload_json->>'body' FROM outbox_event WHERE activity_id=? AND event_type='ACTIVITY_APPROVED'",String.class,id));
        assertEquals(approved,request("/"+id+"/approver-decision",reviewerToken,command,key,200));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity_approval WHERE activity_id=?",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE activity_id=? AND event_type='ACTIVITY_APPROVED'",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=?",Integer.class,id));
        var scheduled=request("/"+id+"/resource-confirm",adminToken,new VersionCommand(2),UUID.randomUUID(),200);
        assertEquals("SCHEDULED",scheduled.at("/data/status").asText());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE' AND version_no=1",Integer.class,id));
    }
    @ParameterizedTest @ValueSource(strings={"", " \t "})
    void rejectionStillRequiresCommentWithoutPartialWrites(String comment) throws Exception {
        UUID id=pending();
        var error=request("/"+id+"/approver-decision",reviewerToken,new ApprovalCommand(1,Decision.REJECT,comment),UUID.randomUUID(),400);
        assertEquals("VALIDATION_ERROR",error.at("/error/code").asText());
        assertEquals("PENDING_APPROVER",jdbc.queryForObject("SELECT status FROM activity WHERE id=?",String.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT lock_version FROM activity WHERE id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM activity_approval WHERE activity_id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE activity_id=? AND event_type='ACTIVITY_REJECTED'",Integer.class,id));
    }
}
