package com.receipttrust.message;

import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Direct messaging between accepted friends. A conversation is addressed by the
 * other participant's username.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final CurrentUserService currentUserService;

    public MessageController(MessageService messageService,
                             CurrentUserService currentUserService) {
        this.messageService = messageService;
        this.currentUserService = currentUserService;
    }

    /** The conversation with the given friend (also marks incoming as read). */
    @GetMapping("/{username}")
    public List<MessageDtos.MessageResponse> conversation(@PathVariable String username) {
        return messageService.conversation(currentUserService.require(), username);
    }

    /** Send a message to the given friend. */
    @PostMapping("/{username}")
    public MessageDtos.MessageResponse send(@PathVariable String username,
                                            @Valid @RequestBody MessageDtos.SendMessageRequest request) {
        return messageService.send(currentUserService.require(), username, request.body());
    }

    /** Count of unread messages addressed to the current user. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", messageService.unreadCount(currentUserService.require()));
    }

    /** Unread message counts keyed by sender username (for per-friend badges). */
    @GetMapping("/unread-by-sender")
    public Map<String, Long> unreadBySender() {
        return messageService.unreadBySender(currentUserService.require());
    }
}
