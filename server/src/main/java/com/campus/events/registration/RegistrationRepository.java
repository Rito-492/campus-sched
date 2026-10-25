package com.campus.events.registration;

import com.campus.events.contract.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 所有写入由调用者持有活动行锁后执行。 */
@Repository
public class RegistrationRepository {
    private final JdbcTemplate jdbc;
    public RegistrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<UUID> activeIds(UUID activity) {
        return jdbc.query("SELECT user_id FROM activity_registration WHERE activity_id=? AND status='ACTIVE' ORDER BY user_id", (rs,n) -> rs.getObject(1,UUID.class), activity);
    }
    public int count(UUID activity) { return jdbc.queryForObject("SELECT count(*) FROM activity_registration WHERE activity_id=? AND status='ACTIVE'", Integer.class, activity); }
    public RegistrationView get(UUID activity, UUID user) {
        var rows = jdbc.query("SELECT * FROM activity_registration WHERE activity_id=? AND user_id=?", (rs,n) ->
                new RegistrationView(activity,user,RegistrationStatus.valueOf(rs.getString("status")),rs.getInt("version_no")),activity,user);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public List<RegistrationView> list(UUID activity) {
        return jdbc.query("SELECT * FROM activity_registration WHERE activity_id=? ORDER BY created_at DESC,user_id LIMIT 201", (rs,n) ->
                new RegistrationView(activity,rs.getObject("user_id",UUID.class),RegistrationStatus.valueOf(rs.getString("status")),rs.getInt("version_no")),activity);
    }
    public RegistrationView save(UUID activity, UUID user, RegistrationStatus status, int version) {
        jdbc.update("INSERT INTO activity_registration(activity_id,user_id,status,version_no) VALUES (?,?,?,?) ON CONFLICT(activity_id,user_id) DO UPDATE SET status=excluded.status,version_no=excluded.version_no,updated_at=now()",activity,user,status.name(),version);
        return new RegistrationView(activity,user,status,version);
    }
    public void activate(UUID activity, int version) { jdbc.update("UPDATE activity_registration SET version_no=?,updated_at=now() WHERE activity_id=? AND status='ACTIVE'",version,activity); }
    public void cancel(UUID activity) { jdbc.update("UPDATE activity_registration SET status='CANCELLED',updated_at=now() WHERE activity_id=? AND status='ACTIVE'",activity); }
}
