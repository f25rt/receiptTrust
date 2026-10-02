package com.receipttrust.friend;

import com.receipttrust.user.User;
import jakarta.validation.constraints.NotBlank;

public final class FriendDtos {

    private FriendDtos() {
    }

    public record FriendRequestCreate(
            @NotBlank String addresseeUsername
    ) {
    }

    public record UserSummary(
            Long id,
            String fullName,
            String username,
            int trustScore,
            String mobile
    ) {
        public static UserSummary from(User u) {
            return new UserSummary(u.getId(), u.getFullName(), u.getUsername(),
                    u.getTrustScore(), u.getMobile());
        }
    }

    public record FriendRequestResponse(
            Long requestId,
            UserSummary requester,
            FriendshipStatus status
    ) {
        public static FriendRequestResponse from(Friendship f) {
            return new FriendRequestResponse(f.getId(), UserSummary.from(f.getRequester()), f.getStatus());
        }
    }
}
