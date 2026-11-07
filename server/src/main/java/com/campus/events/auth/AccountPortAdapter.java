package com.campus.events.auth;

import com.campus.events.contract.AccountPort;
import com.campus.events.contract.Actor;
import com.campus.events.foundation.CanonicalJson;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountPortAdapter implements AccountPort {
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;
    public AccountPortAdapter(AccountRepository accounts, JdbcTemplate jdbc, CanonicalJson json) {
        this.accounts = accounts; this.jdbc = jdbc; this.json = json;
    }
    @Override public Actor get(UUID userId) { return accounts.get(userId, false); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Actor lock(UUID userId) { return accounts.get(userId, true); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void grantResource(UUID userId, UUID resourceId) {
        // 调用者须已持有创建者用户行锁，再创建资源；此处不反向依赖资源服务。
        Actor before = accounts.get(userId, false);
        int inserted = jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?) ON CONFLICT DO NOTHING", userId, resourceId);
        if (inserted == 0) return;
        // 新资源自动授权也是授权变更；创建者本人发起，历史与资源、范围及命令回执共事务。
        jdbc.update("INSERT INTO authorization_change(id,user_id,actor_id,before_json,after_json) VALUES (?,?,?,?::jsonb,?::jsonb)",
                UUID.randomUUID(), userId, userId, json.write(before), json.write(accounts.get(userId, false)));
    }
}
