package com.receipttrust.admin;

import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin dashboard: metrics, user listing/ranking, and moderation. Every endpoint
 * requires the caller to have the ADMIN role (enforced in AdminService).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final CurrentUserService currentUserService;

    public AdminController(AdminService adminService, CurrentUserService currentUserService) {
        this.adminService = adminService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/metrics")
    public AdminDtos.Metrics metrics() {
        return adminService.metrics(currentUserService.require());
    }

    @GetMapping("/users")
    public List<AdminDtos.AdminUser> users(@RequestParam(value = "query", required = false) String query) {
        return adminService.listUsers(currentUserService.require(), query);
    }

    @PostMapping("/users/{id}/moderate")
    public AdminDtos.AdminUser moderate(@PathVariable Long id,
                                        @RequestBody AdminDtos.ModerationRequest request) {
        return adminService.moderate(currentUserService.require(), id, request.locked(), request.flagged());
    }
}
