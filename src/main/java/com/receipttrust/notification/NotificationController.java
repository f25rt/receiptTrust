package com.receipttrust.notification;

import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService,
                                 CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<NotificationDtos.NotificationResponse> list(
            @RequestParam(name = "unread", required = false) Boolean unread) {
        User me = currentUserService.require();
        return notificationService.list(me, unread).stream()
                .map(NotificationDtos.NotificationResponse::from)
                .toList();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        User me = currentUserService.require();
        notificationService.markRead(me, id);
        return ResponseEntity.noContent().build();
    }
}
