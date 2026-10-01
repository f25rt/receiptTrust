package com.receipttrust.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 2000) String body
    ) {
    }

    public record MessageResponse(
            Long id,
            String senderUsername,
            boolean mine,
            String body,
            boolean read,
            Instant createdAt
    ) {
    }
}
