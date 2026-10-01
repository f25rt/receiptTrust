package com.receipttrust.notification;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.realtime.SseService;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseService sseService;

    public NotificationService(NotificationRepository notificationRepository, SseService sseService) {
        this.notificationRepository = notificationRepository;
        this.sseService = sseService;
    }

    @Transactional
    public void notify(User user, NotificationType type, String message) {
        notificationRepository.save(new Notification(user, type, message));
        // Push a live SSE event after the row is committed, so the browser's
        // refetch sees it. If there's no active transaction, push immediately.
        Long userId = user.getId();
        String eventName = sseEventName(type);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseService.publish(userId, eventName, type.name());
                }
            });
        } else {
            sseService.publish(userId, eventName, type.name());
        }
    }

    /** Maps a notification type to the SSE event name the frontend listens for. */
    private static String sseEventName(NotificationType type) {
        return switch (type) {
            case DIRECT_MESSAGE -> "message";
            case ASSIGNMENT_CONFIRM_REQUEST, ASSIGNMENT_CONFIRMED, ASSIGNMENT_DECLINED -> "assignment";
            default -> "notification";
        };
    }

    @Transactional(readOnly = true)
    public List<Notification> list(User user, Boolean unreadOnly) {
        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository.findByUserAndReadOrderByCreatedAtDesc(user, false);
        }
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public void markRead(User user, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Notification not found"));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ApiExceptions.ForbiddenException("Not your notification");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }
}
