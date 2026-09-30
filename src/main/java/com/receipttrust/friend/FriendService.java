package com.receipttrust.friend;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.notification.NotificationService;
import com.receipttrust.notification.NotificationType;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FriendService(FriendshipRepository friendshipRepository,
                         UserRepository userRepository,
                         NotificationService notificationService) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<User> search(User requester, String query) {
        return userRepository
                .findTop20ByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query)
                .stream()
                .filter(u -> !u.getId().equals(requester.getId()))
                .toList();
    }

    @Transactional
    public FriendDtos.FriendRequestResponse sendRequestDto(User requester, String addresseeUsername) {
        return FriendDtos.FriendRequestResponse.from(sendRequest(requester, addresseeUsername));
    }

    @Transactional(readOnly = true)
    public List<FriendDtos.FriendRequestResponse> incomingRequestDtos(User user) {
        return incomingRequests(user).stream()
                .map(FriendDtos.FriendRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendDtos.UserSummary> listFriendDtos(User user) {
        return listFriends(user).stream()
                .map(FriendDtos.UserSummary::from)
                .toList();
    }

    @Transactional
    public Friendship sendRequest(User requester, String addresseeUsername) {
        User addressee = userRepository.findByUsername(addresseeUsername)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User not found"));
        if (addressee.getId().equals(requester.getId())) {
            throw new ApiExceptions.ValidationException("Cannot friend yourself");
        }
        Optional<Friendship> existing = friendshipRepository.findBetween(requester, addressee);
        if (existing.isPresent()) {
            FriendshipStatus status = existing.get().getStatus();
            if (status == FriendshipStatus.PENDING || status == FriendshipStatus.ACCEPTED) {
                throw new ApiExceptions.ConflictException("Friend request already exists");
            }
            // A prior REJECTED relationship can be renewed.
            existing.get().setStatus(FriendshipStatus.PENDING);
            Friendship renewed = friendshipRepository.save(existing.get());
            notificationService.notify(addressee, NotificationType.FRIEND_REQUEST_RECEIVED,
                    requester.getUsername() + " sent you a friend request");
            return renewed;
        }
        Friendship friendship = friendshipRepository.save(new Friendship(requester, addressee));
        notificationService.notify(addressee, NotificationType.FRIEND_REQUEST_RECEIVED,
                requester.getUsername() + " sent you a friend request");
        return friendship;
    }

    @Transactional(readOnly = true)
    public List<Friendship> incomingRequests(User user) {
        return friendshipRepository.findByAddresseeAndStatus(user, FriendshipStatus.PENDING);
    }

    @Transactional
    public void accept(User user, Long requestId) {
        Friendship friendship = requirePendingForAddressee(user, requestId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
        notificationService.notify(friendship.getRequester(), NotificationType.FRIEND_REQUEST_ACCEPTED,
                user.getUsername() + " accepted your friend request");
    }

    @Transactional
    public void reject(User user, Long requestId) {
        Friendship friendship = requirePendingForAddressee(user, requestId);
        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }

    @Transactional(readOnly = true)
    public List<User> listFriends(User user) {
        return friendshipRepository.findAcceptedForUser(user).stream()
                .map(f -> f.getRequester().getId().equals(user.getId())
                        ? f.getAddressee() : f.getRequester())
                .toList();
    }

    @Transactional
    public void removeFriend(User user, Long otherUserId) {
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User not found"));
        Friendship friendship = friendshipRepository.findBetween(user, other)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Friendship not found"));
        friendshipRepository.delete(friendship);
    }

    /** True when the two users have an ACCEPTED friendship. Used by assignment checks. */
    @Transactional(readOnly = true)
    public boolean areFriends(User a, User b) {
        return friendshipRepository.findBetween(a, b)
                .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElse(false);
    }

    private Friendship requirePendingForAddressee(User user, Long requestId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Request not found"));
        if (!friendship.getAddressee().getId().equals(user.getId())) {
            throw new ApiExceptions.ForbiddenException("Not the recipient of this request");
        }
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ApiExceptions.ConflictException("Request is not pending");
        }
        return friendship;
    }
}
