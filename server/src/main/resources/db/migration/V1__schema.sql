-- =====================================================================
-- V1__schema.sql —— 校园活动调度系统 V1 数据库结构（DS-01，黄方勇 55230315）
-- 依据：agents/common/02-数据与接口契约.md §4（唯一权威，字段名不得改名）
--      agents/common/01-开发基线.md §4/§8（数据与时间规则、事务与锁）
-- 约定：
--   * 全部表/索引不写死 schema 名，由 Flyway 迁移目标 schema 决定
--     （迁移测试使用 DbTestSupport 的 t_<32hex> 独立 schema）。
--   * 时间戳一律 timestamptz；数量/版本/次数为 integer；enabled 为 boolean；
--     状态/角色/类别用 text + CHECK；JSON 字段为 jsonb。
--   * 引用 activity_version 的复合外键均为 DEFERRABLE INITIALLY DEFERRED，
--     以保证"同一事务创建活动及其首版本"被允许（契约 §4 外键说明）；
--     提交时仍完整校验，不削弱一致性。
--   * 后续迁移只追加 V2+（新表或约束），不得修改本文件已应用内容。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. organization —— 组织（写入方：配置 seed）
-- ---------------------------------------------------------------------
CREATE TABLE organization (
    id          uuid         NOT NULL,
    name        text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT uq_organization_name UNIQUE (name),
    CONSTRAINT ck_organization_name_not_empty CHECK (length(btrim(name)) > 0)
);

-- ---------------------------------------------------------------------
-- 2. app_user —— 账号（写入方：PM）
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id              uuid         NOT NULL,
    organization_id uuid         NOT NULL,
    username        text         NOT NULL,
    display_name    text         NOT NULL,
    password_hash   text         NOT NULL,
    enabled         boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT fk_app_user_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT ck_app_user_username_not_empty CHECK (length(btrim(username)) > 0)
);

CREATE INDEX idx_app_user_organization ON app_user (organization_id);

-- ---------------------------------------------------------------------
-- 3. user_role —— 角色（纯关系表；写入方：PM）
-- ---------------------------------------------------------------------
CREATE TABLE user_role (
    user_id     uuid         NOT NULL,
    role        text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_role_role CHECK (role IN
        ('STUDENT', 'ORGANIZER', 'TEACHER', 'RESOURCE_ADMIN', 'SYS_ADMIN'))
);

-- ---------------------------------------------------------------------
-- 4. auth_session —— 登录会话（只存令牌哈希；写入方：PM）
-- ---------------------------------------------------------------------
CREATE TABLE auth_session (
    id          uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    token_hash  text         NOT NULL,
    expires_at  timestamptz  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT uq_auth_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_session_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_auth_session_user ON auth_session (user_id);

-- ---------------------------------------------------------------------
-- 5. command_receipt —— 幂等回执（写入方：PM；契约 §5 CommandExecutor）
--    http_status/response_json 在事务内占位时可空，成功提交前必须填好。
-- ---------------------------------------------------------------------
CREATE TABLE command_receipt (
    actor_id        uuid         NOT NULL,
    idempotency_key text         NOT NULL,
    route           text         NOT NULL,
    request_hash    text         NOT NULL,
    http_status     integer      NULL,
    response_json   jsonb        NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_command_receipt PRIMARY KEY (actor_id, idempotency_key),
    CONSTRAINT fk_command_receipt_actor
        FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT ck_command_receipt_http_status CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599)
);

-- ---------------------------------------------------------------------
-- 6. resource —— 场地与器材（写入方：DS）
--    契约 §2 附加校验：venue capacity 正整数、quantity 必须 1、category 固定 VENUE；
--    equipment capacity 为 NULL、quantity 正整数、category 非空。
-- ---------------------------------------------------------------------
CREATE TABLE resource (
    id          uuid         NOT NULL,
    kind        text         NOT NULL,
    name        text         NOT NULL,
    category    text         NOT NULL,
    capacity    integer      NULL,
    quantity    integer      NOT NULL,
    open_from   time         NOT NULL,
    open_to     time         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_resource PRIMARY KEY (id),
    CONSTRAINT ck_resource_kind CHECK (kind IN ('VENUE', 'EQUIPMENT')),
    CONSTRAINT ck_resource_name_not_empty CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_resource_category_not_empty CHECK (length(btrim(category)) > 0),
    CONSTRAINT ck_resource_open_window CHECK (open_from < open_to),
    CONSTRAINT ck_resource_shape CHECK (
        (kind = 'VENUE'     AND capacity IS NOT NULL AND capacity > 0 AND quantity = 1 AND category = 'VENUE')
        OR
        (kind = 'EQUIPMENT' AND capacity IS NULL AND quantity > 0)
    )
);

CREATE INDEX idx_resource_kind ON resource (kind);
CREATE INDEX idx_resource_category ON resource (category);

-- ---------------------------------------------------------------------
-- 7. resource_fault —— 资源故障（写入方：DS）
--    start<end、数量为正；venue 数量仅 1 由 DS-02 服务层校验（跨表规则无法用
--    单表 CHECK 表达，见 docs/design/migration-register.md）。
-- ---------------------------------------------------------------------
CREATE TABLE resource_fault (
    id                   uuid         NOT NULL,
    resource_id          uuid         NOT NULL,
    start_at             timestamptz  NOT NULL,
    end_at               timestamptz  NOT NULL,
    unavailable_quantity integer      NOT NULL,
    reason               text         NOT NULL,
    created_by           uuid         NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_resource_fault PRIMARY KEY (id),
    CONSTRAINT fk_resource_fault_resource
        FOREIGN KEY (resource_id) REFERENCES resource (id),
    CONSTRAINT fk_resource_fault_creator
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_resource_fault_interval CHECK (start_at < end_at),
    CONSTRAINT ck_resource_fault_quantity_positive CHECK (unavailable_quantity > 0)
);

CREATE INDEX idx_resource_fault_resource_window
    ON resource_fault (resource_id, start_at, end_at);

-- ---------------------------------------------------------------------
-- 8. activity —— 活动（写入方：AN）
--    version_no 从 1 起、lock_version 从 0 起；当前/有效版本指针经延迟复合
--    外键指向 activity_version（见文件头部约定）。
-- ---------------------------------------------------------------------
CREATE TABLE activity (
    id                   uuid         NOT NULL,
    organization_id      uuid         NOT NULL,
    owner_id             uuid         NOT NULL,
    status               text         NOT NULL,
    version_no           integer      NOT NULL,
    effective_version_no integer      NULL,
    lock_version         integer      NOT NULL DEFAULT 0,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_activity PRIMARY KEY (id),
    CONSTRAINT fk_activity_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_activity_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT ck_activity_status CHECK (status IN
        ('DRAFT', 'PENDING_TEACHER', 'APPROVED_UNRESERVED', 'SCHEDULED', 'OPEN', 'CANCELLED')),
    CONSTRAINT ck_activity_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_activity_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_activity_effective_version CHECK (effective_version_no IS NULL OR effective_version_no >= 1)
);

CREATE INDEX idx_activity_owner_status ON activity (owner_id, status);
CREATE INDEX idx_activity_organization_status ON activity (organization_id, status);

-- ---------------------------------------------------------------------
-- 9. activity_version —— 活动版本（写入方：AN；input_json 写入后不修改）
-- ---------------------------------------------------------------------
CREATE TABLE activity_version (
    activity_id  uuid         NOT NULL,
    version_no   integer      NOT NULL,
    input_json   jsonb        NOT NULL,
    created_by   uuid         NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_activity_version PRIMARY KEY (activity_id, version_no),
    CONSTRAINT fk_activity_version_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_activity_version_creator
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_activity_version_no_positive CHECK (version_no >= 1)
);

-- 活动 → 版本 的当前/有效版本指针（延迟检查，允许活动与首版本同事务写入）
ALTER TABLE activity
    ADD CONSTRAINT fk_activity_current_version
    FOREIGN KEY (id, version_no) REFERENCES activity_version (activity_id, version_no)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE activity
    ADD CONSTRAINT fk_activity_effective_version
    FOREIGN KEY (id, effective_version_no) REFERENCES activity_version (activity_id, version_no)
    DEFERRABLE INITIALLY DEFERRED;

-- activity_version 不可变保护：input_json 写入后不允许再改/删（契约 §4）
CREATE FUNCTION trg_activity_version_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'activity_version 不可修改或删除（append-only）';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER activity_version_immutable
    BEFORE UPDATE OR DELETE ON activity_version
    FOR EACH ROW EXECUTE FUNCTION trg_activity_version_immutable();

-- ---------------------------------------------------------------------
-- 10. activity_approval —— 审批记录（绑定具体版本，保留历次；写入方：AN）
-- ---------------------------------------------------------------------
CREATE TABLE activity_approval (
    id          uuid         NOT NULL,
    activity_id uuid         NOT NULL,
    version_no  integer      NOT NULL,
    actor_id    uuid         NOT NULL,
    decision    text         NOT NULL,
    comment     text         NOT NULL DEFAULT '',
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_activity_approval PRIMARY KEY (id),
    CONSTRAINT fk_activity_approval_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_activity_approval_actor
        FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT fk_activity_approval_version
        FOREIGN KEY (activity_id, version_no) REFERENCES activity_version (activity_id, version_no)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_activity_approval_decision CHECK (decision IN ('APPROVE', 'REJECT')),
    CONSTRAINT ck_activity_approval_version_positive CHECK (version_no >= 1)
);

CREATE INDEX idx_activity_approval_activity ON activity_approval (activity_id, version_no);

-- ---------------------------------------------------------------------
-- 11. activity_registration —— 报名（写入方：AN）
--     status：ACTIVE/WITHDRAWN/CANCELLED；version_no 为当前有效安排版本。
-- ---------------------------------------------------------------------
CREATE TABLE activity_registration (
    activity_id  uuid         NOT NULL,
    user_id      uuid         NOT NULL,
    status       text         NOT NULL,
    version_no   integer      NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_activity_registration PRIMARY KEY (activity_id, user_id),
    CONSTRAINT fk_activity_registration_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_activity_registration_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_activity_registration_version
        FOREIGN KEY (activity_id, version_no) REFERENCES activity_version (activity_id, version_no)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_activity_registration_status CHECK (status IN
        ('ACTIVE', 'WITHDRAWN', 'CANCELLED')),
    CONSTRAINT ck_activity_registration_version_positive CHECK (version_no >= 1)
);

CREATE INDEX idx_activity_registration_user ON activity_registration (user_id, status);

-- ---------------------------------------------------------------------
-- 12. reservation —— 资源预约（写入方：DS；仅经 SchedulingPort）
--     status：ACTIVE/RELEASED；每活动/版本/资源唯一。
-- ---------------------------------------------------------------------
CREATE TABLE reservation (
    id          uuid         NOT NULL,
    activity_id uuid         NOT NULL,
    version_no  integer      NOT NULL,
    resource_id uuid         NOT NULL,
    start_at    timestamptz  NOT NULL,
    end_at      timestamptz  NOT NULL,
    quantity    integer      NOT NULL,
    status      text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_reservation PRIMARY KEY (id),
    CONSTRAINT fk_reservation_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_reservation_resource
        FOREIGN KEY (resource_id) REFERENCES resource (id),
    CONSTRAINT fk_reservation_version
        FOREIGN KEY (activity_id, version_no) REFERENCES activity_version (activity_id, version_no)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_reservation_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_reservation_interval CHECK (start_at < end_at),
    CONSTRAINT ck_reservation_version_positive CHECK (version_no >= 1),
    CONSTRAINT uq_reservation_activity_version_resource UNIQUE (activity_id, version_no, resource_id)
);

-- 契约 §4 索引：活跃预约按资源与时间窗口检索（冲突扫描主路径）
CREATE INDEX idx_reservation_active_resource_window
    ON reservation (resource_id, start_at, end_at)
    WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- 13. change_request —— 改期请求（写入方：CM；target_plan_json 不可修改）
-- ---------------------------------------------------------------------
CREATE TABLE change_request (
    id                    uuid         NOT NULL,
    activity_id           uuid         NOT NULL,
    base_version_no       integer      NOT NULL,
    target_plan_json      jsonb        NOT NULL,
    impact_json           jsonb        NOT NULL,
    reason                text         NOT NULL,
    status                text         NOT NULL,
    teacher_decision_json jsonb        NULL,
    resource_decision_json jsonb       NULL,
    last_conflicts_json   jsonb        NULL,
    applied_version_no    integer      NULL,
    created_by            uuid         NOT NULL,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_change_request PRIMARY KEY (id),
    CONSTRAINT fk_change_request_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_change_request_base_version
        FOREIGN KEY (activity_id, base_version_no) REFERENCES activity_version (activity_id, version_no)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_change_request_creator
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_change_request_status CHECK (status IN
        ('AWAITING_CONFIRMATIONS', 'PENDING_TEACHER', 'PENDING_RESOURCE',
         'APPLIED', 'REJECTED', 'CONFLICTED', 'WITHDRAWN', 'CANCELLED_BY_ACTIVITY')),
    CONSTRAINT ck_change_request_base_version_positive CHECK (base_version_no >= 1),
    CONSTRAINT ck_change_request_applied_version CHECK (applied_version_no IS NULL OR applied_version_no >= 1),
    CONSTRAINT ck_change_request_reason_not_empty CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX idx_change_request_activity ON change_request (activity_id, created_at);

-- 契约 §4 索引：每活动最多一个待处理改期（三个待处理状态上建部分唯一索引）
CREATE UNIQUE INDEX uq_change_request_pending_activity
    ON change_request (activity_id)
    WHERE status IN ('AWAITING_CONFIRMATIONS', 'PENDING_TEACHER', 'PENDING_RESOURCE');

-- target_plan_json 不可修改保护（契约 §4：不可变 targetPlan）
CREATE FUNCTION trg_change_request_plan_immutable() RETURNS trigger AS $$
BEGIN
    IF NEW.target_plan_json IS DISTINCT FROM OLD.target_plan_json THEN
        RAISE EXCEPTION 'change_request.target_plan_json 不可修改';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER change_request_plan_immutable
    BEFORE UPDATE ON change_request
    FOR EACH ROW EXECUTE FUNCTION trg_change_request_plan_immutable();

-- ---------------------------------------------------------------------
-- 14. change_confirmation —— 改期工作人员确认（写入方：CM）
--     decision 可空（用户被指派但未决定）；comment 允许空字符串。
-- ---------------------------------------------------------------------
CREATE TABLE change_confirmation (
    change_id   uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    decision    text         NULL,
    comment     text         NOT NULL DEFAULT '',
    decided_at  timestamptz  NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_change_confirmation PRIMARY KEY (change_id, user_id),
    CONSTRAINT fk_change_confirmation_change
        FOREIGN KEY (change_id) REFERENCES change_request (id),
    CONSTRAINT fk_change_confirmation_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_change_confirmation_decision CHECK (decision IN ('APPROVE', 'REJECT'))
);

-- ---------------------------------------------------------------------
-- 15. audit_log —— 审计（只追加；写入方：CM 事件基础设施）
--     activity_id 可空以支持资源故障事件；该情形 version_no 置 0。
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id          uuid         NOT NULL,
    activity_id uuid         NULL,
    actor_id    uuid         NOT NULL,
    event_type  text         NOT NULL,
    version_no  integer      NOT NULL DEFAULT 0,
    summary     text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_audit_log_actor
        FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT ck_audit_log_version_no CHECK (version_no >= 0),
    CONSTRAINT ck_audit_log_event_type_not_empty CHECK (length(btrim(event_type)) > 0)
);

CREATE INDEX idx_audit_log_activity_time ON audit_log (activity_id, created_at);

-- audit_log 只追加保护
CREATE FUNCTION trg_audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log 只追加，不可修改或删除';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION trg_audit_log_append_only();

-- ---------------------------------------------------------------------
-- 16. outbox_event —— 站外/站内事件发件箱（写入方：CM，随业务事务追加）
--     status：PENDING/DONE/FAILED；attempts 初始 0。
-- ---------------------------------------------------------------------
CREATE TABLE outbox_event (
    id               uuid         NOT NULL,
    activity_id      uuid         NULL,
    version_no       integer      NOT NULL DEFAULT 0,
    event_type       text         NOT NULL,
    payload_json     jsonb        NOT NULL,
    status           text         NOT NULL DEFAULT 'PENDING',
    attempts         integer      NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz  NOT NULL DEFAULT now(),
    last_error       text         NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT fk_outbox_event_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    CONSTRAINT ck_outbox_event_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_event_version_no CHECK (version_no >= 0),
    CONSTRAINT ck_outbox_event_event_type_not_empty CHECK (length(btrim(event_type)) > 0)
);

-- 契约 §4 索引：消费者按批取待处理/到期事件
CREATE INDEX idx_outbox_event_status_next_attempt ON outbox_event (status, next_attempt_at);

-- ---------------------------------------------------------------------
-- 17. notification —— 站内通知（写入方：CM；event_id+recipient_id 唯一去重）
-- ---------------------------------------------------------------------
CREATE TABLE notification (
    id           uuid         NOT NULL,
    event_id     uuid         NOT NULL,
    recipient_id uuid         NOT NULL,
    activity_id  uuid         NULL,
    version_no   integer      NULL,
    title        text         NOT NULL,
    body         text         NOT NULL,
    read_at      timestamptz  NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT uq_notification_event_recipient UNIQUE (event_id, recipient_id),
    CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_id) REFERENCES app_user (id),
    CONSTRAINT fk_notification_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT ck_notification_title_not_empty CHECK (length(btrim(title)) > 0)
);

CREATE INDEX idx_notification_recipient_time ON notification (recipient_id, created_at);

-- 说明：notification.event_id 刻意不加指向 outbox_event 的外键——outbox 合约允许
-- 消费完成后归档清理，而通知是长期记录，两者生命周期不同（设计取舍记录在
-- docs/design/migration-register.md）。
