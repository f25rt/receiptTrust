package com.receipttrust.notification;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            NotificationType type,
            String message,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getMessage(),
                    n.isRead(), n.getCreatedAt());
        }
    }
}
