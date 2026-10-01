package com.receipttrust.message;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.friend.FriendService;
import com.receipttrust.notification.NotificationService;
import com.receipttrust.notification.NotificationType;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    private final DirectMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FriendService friendService;
    private final NotificationService notificationService;

    public MessageService(DirectMessageRepository messageRepository,
                          UserRepository userRepository,
                          FriendService friendService,
                          NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.friendService = friendService;
        this.notificationService = notificationService;
    }

    /** Send a message to a friend. Only accepted friends may message each other. */
    @Transactional
    public MessageDtos.MessageResponse send(User sender, String recipientUsername, String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw new ApiExceptions.ValidationException("Message cannot be empty");
        }
        User recipient = requireFriend(sender, recipientUsername);
        DirectMessage saved = messageRepository.save(new DirectMessage(sender, recipient, text));
        notificationService.notify(recipient, NotificationType.DIRECT_MESSAGE,
                sender.getUsername() + " sent you a message: " + preview(text));
        return toResponse(saved, sender);
    }

    /** The conversation between the current user and a friend (marks it read). */
    @Transactional
    public List<MessageDtos.MessageResponse> conversation(User me, String friendUsername) {
        User other = requireFriend(me, friendUsername);
        messageRepository.markConversationRead(me.getId(), other.getId());
        return messageRepository.findConversation(me.getId(), other.getId()).stream()
                .map(m -> toResponse(m, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User me) {
        return messageRepository.countByRecipientAndReadByRecipientFalse(me);
    }

    /** Unread message counts keyed by sender username, for badges on the friends list. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> unreadBySender(User me) {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        for (var row : messageRepository.countUnreadGroupedBySender(me.getId())) {
            result.put(row.getUsername(), row.getCnt());
        }
        return result;
    }

    private User requireFriend(User me, String otherUsername) {
        User other = userRepository.findByUsername(otherUsername)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User not found"));
        if (other.getId().equals(me.getId())) {
            throw new ApiExceptions.ValidationException("You cannot message yourself");
        }
        if (!friendService.areFriends(me, other)) {
            throw new ApiExceptions.ForbiddenException("You can only message accepted friends");
        }
        return other;
    }

    private MessageDtos.MessageResponse toResponse(DirectMessage m, User viewer) {
        return new MessageDtos.MessageResponse(
                m.getId(),
                m.getSender().getUsername(),
                m.getSender().getId().equals(viewer.getId()),
                m.getBody(),
                m.isReadByRecipient(),
                m.getCreatedAt());
    }

    private static String preview(String text) {
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
    }
}
