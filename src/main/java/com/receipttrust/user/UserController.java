package com.receipttrust.user;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.common.storage.FileStorageService;
import com.receipttrust.common.storage.StoredFile;
import com.receipttrust.security.CurrentUserService;
import com.receipttrust.trust.ReputationLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class UserController {

    private final UserRepository userRepository;
    private final ProfileMetricsService metricsService;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final com.receipttrust.security.AuthService authService;

    public UserController(UserRepository userRepository,
                          ProfileMetricsService metricsService,
                          CurrentUserService currentUserService,
                          FileStorageService fileStorageService,
                          com.receipttrust.security.AuthService authService) {
        this.userRepository = userRepository;
        this.metricsService = metricsService;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
        this.authService = authService;
    }

    @GetMapping("/api/me")
    public UserDtos.MyProfileResponse me() {
        User me = currentUserService.require();
        return new UserDtos.MyProfileResponse(
                me.getId(), me.getFullName(), me.getUsername(), me.getEmail(),
                me.getProfileImagePath(), me.getTrustScore(),
                ReputationLevel.fromScore(me.getTrustScore()),
                metricsService.debtsSettled(me),
                metricsService.currentDebts(me),
                metricsService.averageRepaymentDays(me),
                me.getProvider(),
                me.hasPassword());
    }

    @GetMapping("/api/users/{username}")
    public UserDtos.PublicProfileResponse publicProfile(@PathVariable String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User not found"));
        return new UserDtos.PublicProfileResponse(
                user.getFullName(), user.getUsername(), user.getTrustScore(),
                ReputationLevel.fromScore(user.getTrustScore()),
                metricsService.debtsSettled(user),
                metricsService.averageRepaymentDays(user));
    }

    @GetMapping("/api/users/search")
    public List<UserDtos.SearchResult> search(@RequestParam("query") String query) {
        User me = currentUserService.require();
        return userRepository
                .findTop20ByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query)
                .stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .map(UserDtos.SearchResult::from)
                .toList();
    }

    @PostMapping("/api/me/profile-image")
    public UserDtos.MyProfileResponse uploadProfileImage(@RequestParam("file") MultipartFile file) {
        User me = currentUserService.require();
        StoredFile stored = fileStorageService.storeProfileImage(file);
        me.setProfileImagePath(stored.relativePath());
        userRepository.save(me);
        return me();
    }

    /** Set/enable a local password (lets social-login users also use password sign-in). */
    @PostMapping("/api/me/password")
    public UserDtos.MyProfileResponse setPassword(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.receipttrust.security.AuthDtos.SetPasswordRequest request) {
        User me = currentUserService.require();
        authService.setPassword(me, request.password());
        return me();
    }
}
