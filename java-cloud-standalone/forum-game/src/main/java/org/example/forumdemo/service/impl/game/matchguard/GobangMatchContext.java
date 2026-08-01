package org.example.forumdemo.service.impl.game.matchguard;

import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;

public class GobangMatchContext {

    private final Long userId;

    private final User user;

    private final GameUserProfile profile;

    private final int points;

    private final boolean alreadyQueued;

    public GobangMatchContext(Long userId, User user, GameUserProfile profile, int points, boolean alreadyQueued) {
        this.userId = userId;
        this.user = user;
        this.profile = profile;
        this.points = points;
        this.alreadyQueued = alreadyQueued;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
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
