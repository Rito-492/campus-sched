package com.campus.events.dev;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * demo.json 文件格式模型（配置经理拥有，dev 包）。
 *
 * 仅描述 {@code test-data/demo.json} 的顶层结构（QA 的 test-data-plan §4 定义的数据文件格式），
 * 不是 02 契约 §2 的业务 DTO。业务 DTO（AuthorizationInput/ResourceView/ActivityInput/RegistrationView）
 * 由 PM 的 {@code com.campus.events.contract} 提供；种子导入器以本文件格式解析，写入 activity_version.input_json
 * 时回写的是与契约 ActivityInput 一致的 JSON。待 PM 契约 record 就绪后可按 02 §7 委托统一 record 复核。
 *
 * 时间占位符 {@code ${DEMO_DATE}} 由 DemoDataSeeder 在解析前整体替换为演示日期 D。
 */
public record DemoData(
        int schemaVersion,
        List<Organization> organizations,
        List<User> users,
        List<Resource> resources,
        List<Activity> activities,
        List<Registration> registrations) {

    public record Organization(UUID id, String name) {}

    public record ApprovalScope(UUID organizationId, List<String> resourceCategories) {}

    public record User(
            UUID id,
            String username,
            String displayName,
            UUID organizationId,
            String identity,
            List<String> roles,
            List<String> permissions,
            List<ApprovalScope> approvalScopes,
            List<UUID> managedResourceIds) {}

    public record Resource(
            UUID id,
            String kind,
            String name,
            String category,
            Integer capacity,
            Integer quantity,
            String openFrom,
            String openTo,
            boolean allowTeacher,
            boolean allowStudent,
            OffsetDateTime createdAt) {}

    public record EquipmentNeed(UUID resourceId, int quantity) {}

    public record Plan(
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            int setupMinutes,
            int teardownMinutes,
            UUID venueId,
            List<EquipmentNeed> equipment) {}

    public record ActivityInput(
            String title,
            String description,
            int expectedAttendees,
            int registrationCapacity,
            List<UUID> staffUserIds,
            Plan plan) {}

    public record Activity(
            UUID id,
            UUID ownerId,
            UUID organizationId,
            String status,
            int versionNo,
            ActivityInput input) {}

    public record Registration(UUID activityId, UUID userId, String status, int versionNo) {}
}
