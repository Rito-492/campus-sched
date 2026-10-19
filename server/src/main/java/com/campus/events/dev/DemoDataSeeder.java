package com.campus.events.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 演示数据种子导入器（配置经理拥有，dev 包；CM-01）。
 *
 * 运行门禁（test-data-plan §5.6）：仅 dev profile 且 {@code app.demo-seed=true}（APP_DEMO_SEED）时运行。
 * 数据来源唯一：{@code test-data/demo.json}（v3.0 三角色/身份/资格）。派生规则见
 * docs/testing/test-data-plan.md §5，本类逐条实现：
 *   1. app_user.password_hash = BCrypt(DEMO_PASSWORD)，enabled=true；user_role 按 roles 展开
 *      （同时按 permissions/approvalScopes/managedResourceIds 展开到对应关系表）；
 *   2. activity.version_no = versionNo；effective_version_no = versionNo（SCHEDULED/OPEN）否则 NULL；lock_version=0；
 *   3. activity_version：每活动一行 (activity_id, version_no, input_json=完整 input, created_by=ownerId)；
 *   4. reservation：SCHEDULED/OPEN 活动按 input.plan 派生 ACTIVE 预约，区间 [startAt−setup, endAt+teardown)，
 *      场地一行 (quantity=1)，每条器材一行；DRAFT/PENDING_APPROVER/APPROVED_UNRESERVED 不派生；
 *   5. 幂等：已存在同 id 记录则校验一致后跳过，不覆盖用户已修改数据。
 *
 * 依赖：PM 后端工程（pom/JdbcTemplate/TransactionTemplate/Spring Security BCrypt PasswordEncoder/Jackson JavaTime）。
 * 编译阻塞：server/pom.xml 尚未交付（PM M0），本文件与 DemoData.java 待其就绪后编译。
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.demo-seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.demo-seed-file:test-data/demo.json}")
    private String demoSeedFile;

    @Value("${app.demo-date:}")
    private String demoDate;

    @Value("${app.demo-password:}")
    private String demoPassword;

    public DemoDataSeeder(JdbcTemplate jdbc,
                          ObjectMapper objectMapper,
                          PasswordEncoder passwordEncoder,
                          TransactionTemplate transactionTemplate) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("APP_DEMO_SEED=true 但未配置 DEMO_PASSWORD，跳过导入（无法生成演示账号口令）");
            return;
        }

        String date = resolveDemoDate();
        Path file = Path.of(demoSeedFile);
        if (!Files.exists(file)) {
            log.warn("未找到种子文件 {}，跳过导入", file.toAbsolutePath());
            return;
        }

        String resolved = Files.readString(file).replace("${DEMO_DATE}", date);
        DemoData data = objectMapper.readValue(resolved, DemoData.class);
        String passwordHash = passwordEncoder.encode(demoPassword);

        transactionTemplate.executeWithoutResult(status -> seed(data, passwordHash));

        log.info("演示数据导入完成：组织 {}、用户 {}、资源 {}、活动 {}、预约 {}、报名 {}（演示日期 {}）",
                organizations, users, resources, activities, reservations, registrations, date);
    }

    // 计数（每次启动重置，仅记录本次新插入条数；已存在跳过的不计入）
    private int organizations;
    private int users;
    private int resources;
    private int activities;
    private int reservations;
    private int registrations;

    private void seed(DemoData data, String passwordHash) {
        organizations = users = resources = activities = reservations = registrations = 0;

        for (var org : data.organizations()) {
            seedOrganization(org);
        }
        // 先建用户基础资料/角色/权限/审批范围，再建资源，最后建资源管理范围授权：
        // user_resource_scope.resource_id 有即时外键（V1__schema.sql §11），须在 resource 存在后插入。
        for (var user : data.users()) {
            seedUserBase(user, passwordHash);
        }
        for (var resource : data.resources()) {
            seedResource(resource);
        }
        for (var user : data.users()) {
            seedUserResourceScopes(user);
        }
        for (var activity : data.activities()) {
            seedActivity(activity);
        }
        for (var registration : data.registrations()) {
            seedRegistration(registration);
        }
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private void seedOrganization(DemoData.Organization org) {
        if (exists("SELECT count(*) FROM organization WHERE id = ?", org.id())) {
            String name = jdbc.queryForObject("SELECT name FROM organization WHERE id = ?", String.class, org.id());
            if (!org.name().equals(name)) {
                log.warn("organization {} 已存在但 name 不一致（库={}，文件={}），跳过不覆盖", org.id(), name, org.name());
            }
            return;
        }
        jdbc.update("INSERT INTO organization (id, name) VALUES (?, ?)", org.id(), org.name());
        organizations++;
    }

    private void seedUserBase(DemoData.User user, String passwordHash) {
        if (exists("SELECT count(*) FROM app_user WHERE id = ?", user.id())) {
            return; // 已存在则跳过，不覆盖用户已修改数据
        }
        jdbc.update("""
                INSERT INTO app_user (id, organization_id, username, display_name, password_hash, enabled, identity)
                VALUES (?, ?, ?, ?, ?, true, ?)
                """,
                user.id(), user.organizationId(), user.username(), user.displayName(), passwordHash, user.identity());
        for (String role : user.roles()) {
            jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", user.id(), role);
        }
        for (String permission : user.permissions()) {
            jdbc.update("INSERT INTO user_permission (user_id, permission) VALUES (?, ?)", user.id(), permission);
        }
        for (var scope : user.approvalScopes()) {
            for (String category : scope.resourceCategories()) {
                jdbc.update("INSERT INTO user_approval_scope (user_id, organization_id, resource_category) VALUES (?, ?, ?)",
                        user.id(), scope.organizationId(), category);
            }
        }
        users++;
    }

    private void seedUserResourceScopes(DemoData.User user) {
        // 依赖 resource 已存在（user_resource_scope.resource_id 即时外键，V1__schema.sql §11），须在 seedResource 之后调用。
        for (UUID resourceId : user.managedResourceIds()) {
            if (exists("SELECT count(*) FROM user_resource_scope WHERE user_id = ? AND resource_id = ?",
                    user.id(), resourceId)) {
                continue;
            }
            jdbc.update("INSERT INTO user_resource_scope (user_id, resource_id) VALUES (?, ?)", user.id(), resourceId);
        }
    }

    private void seedResource(DemoData.Resource resource) {
        if (exists("SELECT count(*) FROM resource WHERE id = ?", resource.id())) {
            return;
        }
        jdbc.update("""
                INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to,
                                     allow_teacher, allow_student, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                resource.id(), resource.kind(), resource.name(), resource.category(),
                resource.capacity(), resource.quantity(),
                LocalTime.parse(resource.openFrom()), LocalTime.parse(resource.openTo()),
                resource.allowTeacher(), resource.allowStudent(), resource.createdAt());
        resources++;
    }

    private void seedActivity(DemoData.Activity activity) {
        if (exists("SELECT count(*) FROM activity WHERE id = ?", activity.id())) {
            return;
        }
        Integer effectiveVersion = isScheduledOrOpen(activity.status()) ? activity.versionNo() : null;
        jdbc.update("""
                INSERT INTO activity (id, organization_id, owner_id, status, version_no, effective_version_no, lock_version)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """,
                activity.id(), activity.organizationId(), activity.ownerId(), activity.status(),
                activity.versionNo(), effectiveVersion);

        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(activity.input());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("序列化 activity.input 失败: " + activity.id(), e);
        }
        jdbc.update("INSERT INTO activity_version (activity_id, version_no, input_json, created_by) VALUES (?, ?, ?::jsonb, ?)",
                activity.id(), activity.versionNo(), inputJson, activity.ownerId());
        activities++;

        seedReservations(activity);
    }

    private void seedReservations(DemoData.Activity activity) {
        if (!isScheduledOrOpen(activity.status())) {
            return; // DRAFT/PENDING_APPROVER/APPROVED_UNRESERVED 不派生预约（首次确认前 0 条 ACTIVE）
        }
        var plan = activity.input().plan();
        OffsetDateTime start = plan.startAt().minusMinutes(plan.setupMinutes());
        OffsetDateTime end = plan.endAt().plusMinutes(plan.teardownMinutes());

        insertReservationIfAbsent(activity.id(), activity.versionNo(), plan.venueId(), start, end, 1);
        for (var equipment : plan.equipment()) {
            insertReservationIfAbsent(activity.id(), activity.versionNo(), equipment.resourceId(), start, end, equipment.quantity());
        }
    }

    private void insertReservationIfAbsent(UUID activityId, int versionNo, UUID resourceId,
                                           OffsetDateTime start, OffsetDateTime end, int quantity) {
        if (exists("SELECT count(*) FROM reservation WHERE activity_id = ? AND version_no = ? AND resource_id = ?",
                activityId, versionNo, resourceId)) {
            return;
        }
        jdbc.update("""
                INSERT INTO reservation (id, activity_id, version_no, resource_id, start_at, end_at, quantity, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """,
                UUID.randomUUID(), activityId, versionNo, resourceId, start, end, quantity);
        reservations++;
    }

    private void seedRegistration(DemoData.Registration registration) {
        if (exists("SELECT count(*) FROM activity_registration WHERE activity_id = ? AND user_id = ?",
                registration.activityId(), registration.userId())) {
            return;
        }
        jdbc.update("INSERT INTO activity_registration (activity_id, user_id, status, version_no) VALUES (?, ?, ?, ?)",
                registration.activityId(), registration.userId(), registration.status(), registration.versionNo());
        registrations++;
    }

    private static boolean isScheduledOrOpen(String status) {
        return "SCHEDULED".equals(status) || "OPEN".equals(status);
    }

    private String resolveDemoDate() {
        if (demoDate != null && !demoDate.isBlank()) {
            return demoDate.trim();
        }
        // 默认：首次导入当天上海日期 + 7 天（03 契约 §1）
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(7).toString();
    }
}
