package com.receipttrust.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Spring Security principal carrying our application user id. Authorities are
 * derived from the user's CURRENT database role on every request (never from the
 * token), so promoting or demoting an admin takes effect immediately.
 */
public class AuthenticatedUser extends User {

    private final Long userId;

    public AuthenticatedUser(Long userId, String username, boolean admin) {
        super(username, "", authorities(admin));
        this.userId = userId;
    }

    private static List<GrantedAuthority> authorities(boolean admin) {
        return admin
                ? AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ADMIN")
                : AuthorityUtils.createAuthorityList("ROLE_USER");
    }

    public Long getUserId() {
        return userId;
    }
}
