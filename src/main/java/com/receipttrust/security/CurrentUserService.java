package com.receipttrust.security;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ApiExceptions.ForbiddenException("Not authenticated");
        }
        return userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ApiExceptions.ForbiddenException("Not authenticated"));
    }
}
