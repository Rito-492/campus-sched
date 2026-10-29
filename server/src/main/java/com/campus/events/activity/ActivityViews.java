package com.campus.events.activity;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.registration.RegistrationRepository;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 完整内部快照与HTTP裁剪分离；不把缺失端口当作查询成功。 */
@Component
public class ActivityViews {
    private final RegistrationRepository registrations;
    private final ObjectProvider<ChangePort> changes;
    private final SchedulingPort scheduling;
    public ActivityViews(RegistrationRepository registrations, ObjectProvider<ChangePort> changes, SchedulingPort scheduling) {
        this.registrations=registrations; this.changes=changes; this.scheduling=scheduling;
    }
    public ChangePort changes() {
        var port=changes.getIfAvailable();
        if(port==null) throw new ApiException(503,"INTERNAL_ERROR","改期端口尚未就绪");
        return port;
    }
    public ActivitySnapshot snapshot(ActivityRepository.Row row) {
        var ids=registrations.activeIds(row.id());
        return new ActivitySnapshot(view(row,ids.size(),null),ids);
    }
    private ActivityView view(ActivityRepository.Row row,int count,RegistrationStatus mine) {
        boolean reserved=row.status()==ActivityStatus.SCHEDULED || row.status()==ActivityStatus.OPEN;
        // 未预约阶段不允许创建改期，因此无待处理改期是生命周期保证。
        UUID pending=reserved ? changes().pendingId(row.id()) : null;
        return new ActivityView(row.id(),row.organizationId(),row.ownerId(),row.status(),row.versionNo(),
                row.effectiveVersionNo(),row.lockVersion(),row.input(),count,scheduling.risk(row.id()),pending,mine,row.createdAt());
    }
    public ActivityView http(ActivityRepository.Row row, Actor actor, boolean privileged) {
        var mine=registrations.get(row.id(),actor.id());
        // HTTP只需要真实人数；仅内部快照为CM装载完整ACTIVE名单。
        var full=view(row,registrations.count(row.id()),mine==null ? null : mine.status()); var input=full.input();
        if(!privileged) input=new ActivityInput(input.title(),input.description(),input.expectedAttendees(),input.registrationCapacity(),List.of(),input.plan());
        return new ActivityView(full.id(),full.organizationId(),full.ownerId(),full.status(),full.versionNo(),full.effectiveVersionNo(),
                full.lockVersion(),input,full.registrationCount(),full.resourceRisk(),full.activeChangeId(),mine==null ? null : mine.status(),full.createdAt());
    }
}
