package com.campus.events.auth;

import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final AccountRepository accounts;
    private final AccountPort accountPort;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final CanonicalJson json;
    public UserService(AccountRepository accounts, AccountPort accountPort, JdbcTemplate jdbc, PasswordEncoder encoder, CanonicalJson json) {
        this.accounts = accounts; this.accountPort = accountPort; this.jdbc = jdbc; this.encoder = encoder; this.json = json;
    }
    public CommandResult list(Actor caller, String purpose) {
        Actor actor = accounts.get(caller.id(), false);
        if (purpose != null && !purpose.equals("staff")) throw ApiException.invalid("purpose仅支持staff");
        if ("staff".equals(purpose)) {
            AuthorizationPolicy.role(actor, Role.APPLICANT);
            var rows = jdbc.query("""
                    SELECT id,display_name,organization_id,enabled FROM app_user
                    WHERE organization_id=? AND enabled ORDER BY created_at DESC,id ASC LIMIT 201
                    """, (rs, i) -> new StaffView(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getBoolean(4)), actor.organizationId());
            return Responses.list(rows.stream().limit(200).toList(), rows.size() > 200);
        }
        AuthorizationPolicy.permission(actor, Permission.USER_MANAGE);
        var ids = jdbc.query("SELECT id FROM app_user ORDER BY created_at DESC,id ASC LIMIT 201", (rs, i) -> rs.getObject(1, UUID.class));
        return Responses.list(ids.stream().limit(200).map(id -> accounts.get(id, false)).toList(), ids.size() > 200);
    }
    public CommandResult organizations(Actor caller) {
        AuthorizationPolicy.permission(accounts.get(caller.id(), false), Permission.USER_MANAGE);
        var rows = jdbc.query("SELECT id,name FROM organization ORDER BY created_at DESC,id ASC LIMIT 201",
                (rs, i) -> new OrganizationView(rs.getObject(1, UUID.class), rs.getString(2)));
        return Responses.list(rows.stream().limit(200).toList(), rows.size() > 200);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Actor create(Actor caller, UserCreate input) {
        AuthorizationPolicy.permission(accountPort.lock(caller.id()), Permission.USER_MANAGE);
        var authorization = new AuthorizationInput(input.identity(), input.roles(), input.permissions(), input.approvalScopes(), input.managedResourceIds());
        validateAuthorization(authorization);
        requireOrganization(input.organizationId());
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw ApiException.invalid("密码UTF-8长度不能超过72字节");
        UUID id = UUID.randomUUID();
        // ON CONFLICT避免捕获唯一键异常后继续使用已中止的PostgreSQL事务。
        int inserted = jdbc.update("""
                INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity)
                VALUES (?,?,?,?,?,?) ON CONFLICT(username) DO NOTHING
                """, id, input.organizationId(), input.username(), input.displayName(), encoder.encode(input.password()), input.identity().name());
        if (inserted == 0) throw new ApiException(409, "INVALID_STATE", "用户名已存在");
        accounts.replace(id, authorization);
        Actor created = accounts.get(id, false);
        history(id, caller.id(), Map.of(), created);
        return created;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Actor replace(Actor caller, UUID target, AuthorizationInput input) {
        var locked = lockAccounts(caller.id(), target);
        AuthorizationPolicy.permission(locked.get(caller.id()), Permission.USER_MANAGE);
        validateAuthorization(input);
        Actor before = locked.get(target);
        accounts.replace(target, input);
        Actor after = accounts.get(target, false);
        history(target, caller.id(), before, after);
        return after;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void enabled(Actor caller, UUID target, boolean enabled) {
        var locked = lockAccounts(caller.id(), target);
        AuthorizationPolicy.permission(locked.get(caller.id()), Permission.USER_MANAGE);
        Actor before = locked.get(target);
        jdbc.update("UPDATE app_user SET enabled=?,updated_at=now() WHERE id=?", enabled, target);
        if (!enabled) jdbc.update("DELETE FROM auth_session WHERE user_id=?", target);
        history(target, caller.id(), before, accounts.get(target, false));
    }
    private Map<UUID, Actor> lockAccounts(UUID... ids) {
        var result = new HashMap<UUID, Actor>();
        // UUID字符串顺序与PostgreSQL uuid字节顺序一致，避免Java UUID有符号比较差异。
        Arrays.stream(ids).distinct().sorted(Comparator.comparing(UUID::toString)).forEach(id -> result.put(id, accountPort.lock(id)));
        return result;
    }
    private void history(UUID target, UUID actor, Object before, Object after) {
        jdbc.update("INSERT INTO authorization_change(id,user_id,actor_id,before_json,after_json) VALUES (?,?,?,?::jsonb,?::jsonb)",
                UUID.randomUUID(), target, actor, json.write(before), json.write(after));
    }
    private void requireOrganization(UUID id) {
        if (id == null || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM organization WHERE id=?)", Boolean.class, id))) {
            throw ApiException.invalid("组织不存在");
        }
    }
    private void validateAuthorization(AuthorizationInput input) {
        if (input.identity() == null || input.roles() == null || input.permissions() == null || input.approvalScopes() == null || input.managedResourceIds() == null) {
            throw ApiException.invalid("授权字段必须完整提供");
        }
        unique(input.roles()); unique(input.permissions()); unique(input.managedResourceIds());
        if (!input.roles().contains(Role.ADMIN) && (!input.permissions().isEmpty() || !input.managedResourceIds().isEmpty())) {
            throw ApiException.invalid("移除ADMIN时必须清空管理员权限和资源范围");
        }
        if (!input.roles().contains(Role.APPROVER) && !input.approvalScopes().isEmpty()) {
            throw ApiException.invalid("移除APPROVER时必须清空审批范围");
        }
        var organizations = new HashSet<UUID>();
        for (ApprovalScope scope : input.approvalScopes()) {
            if (scope == null || scope.resourceCategories() == null || !organizations.add(scope.organizationId())) throw ApiException.invalid("审批组织范围重复或缺失");
            requireOrganization(scope.organizationId()); unique(scope.resourceCategories());
            for (String category : scope.resourceCategories()) {
                if (category == null || category.isBlank() || category.equals("*") || !Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM resource WHERE category=?)", Boolean.class, category))) throw ApiException.invalid("资源类别不存在或包含通配符");
            }
        }
        for (UUID resource : input.managedResourceIds()) if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM resource WHERE id=?)", Boolean.class, resource))) throw ApiException.invalid("授权资源不存在");
    }
    private static void unique(List<?> values) {
        if (values.stream().anyMatch(Objects::isNull) || values.size() != new HashSet<>(values).size()) throw ApiException.invalid("授权列表不得包含空值或重复值");
    }
}
