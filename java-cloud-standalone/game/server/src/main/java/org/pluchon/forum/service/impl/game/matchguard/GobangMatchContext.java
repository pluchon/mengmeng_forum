package org.pluchon.forum.service.impl.game.matchguard;

import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.UserInternalVO;

public class GobangMatchContext {

    private final Long userId;

    private final UserInternalVO user;

    private final GameUserProfile profile;

    private final int points;

    private final boolean alreadyQueued;

    public GobangMatchContext(Long userId, UserInternalVO user, GameUserProfile profile, int points, boolean alreadyQueued) {
        this.userId = userId;
        this.user = user;
        this.profile = profile;
        this.points = points;
        this.alreadyQueued = alreadyQueued;
    }

    public Long getUserId() {
        return userId;
    }

    public UserInternalVO getUser() {
        return user;
    }

    public GameUserProfile getProfile() {
        return profile;
    }

    public int getPoints() {
        return points;
    }

    public boolean isAlreadyQueued() {
        return alreadyQueued;
    }
}
