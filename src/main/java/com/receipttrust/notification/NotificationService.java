package com.receipttrust.notification;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(User user, NotificationType type, String message) {
        notificationRepository.save(new Notification(user, type, message));
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
