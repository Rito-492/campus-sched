package com.campus.events.auth;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcTemplate jdbc;
    public AccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Actor get(UUID id, boolean lock) {
        // 用户命令不修改id/username等唯一键；NO KEY UPDATE仍串行化用户变更，且兼容回执外键的KEY SHARE。
        var rows = jdbc.query("SELECT id,organization_id,display_name,identity,enabled FROM app_user WHERE id=?" + (lock ? " FOR NO KEY UPDATE" : ""),
                (rs, i) -> new Actor(Identity.valueOf(rs.getString("identity")), List.of(), List.of(), List.of(), List.of(),
                        rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getObject("organization_id", UUID.class), rs.getBoolean("enabled")), id);
        if (rows.isEmpty()) throw ApiException.notFound();
        Actor a = rows.getFirst();
        var roles = jdbc.query("SELECT role FROM user_role WHERE user_id=? ORDER BY role", (rs, i) -> Role.valueOf(rs.getString(1)), id);
        var permissions = roles.contains(Role.ADMIN) ? jdbc.query("SELECT permission FROM user_permission WHERE user_id=? ORDER BY permission",
                (rs, i) -> Permission.valueOf(rs.getString(1)), id) : List.<Permission>of();
        var scopes = new LinkedHashMap<UUID, List<String>>();
        if (roles.contains(Role.APPROVER)) jdbc.query("SELECT organization_id,resource_category FROM user_approval_scope WHERE user_id=? ORDER BY organization_id,resource_category",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> scopes.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2)), id);
        var resources = roles.contains(Role.ADMIN) ? jdbc.query("SELECT resource_id FROM user_resource_scope WHERE user_id=? ORDER BY resource_id",
                (rs, i) -> rs.getObject(1, UUID.class), id) : List.<UUID>of();
        return new Actor(a.identity(), List.copyOf(roles), List.copyOf(permissions), scopes.entrySet().stream()
                .map(e -> new ApprovalScope(e.getKey(), List.copyOf(e.getValue()))).toList(), List.copyOf(resources),
                a.id(), a.displayName(), a.organizationId(), a.enabled());
    }
    public void replace(UUID id, AuthorizationInput input) {
        jdbc.update("UPDATE app_user SET identity=?,updated_at=now() WHERE id=?", input.identity().name(), id);
        for (String table : List.of("user_role", "user_permission", "user_approval_scope", "user_resource_scope")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id=?", id);
        }
        for (Role role : input.roles()) jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)", id, role.name());
        for (Permission permission : input.permissions()) jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,?)", id, permission.name());
        for (ApprovalScope scope : input.approvalScopes()) for (String category : scope.resourceCategories()) {
            jdbc.update("INSERT INTO user_approval_scope(user_id,organization_id,resource_category) VALUES (?,?,?)", id, scope.organizationId(), category);
        }
        for (UUID resource : input.managedResourceIds()) jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?)", id, resource);
    }
}
