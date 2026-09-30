package com.receipttrust.security;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/**
 * Spring Security principal carrying our application user id.
 */
public class AuthenticatedUser extends User {

    private final Long userId;

    public AuthenticatedUser(Long userId, String username) {
        super(username, "", AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
