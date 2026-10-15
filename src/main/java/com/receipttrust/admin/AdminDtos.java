package com.receipttrust.admin;

import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** A single labeled count, e.g. a day's registrations or a month's scans. */
    public record Point(String label, long value) {
    }

    /** Compact user row for the admin user table / rankings. */
    public record AdminUser(
            Long id,
            String fullName,
            String username,
            String email,
            int trustScore,
            String role,
            boolean locked,
            boolean flagged,
            Instant lastActiveAt,
            Instant createdAt
    ) {
    }

    /** Everything the admin dashboard needs in one call. */
    public record Metrics(
            long totalUsers,
            long admins,
            long lockedUsers,
            long flaggedUsers,
            long activeToday,
            long activeThisMonth,
            long onlineNow,
            long totalReceipts,
            long totalDebts,
            long totalPayments,
            // OCR quota
            int ocrScansThisMonth,
            int ocrMonthlyLimit,
            List<Point> registrationsByDay,
            List<Point> ocrUsageByMonth,
            List<AdminUser> topTrust,
            List<AdminUser> bottomTrust
    ) {
    }

    public record ModerationRequest(Boolean locked, Boolean flagged) {
    }
}
