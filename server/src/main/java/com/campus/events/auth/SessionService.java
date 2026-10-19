package com.campus.events.auth;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.foundation.CanonicalJson;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SessionService {
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;
    public SessionService(JdbcTemplate jdbc, AccountRepository accounts, PasswordEncoder encoder, Clock clock, TransactionTemplate transactions) {
        this.jdbc = jdbc; this.accounts = accounts; this.encoder = encoder; this.clock = clock; this.transactions = transactions;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }
    public LoginView login(LoginInput input) {
        return transactions.execute(tx -> {
            var users = jdbc.queryForList("SELECT id,password_hash FROM app_user WHERE username=? FOR UPDATE", input.username());
            String hash = users.isEmpty() ? dummyHash : users.getFirst().get("password_hash").toString();
            boolean valid;
            try { valid = encoder.matches(input.password(), hash); } catch (IllegalArgumentException ex) { valid = false; }
            if (users.isEmpty() || !valid) throw ApiException.unauthenticated();
            Actor actor = accounts.get((UUID) users.getFirst().get("id"), false);
            AuthorizationPolicy.enabled(actor);
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            var expires = clock.instant().plus(Duration.ofHours(8));
            jdbc.update("INSERT INTO auth_session(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
                    UUID.randomUUID(), actor.id(), CanonicalJson.sha256(token), Timestamp.from(expires));
            return new LoginView(token, expires, actor);
        });
    }
    public Actor authenticate(String token) {
        var ids = jdbc.query("""
                SELECT s.user_id FROM auth_session s JOIN app_user u ON u.id=s.user_id
                WHERE s.token_hash=? AND s.expires_at>? AND u.enabled
                """, (rs, i) -> rs.getObject(1, UUID.class), CanonicalJson.sha256(token), Timestamp.from(clock.instant()));
        if (ids.isEmpty()) throw ApiException.unauthenticated();
        Actor actor = accounts.get(ids.getFirst(), false);
        AuthorizationPolicy.enabled(actor);
        return actor;
    }
    public void logout(String token) { jdbc.update("DELETE FROM auth_session WHERE token_hash=?", CanonicalJson.sha256(token)); }
}
