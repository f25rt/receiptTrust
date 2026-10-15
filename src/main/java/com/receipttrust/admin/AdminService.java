package com.receipttrust.admin;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.config.OcrProperties;
import com.receipttrust.debt.DebtRepository;
import com.receipttrust.notification.NotificationService;
import com.receipttrust.notification.NotificationType;
import com.receipttrust.ocr.OcrUsage;
import com.receipttrust.ocr.OcrUsageService;
import com.receipttrust.payment.PaymentRepository;
import com.receipttrust.receipt.ReceiptRepository;
import com.receipttrust.user.Role;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    /** OCR free-tier monthly cap used for the quota gauge (configurable). */
    private static final int DEFAULT_OCR_LIMIT = 1000;

    private final UserRepository userRepository;
    private final ReceiptRepository receiptRepository;
    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final OcrUsageService ocrUsageService;
    private final OcrProperties ocrProperties;
    private final NotificationService notificationService;

    public AdminService(UserRepository userRepository,
                        ReceiptRepository receiptRepository,
                        DebtRepository debtRepository,
                        PaymentRepository paymentRepository,
                        OcrUsageService ocrUsageService,
                        OcrProperties ocrProperties,
                        NotificationService notificationService) {
        this.userRepository = userRepository;
        this.receiptRepository = receiptRepository;
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.ocrUsageService = ocrUsageService;
        this.ocrProperties = ocrProperties;
        this.notificationService = notificationService;
    }

    /** Ensures the caller is an admin; throws 403 otherwise. */
    public void requireAdmin(User user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiExceptions.ForbiddenException("Admin access required");
        }
    }

    @Transactional(readOnly = true)
    public AdminDtos.Metrics metrics(User admin) {
        requireAdmin(admin);

        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant onlineCutoff = now.minus(5, ChronoUnit.MINUTES);

        long totalUsers = userRepository.count();
        long admins = userRepository.countByRole(Role.ADMIN);
        long locked = userRepository.countByLocked(true);
        long flagged = userRepository.countByFlagged(true);
        long activeToday = userRepository.countByLastActiveAtAfter(startOfToday);
        long activeThisMonth = userRepository.countByLastActiveAtAfter(startOfMonth);
        long onlineNow = userRepository.countByLastActiveAtAfter(onlineCutoff);

        // Registrations per day for the last 14 days (bucketed in-app for DB portability).
        List<AdminDtos.Point> registrations = registrationsByDay(14);

        // OCR usage: current month + up to 12 recent months.
        int ocrThisMonth = ocrUsageService.currentMonthCount();
        List<AdminDtos.Point> ocrByMonth = new ArrayList<>();
        List<OcrUsage> recent = ocrUsageService.recentMonths();
        // recentMonths() is desc; present ascending for a left-to-right chart.
        for (int i = recent.size() - 1; i >= 0; i--) {
            OcrUsage u = recent.get(i);
            ocrByMonth.add(new AdminDtos.Point(u.getYearMonth(), u.getScanCount()));
        }

        List<AdminDtos.AdminUser> topTrust = userRepository.findTop10ByOrderByTrustScoreDesc()
                .stream().map(AdminService::toAdminUser).toList();
        List<AdminDtos.AdminUser> bottomTrust = userRepository.findTop10ByOrderByTrustScoreAsc()
                .stream().map(AdminService::toAdminUser).toList();

        return new AdminDtos.Metrics(
                totalUsers, admins, locked, flagged,
                activeToday, activeThisMonth, onlineNow,
                receiptRepository.count(), debtRepository.count(), paymentRepository.count(),
                ocrThisMonth, DEFAULT_OCR_LIMIT,
                registrations, ocrByMonth, topTrust, bottomTrust);
    }

    private List<AdminDtos.Point> registrationsByDay(int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(days - 1L);
        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);

        // Seed every day with 0 so gaps show as empty bars.
        Map<String, Long> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            byDay.put(from.plusDays(i).toString(), 0L);
        }
        for (User u : userRepository.findAll()) {
            if (u.getCreatedAt() != null && !u.getCreatedAt().isBefore(fromInstant)) {
                String day = LocalDate.ofInstant(u.getCreatedAt(), ZoneOffset.UTC).toString();
                byDay.computeIfPresent(day, (k, v) -> v + 1);
            }
        }
        return byDay.entrySet().stream()
                .map(e -> new AdminDtos.Point(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AdminUser> listUsers(User admin, String query) {
        requireAdmin(admin);
        List<User> users;
        if (query == null || query.isBlank()) {
            users = userRepository.findAll();
        } else {
            users = userRepository.searchByNameOrUsernameOrEmail(
                    query.trim(), org.springframework.data.domain.PageRequest.of(0, 100));
        }
        return users.stream().map(AdminService::toAdminUser).toList();
    }

    /** Updates lock/flag moderation flags on a user. Admins cannot lock themselves. */
    @Transactional
    public AdminDtos.AdminUser moderate(User admin, Long userId, Boolean locked, Boolean flagged) {
        requireAdmin(admin);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User not found"));
        boolean notifyFlagged = false;
        boolean notifyLocked = false;
        if (locked != null) {
            if (locked && target.getId().equals(admin.getId())) {
                throw new ApiExceptions.ValidationException("You cannot lock your own account");
            }
            notifyLocked = locked && !target.isLocked();
            target.setLocked(locked);
        }
        if (flagged != null) {
            notifyFlagged = flagged && !target.isFlagged();
            target.setFlagged(flagged);
        }
        userRepository.save(target);

        // Notify the user when they are newly flagged (so they see it on their dashboard/activity).
        if (notifyFlagged) {
            notificationService.notify(target, NotificationType.ACCOUNT_FLAGGED,
                    "Your account has been flagged by an administrator for review.");
        }
        if (notifyLocked) {
            notificationService.notify(target, NotificationType.ACCOUNT_LOCKED,
                    "Your account has been locked by an administrator.");
        }
        return toAdminUser(target);
    }

    private static AdminDtos.AdminUser toAdminUser(User u) {
        return new AdminDtos.AdminUser(
                u.getId(), u.getFullName(), u.getUsername(), u.getEmail(),
                u.getTrustScore(), u.getRole().name(), u.isLocked(), u.isFlagged(),
                u.getLastActiveAt(), u.getCreatedAt());
    }
}
