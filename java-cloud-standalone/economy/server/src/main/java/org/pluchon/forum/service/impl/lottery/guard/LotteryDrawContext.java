package org.pluchon.forum.service.impl.lottery.guard;

import org.pluchon.forum.entity.db.LotteryActivity;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.lottery.LotteryDrawDTO;

public class LotteryDrawContext {

    private final Long userId;

    private final LotteryDrawDTO request;

    private final LotteryActivity activity;

    private final User lockedUser;

    private final boolean resourcesResolved;

    private LotteryDrawContext(
            Long userId,
            LotteryDrawDTO request,
            LotteryActivity activity,
            User lockedUser,
            boolean resourcesResolved
    ) {
        this.userId = userId;
        this.request = request;
        this.activity = activity;
        this.lockedUser = lockedUser;
        this.resourcesResolved = resourcesResolved;
    }

    public static LotteryDrawContext requestOnly(Long userId, LotteryDrawDTO request) {
        return new LotteryDrawContext(userId, request, null, null, false);
    }

    public static LotteryDrawContext resolved(
            Long userId,
            LotteryDrawDTO request,
            LotteryActivity activity,
            User lockedUser
    ) {
        return new LotteryDrawContext(userId, request, activity, lockedUser, true);
    }

    public Long getUserId() {
        return userId;
    }

    public LotteryDrawDTO getRequest() {
        return request;
    }

    public LotteryActivity getActivity() {
        return activity;
    }

    public User getLockedUser() {
        return lockedUser;
    }

    public boolean isResourcesResolved() {
        return resourcesResolved;
    }

    public int times() {
        return request == null || request.getTimes() == null ? 0 : request.getTimes();
    }
}
