package com.meanderatlas.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    private final UserId defaultUserId;

    public CurrentUserProvider(@Value("${app.default-user-id}") String defaultUserId) {
        this.defaultUserId = UserId.fromString(defaultUserId);
    }

    public UserId currentUserId() {
        return defaultUserId;
    }
}
