package com.meanderatlas.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfiguredCurrentUserProvider implements CurrentUserProvider {

    private final UserId defaultUserId;

    ConfiguredCurrentUserProvider(@Value("${app.default-user-id}") String defaultUserId) {
        this.defaultUserId = UserId.fromString(defaultUserId);
    }

    @Override
    public UserId currentUserId() {
        return defaultUserId;
    }
}
