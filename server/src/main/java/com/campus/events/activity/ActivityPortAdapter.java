package com.campus.events.activity;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.registration.RegistrationRepository;
import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** 只依赖本模块持久层和只读快照，不依赖上层Service，避免改期模块循环。 */
@Component
public class ActivityPortAdapter implements ActivityPort {
    private final ActivityRepository activities;
    private final RegistrationRepository registrations;
    private final ActivityViews views;
    private final Validator validator;
    public ActivityPortAdapter(ActivityRepository a, RegistrationRepository r, ActivityViews v, Validator validator) {
        activities=a; registrations=r; views=v; this.validator=validator;
    }
    @Override @Transactional(readOnly=true) public ActivitySnapshot get(UUID id) { return views.snapshot(activities.get(id)); }
    @Override @Transactional(propagation=Propagation.MANDATORY) public ActivitySnapshot lock(UUID id) { return views.snapshot(activities.lock(id)); }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public int activatePlan(UUID id,int base,Plan plan,UUID actor) {
        activities.requireLock(id); var row=activities.get(id);
        if(row.versionNo()!=base) throw new ApiException(409,"STALE_VERSION","活动版本已变化");
        if(row.status()!=ActivityStatus.SCHEDULED && row.status()!=ActivityStatus.OPEN)
            throw new ApiException(409,"INVALID_STATE","当前活动不可切换安排");
        if(plan==null || !validator.validate(plan).isEmpty()) throw ApiException.invalid("目标安排不合法");
        var old=row.input(); var input=new ActivityInput(old.title(),old.description(),old.expectedAttendees(),old.registrationCapacity(),old.staffUserIds(),plan);
        int next=row.versionNo()+1;
        activities.version(id,next,input,actor); activities.update(row,row.status(),next,next); registrations.activate(id,next);
        return next;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public void bumpLockVersion(UUID id) {
        activities.requireLock(id); var row=activities.get(id); activities.update(row,row.status(),row.versionNo(),row.effectiveVersionNo());
    }
}
