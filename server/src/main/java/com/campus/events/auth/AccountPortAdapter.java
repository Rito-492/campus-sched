package com.campus.events.auth;

import com.campus.events.contract.AccountPort;
import com.campus.events.contract.Actor;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountPortAdapter implements AccountPort {
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    public AccountPortAdapter(AccountRepository accounts, JdbcTemplate jdbc) { this.accounts = accounts; this.jdbc = jdbc; }
    @Override public Actor get(UUID userId) { return accounts.get(userId, false); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Actor lock(UUID userId) { return accounts.get(userId, true); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void grantResource(UUID userId, UUID resourceId) {
        // 调用者须已持有创建者用户行锁，再创建资源；此处不反向依赖资源服务。
        jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?) ON CONFLICT DO NOTHING", userId, resourceId);
    }
}
