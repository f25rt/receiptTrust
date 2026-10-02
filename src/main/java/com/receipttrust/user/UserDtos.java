package com.receipttrust.user;

import com.receipttrust.trust.ReputationLevel;

public final class UserDtos {

    private UserDtos() {
    }

    /** Full private profile for the authenticated owner. */
    public record MyProfileResponse(
            Long id,
            String fullName,
            String username,
            String email,
            String profileImagePath,
            int trustScore,
            ReputationLevel reputationLevel,
            long debtsSettled,
            long currentDebts,
            Double averageRepaymentDays,
            AuthProvider provider,
            boolean hasPassword,
            String currency,
            String mobile,
            String gender,
            String country,
            String city
    ) {
    }

    /** Editable profile fields the owner can update from their profile page. */
    public record UpdateProfileRequest(
            String currency,
            String mobile,
            String gender,
            String country,
            String city
    ) {
    }

    /** Public profile visible to others (no email or private debts). */
    public record PublicProfileResponse(
            String fullName,
            String username,
            int trustScore,
            ReputationLevel reputationLevel,
            long debtsSettled,
            Double averageRepaymentDays
    ) {
    }

    public record SearchResult(
            Long id,
            String fullName,
            String username
    ) {
        public static SearchResult from(User u) {
            return new SearchResult(u.getId(), u.getFullName(), u.getUsername());
        }
    }
}
