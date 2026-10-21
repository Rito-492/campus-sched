package com.campus.events.resource;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 资源模块的持久化入口；只写资源、资格历史、故障及预约表。 */
@Repository
public class ResourceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ResourceRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Occupancy(UUID activityId, int versionNo, UUID resourceId,
                            Instant start, Instant end, int quantity) {}
    public record Fault(UUID id, UUID resourceId, Instant start, Instant end, int quantity, String reason) {}
    public record ActivityData(UUID id, UUID ownerId, UUID organizationId, ActivityStatus status,
                               int versionNo, ActivityInput input) {}
    public record CalendarRow(UUID resourceId, Instant start, Instant end, int quantity,
                              UUID activityId, Integer version, CalendarSegment segment) {}

    private ResourceView resource(ResultSet rs, int row) throws SQLException {
        return new ResourceView(rs.getBoolean("allow_teacher"), rs.getBoolean("allow_student"),
                ResourceKind.valueOf(rs.getString("kind")), rs.getString("name"), rs.getString("category"),
                (Integer) rs.getObject("capacity"), rs.getInt("quantity"),
                rs.getTime("open_from").toLocalTime().toString(), rs.getTime("open_to").toLocalTime().toString(),
                rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toInstant());
    }

    public ResourceView get(UUID id) {
        var rows = jdbc.query("SELECT * FROM resource WHERE id=?", this::resource, id);
        if (rows.isEmpty()) throw ApiException.notFound();
        return rows.getFirst();
    }

    public List<ResourceView> all() {
        return jdbc.query("SELECT * FROM resource ORDER BY id", this::resource);
    }

    public List<ResourceView> list(ResourceKind kind, String category) {
        return jdbc.query("""
                SELECT * FROM resource WHERE (?::text IS NULL OR kind=?)
                AND (?::text IS NULL OR category=?) ORDER BY created_at DESC,id LIMIT 201
                """, this::resource, kind == null ? null : kind.name(), kind == null ? null : kind.name(), category, category);
    }

    public void lock(Collection<UUID> ids) {
        for (UUID id : ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList()) {
            if (jdbc.query("SELECT id FROM resource WHERE id=? FOR UPDATE",
                    (rs, n) -> rs.getObject(1, UUID.class), id).isEmpty()) throw ApiException.notFound();
        }
    }

    public List<Occupancy> reservations(UUID resourceId, Instant start, Instant end, UUID excluded) {
        return jdbc.query("""
                SELECT * FROM reservation WHERE resource_id=? AND status='ACTIVE'
                AND start_at<? AND end_at>? AND (?::uuid IS NULL OR activity_id<>?)
                ORDER BY start_at,id
                """, this::occupancy, resourceId, Timestamp.from(end), Timestamp.from(start), excluded, excluded);
    }

    private Occupancy occupancy(ResultSet rs, int row) throws SQLException {
        return new Occupancy(rs.getObject("activity_id", UUID.class), rs.getInt("version_no"),
                rs.getObject("resource_id", UUID.class), rs.getTimestamp("start_at").toInstant(),
                rs.getTimestamp("end_at").toInstant(), rs.getInt("quantity"));
    }

    public List<Occupancy> active(UUID activityId) {
        return jdbc.query("SELECT * FROM reservation WHERE activity_id=? AND status='ACTIVE' ORDER BY resource_id",
                this::occupancy, activityId);
    }

    public List<Fault> faults(UUID resourceId, Instant start, Instant end) {
        return faults(resourceId, start, end, Integer.MAX_VALUE);
    }

    public List<Fault> listFaults(UUID resourceId, Instant start, Instant end) {
        return faults(resourceId, start, end, 201);
    }

    private List<Fault> faults(UUID resourceId, Instant start, Instant end, int limit) {
        return jdbc.query("""
                SELECT * FROM resource_fault WHERE resource_id=? AND start_at<? AND end_at>?
                ORDER BY created_at DESC,id LIMIT ?
                """, (rs, n) -> new Fault(rs.getObject("id", UUID.class), resourceId,
                rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
                rs.getInt("unavailable_quantity"), rs.getString("reason")),
                resourceId, Timestamp.from(end), Timestamp.from(start), limit);
    }

    public ActivityData activity(UUID id) {
        var rows = jdbc.query("""
                SELECT a.*,v.input_json FROM activity a JOIN activity_version v
                ON v.activity_id=a.id AND v.version_no=a.version_no WHERE a.id=?
                """, (rs, n) -> new ActivityData(id, rs.getObject("owner_id", UUID.class),
                rs.getObject("organization_id", UUID.class), ActivityStatus.valueOf(rs.getString("status")),
                rs.getInt("version_no"), input(rs.getString("input_json"))), id);
        if (rows.isEmpty()) throw ApiException.notFound();
        return rows.getFirst();
    }

    public ActivityInput version(UUID id, int version) {
        var rows = jdbc.query("SELECT input_json FROM activity_version WHERE activity_id=? AND version_no=?",
                (rs, n) -> input(rs.getString(1)), id, version);
        if (rows.isEmpty()) throw ApiException.notFound();
        return rows.getFirst();
    }

    private ActivityInput input(String json) {
        try { return mapper.readValue(json, ActivityInput.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("活动版本数据不符合统一契约", e);
        }
    }

    public Identity ownerIdentity(UUID activityId) {
        var rows = jdbc.query("""
                SELECT u.identity FROM app_user u JOIN activity a ON a.owner_id=u.id
                WHERE a.id=? AND u.enabled
                """, (rs, n) -> Identity.valueOf(rs.getString(1)), activityId);
        if (rows.isEmpty()) throw ApiException.forbidden();
        return rows.getFirst();
    }

    public void release(UUID activityId) {
        jdbc.update("UPDATE reservation SET status='RELEASED',updated_at=now() WHERE activity_id=? AND status='ACTIVE'", activityId);
    }

    public void reserve(UUID activityId, int version, UUID resourceId, Instant start, Instant end, int quantity) {
        jdbc.update("""
                INSERT INTO reservation(id,activity_id,version_no,resource_id,start_at,end_at,quantity,status)
                VALUES (?,?,?,?,?,?,?,'ACTIVE')
                """, UUID.randomUUID(), activityId, version, resourceId, Timestamp.from(start), Timestamp.from(end), quantity);
    }

    public ResourceView create(ResourceCreate input) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO resource(id,kind,name,category,capacity,quantity,open_from,open_to,allow_teacher,allow_student)
                VALUES (?,?,?,?,?,?,?::time,?::time,?,?)
                """, id, input.kind().name(), input.name(), input.category(), input.capacity(), input.quantity(),
                input.openFrom(), input.openTo(), input.allowTeacher(), input.allowStudent());
        return get(id);
    }

    public void eligibility(UUID id, UUID actorId, ResourceEligibility input, String before, String after) {
        jdbc.update("UPDATE resource SET allow_teacher=?,allow_student=?,updated_at=now() WHERE id=?",
                input.allowTeacher(), input.allowStudent(), id);
        jdbc.update("""
                INSERT INTO resource_eligibility_change(id,resource_id,actor_id,before_json,after_json)
                VALUES (?,?,?,?::jsonb,?::jsonb)
                """, UUID.randomUUID(), id, actorId, before, after);
    }

    public Fault addFault(UUID id, UUID actorId, FaultInput input) {
        UUID faultId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO resource_fault(id,resource_id,start_at,end_at,unavailable_quantity,reason,created_by)
                VALUES (?,?,?,?,?,?,?)
                """, faultId, id, Timestamp.from(input.startAt()), Timestamp.from(input.endAt()),
                input.unavailableQuantity(), input.reason(), actorId);
        return new Fault(faultId, id, input.startAt(), input.endAt(), input.unavailableQuantity(), input.reason());
    }

    public List<UUID> registered(UUID activityId) {
        return jdbc.query("SELECT user_id FROM activity_registration WHERE activity_id=? AND status='ACTIVE' ORDER BY user_id",
                (rs, n) -> rs.getObject(1, UUID.class), activityId);
    }

    public List<CalendarRow> calendar(Instant from, Instant to, UUID resourceId) {
        return jdbc.query("""
                SELECT * FROM (
                  SELECT r.resource_id,s.starts_at,s.ends_at,r.quantity,r.activity_id,r.version_no,
                         s.segment,r.created_at,r.id
                  FROM reservation r JOIN activity_version v ON v.activity_id=r.activity_id AND v.version_no=r.version_no
                  CROSS JOIN LATERAL (VALUES
                    (r.start_at,(v.input_json->'plan'->>'startAt')::timestamptz,'SETUP'),
                    ((v.input_json->'plan'->>'startAt')::timestamptz,(v.input_json->'plan'->>'endAt')::timestamptz,'EVENT'),
                    ((v.input_json->'plan'->>'endAt')::timestamptz,r.end_at,'TEARDOWN')
                  ) s(starts_at,ends_at,segment)
                  WHERE r.status='ACTIVE'
                  UNION ALL
                  SELECT resource_id,start_at,end_at,unavailable_quantity,NULL::uuid,NULL::integer,
                         'FAULT',created_at,id FROM resource_fault
                ) entries WHERE starts_at<ends_at AND starts_at<? AND ends_at>?
                  AND (?::uuid IS NULL OR resource_id=?)
                ORDER BY created_at DESC,id,starts_at,segment LIMIT 201
                """, (rs, n) -> new CalendarRow(rs.getObject("resource_id", UUID.class),
                rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                rs.getInt("quantity"), rs.getObject("activity_id", UUID.class),
                (Integer) rs.getObject("version_no"), CalendarSegment.valueOf(rs.getString("segment"))),
                Timestamp.from(to), Timestamp.from(from), resourceId, resourceId);
    }
}
