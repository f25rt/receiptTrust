package com.receipttrust.debt;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.payment.Payment;
import com.receipttrust.payment.PaymentRepository;
import com.receipttrust.payment.PaymentStatus;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DebtQueryService {

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final DebtService debtService;

    public DebtQueryService(DebtRepository debtRepository,
                            PaymentRepository paymentRepository,
                            DebtService debtService) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.debtService = debtService;
    }

    @Transactional(readOnly = true)
    public DebtDtos.DashboardResponse dashboard(User user) {
        List<Debt> owedToMe = debtRepository.findByCreditor(user);
        List<Debt> iOwe = debtRepository.findByDebtor(user);

        List<DebtDtos.DebtSummary> owedToMeDtos = owedToMe.stream()
                .map(d -> toSummary(d, d.getDebtor().getUsername()))
                .toList();
        List<DebtDtos.DebtSummary> iOweDtos = iOwe.stream()
                .map(d -> toSummary(d, d.getCreditor().getUsername()))
                .toList();

        BigDecimal totalOwedToMe = owedToMe.stream()
                .filter(d -> d.getStatus() == DebtStatus.ACTIVE)
                .map(Debt::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIOwe = iOwe.stream()
                .filter(d -> d.getStatus() == DebtStatus.ACTIVE)
                .map(Debt::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long active = iOwe.stream().filter(d -> d.getStatus() == DebtStatus.ACTIVE).count();
        long settled = iOwe.stream().filter(d -> d.getStatus() == DebtStatus.SETTLED).count();

        return new DebtDtos.DashboardResponse(owedToMeDtos, iOweDtos,
                totalOwedToMe, totalIOwe, active, settled);
    }

    @Transactional(readOnly = true)
    public DebtDtos.ExplanationResponse explanation(User user, Long debtId) {
        Debt debt = debtService.require(debtId);
        boolean participant = debt.getCreditor().getId().equals(user.getId())
                || debt.getDebtor().getId().equals(user.getId());
        if (!participant) {
            throw new ApiExceptions.ForbiddenException("Not a participant in this debt");
        }
        List<DebtDtos.ExplanationItem> items = debt.getItems().stream()
                .map(i -> new DebtDtos.ExplanationItem(i.getItemName(), i.getAmount()))
                .toList();
        return new DebtDtos.ExplanationResponse(
                debt.getId(),
                debt.getCreditor().getUsername(),
                debt.getReceipt().getStoreName(),
                debt.getPurchaseDate(),
                items,
                debt.getReceipt().getId(),
                "/api/receipts/" + debt.getReceipt().getId() + "/image",
                debt.getOriginalAmount());
    }

    @Transactional(readOnly = true)
    public DebtDtos.HistoryResponse history(User user, Long debtId) {
        Debt debt = debtService.requireParticipant(user, debtId);
        List<DebtDtos.TimelineEvent> events = new ArrayList<>();

        events.add(new DebtDtos.TimelineEvent(debt.getReceipt().getCreatedAt(),
                "RECEIPT_UPLOADED", "Receipt uploaded for " + debt.getReceipt().getStoreName(),
                null, null));
        events.add(new DebtDtos.TimelineEvent(debt.getCreatedAt(),
                "DEBT_CREATED", "Debt created", debt.getOriginalAmount(), debt.getOriginalAmount()));

        List<Payment> payments = paymentRepository.findByDebtOrderByCreatedAtAsc(debt);
        for (Payment p : payments) {
            events.add(new DebtDtos.TimelineEvent(p.getCreatedAt(),
                    "PAYMENT_SUBMITTED", "Payment submitted (" + p.getMethod() + ")",
                    p.getAmount(), null));
            if (p.getStatus() == PaymentStatus.APPROVED && p.getDecidedAt() != null) {
                events.add(new DebtDtos.TimelineEvent(p.getDecidedAt(),
                        "PAYMENT_APPROVED", "Payment approved", p.getAmount(), p.getBalanceAfter()));
            } else if (p.getStatus() == PaymentStatus.REJECTED && p.getDecidedAt() != null) {
                events.add(new DebtDtos.TimelineEvent(p.getDecidedAt(),
                        "PAYMENT_REJECTED", "Payment rejected", p.getAmount(), null));
            }
        }

        if (debt.getStatus() == DebtStatus.SETTLED && debt.getSettledAt() != null) {
            events.add(new DebtDtos.TimelineEvent(debt.getSettledAt(),
                    "DEBT_SETTLED", "Debt fully settled", null, BigDecimal.ZERO));
        }

        events.sort(Comparator.comparing(DebtDtos.TimelineEvent::at));
        return new DebtDtos.HistoryResponse(debt.getId(), events);
    }

    private DebtDtos.DebtSummary toSummary(Debt d, String counterparty) {
        return new DebtDtos.DebtSummary(d.getId(), counterparty, d.getOutstandingAmount(),
                d.getOriginalAmount(), d.getStatus(), d.getDueDate());
    }
}
