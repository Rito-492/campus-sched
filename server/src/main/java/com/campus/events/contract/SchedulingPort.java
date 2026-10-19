package com.campus.events.contract;

import java.util.UUID;

/** 跨模块端口；写入方法必须加入调用者事务，锁序见公共契约。 */
public interface SchedulingPort {
    CheckResult check(Plan plan, int expectedAttendees, UUID excludeActivityId, Identity applicantIdentity);
    AlternativesView alternatives(Plan plan, int expectedAttendees, UUID excludeActivityId, Identity applicantIdentity);
    CheckResult lockAndCheck(UUID activityId, Plan targetPlan, int expectedAttendees, Identity applicantIdentity);
    void replaceReservations(UUID activityId, int newVersionNo, Plan targetPlan);
    void releaseReservations(UUID activityId);
    Risk risk(UUID activityId);
}
