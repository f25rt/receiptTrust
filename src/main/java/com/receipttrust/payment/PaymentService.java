package com.receipttrust.payment;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.debt.Debt;
import com.receipttrust.debt.DebtRepository;
import com.receipttrust.debt.DebtService;
import com.receipttrust.debt.DebtStatus;
import com.receipttrust.notification.NotificationService;
import com.receipttrust.notification.NotificationType;
import com.receipttrust.trust.TrustScoreService;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final DebtRepository debtRepository;
    private final DebtService debtService;
    private final TrustScoreService trustScoreService;
    private final NotificationService notificationService;

    public PaymentService(PaymentRepository paymentRepository,
                          DebtRepository debtRepository,
                          DebtService debtService,
                          TrustScoreService trustScoreService,
                          NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.debtRepository = debtRepository;
        this.debtService = debtService;
        this.trustScoreService = trustScoreService;
        this.notificationService = notificationService;
    }

    @Transactional
    public PaymentDtos.PaymentResponse submit(User submitter, Long debtId, BigDecimal amount,
                                              PaymentMethod method, String notes) {
        Debt debt = debtService.require(debtId);
        if (!debt.getDebtor().getId().equals(submitter.getId())) {
            throw new ApiExceptions.ForbiddenException("Only the debtor can submit a payment");
        }
        if (debt.getStatus() == DebtStatus.SETTLED) {
            throw new ApiExceptions.ConflictException("Debt is already settled");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ApiExceptions.ValidationException("Amount must be positive");
        }
        if (amount.compareTo(debt.getOutstandingAmount()) > 0) {
            throw new ApiExceptions.ValidationException(
                    "Amount exceeds the outstanding balance");
        }
        Payment payment = paymentRepository.save(new Payment(debt, submitter, amount, method, notes));
        notificationService.notify(debt.getCreditor(), NotificationType.PAYMENT_SUBMITTED,
                submitter.getUsername() + " submitted a payment of " + amount);
        return PaymentDtos.PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentDtos.PaymentResponse approve(User creditor, Long paymentId) {
        Payment payment = requirePending(paymentId);
        Debt debt = payment.getDebt();
        if (!debt.getCreditor().getId().equals(creditor.getId())) {
            throw new ApiExceptions.ForbiddenException("Only the creditor can approve");
        }

        BigDecimal newOutstanding = debt.getOutstandingAmount().subtract(payment.getAmount());
        if (newOutstanding.signum() < 0) {
            newOutstanding = BigDecimal.ZERO;
        }
        debt.setOutstandingAmount(newOutstanding);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setBalanceAfter(newOutstanding);
        payment.setDecidedAt(Instant.now());

        boolean settled = newOutstanding.signum() == 0;
        if (settled) {
            debt.setStatus(DebtStatus.SETTLED);
            debt.setSettledAt(Instant.now());
        }
        debtRepository.save(debt);
        paymentRepository.save(payment);

        notificationService.notify(debt.getDebtor(), NotificationType.PAYMENT_APPROVED,
                creditor.getUsername() + " approved your payment of " + payment.getAmount());

        if (settled) {
            trustScoreService.onDebtSettled(debt);
            notificationService.notify(debt.getDebtor(), NotificationType.DEBT_SETTLED,
                    "Your debt to " + creditor.getUsername() + " is fully settled");
        }
        return PaymentDtos.PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentDtos.PaymentResponse reject(User creditor, Long paymentId) {
        Payment payment = requirePending(paymentId);
        Debt debt = payment.getDebt();
        if (!debt.getCreditor().getId().equals(creditor.getId())) {
            throw new ApiExceptions.ForbiddenException("Only the creditor can reject");
        }
        payment.setStatus(PaymentStatus.REJECTED);
        payment.setDecidedAt(Instant.now());
        paymentRepository.save(payment);
        notificationService.notify(debt.getDebtor(), NotificationType.PAYMENT_REJECTED,
                creditor.getUsername() + " rejected your payment of " + payment.getAmount());
        return PaymentDtos.PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentResponse> list(User user, Long debtId) {
        Debt debt = debtService.requireParticipant(user, debtId);
        return paymentRepository.findByDebtOrderByCreatedAtAsc(debt).stream()
                .map(PaymentDtos.PaymentResponse::from)
                .toList();
    }

    private Payment requirePending(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Payment not found"));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiExceptions.ConflictException("Payment is not pending");
        }
        return payment;
    }
}
