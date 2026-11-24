package com.meanderatlas.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfiguredCurrentUserProvider implements CurrentUserProvider {

    private static final String DEFAULT_USER_ID_EXPRESSION = "${app.default-user-id}";

    private final UserId defaultUserId;

    ConfiguredCurrentUserProvider(@Value(DEFAULT_USER_ID_EXPRESSION) String defaultUserId) {
        this.defaultUserId = UserId.fromString(defaultUserId);
    }

    @Override
    public UserId currentUserId() {
        return defaultUserId;
    }
}
