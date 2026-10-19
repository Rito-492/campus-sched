package com.campus.events.contract;

import java.util.UUID;

/** 跨模块端口；写入方法必须加入调用者事务，锁序见公共契约。 */
public interface ActivityPort {
    ActivitySnapshot get(UUID activityId);
    ActivitySnapshot lock(UUID activityId);
    int activatePlan(UUID activityId, int expectedBaseVersionNo, Plan targetPlan, UUID actorId);
    void bumpLockVersion(UUID activityId);
}
