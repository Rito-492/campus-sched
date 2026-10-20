package com.campus.events.foundation;

import com.campus.events.contract.Actor;
import com.campus.events.contract.CommandResult;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 回执与业务共事务；业务冲突须返回error结果，异常须抛出并整体回滚。 */
@Component
public class CommandExecutor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CanonicalJson json;
    public CommandExecutor(JdbcTemplate jdbc, TransactionTemplate transactions, CanonicalJson json) {
        this.jdbc = jdbc; this.transactions = transactions; this.json = json;
    }
    public CommandResult execute(Actor actor, UUID key, String route, String canonicalJson,
                                 Supplier<CommandResult> action) {
        if (actor == null || !actor.enabled()) throw ApiException.unauthenticated();
        if (key == null || route == null || route.isBlank()) throw ApiException.invalid("需要有效幂等键和路由");
        String hash = CanonicalJson.sha256(json.normalize(canonicalJson));
        return transactions.execute(tx -> {
            jdbc.update("""
                    INSERT INTO command_receipt(actor_id,idempotency_key,route,request_hash)
                    VALUES (?,?,?,?) ON CONFLICT DO NOTHING
                    """, actor.id(), key.toString(), route, hash);
            var receipt = jdbc.queryForMap("SELECT * FROM command_receipt WHERE actor_id=? AND idempotency_key=? FOR UPDATE",
                    actor.id(), key.toString());
            if (!route.equals(receipt.get("route")) || !hash.equals(receipt.get("request_hash"))) {
                throw new ApiException(409, "IDEMPOTENCY_KEY_REUSED", "幂等键已用于其他请求");
            }
            if (receipt.get("http_status") != null) {
                return new CommandResult(((Number) receipt.get("http_status")).intValue(), json.read(receipt.get("response_json").toString()));
            }
            CommandResult result = Objects.requireNonNull(action.get(), "业务必须返回完整响应");
            var envelope = json.read(json.write(result.body()));
            if (result.httpStatus() < 200 || result.httpStatus() > 599 || !envelope.hasNonNull("requestId")
                    || !(envelope.has("data") ^ envelope.has("error"))) {
                throw new IllegalStateException("命令响应必须是完整且唯一包装的信封");
            }
            jdbc.update("""
                    UPDATE command_receipt SET http_status=?,response_json=?::jsonb,updated_at=now()
                    WHERE actor_id=? AND idempotency_key=?
                    """, result.httpStatus(), envelope.toString(), actor.id(), key.toString());
            return result;
        });
    }
}
