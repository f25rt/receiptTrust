package com.receipttrust.debt;

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

@RestController
@RequestMapping("/api/debts")
public class DebtController {

    private final DebtQueryService debtQueryService;
    private final DebtService debtService;
    private final CurrentUserService currentUserService;

    public DebtController(DebtQueryService debtQueryService,
                         DebtService debtService,
                         CurrentUserService currentUserService) {
        this.debtQueryService = debtQueryService;
        this.debtService = debtService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    public DebtDtos.DashboardResponse dashboard() {
        return debtQueryService.dashboard(currentUserService.require());
    }

    @GetMapping("/{id}")
    public DebtDtos.DebtSummary get(@PathVariable Long id) {
        User me = currentUserService.require();
        return debtService.summaryFor(me, id);
    }

    @GetMapping("/{id}/explanation")
    public DebtDtos.ExplanationResponse explanation(@PathVariable Long id) {
        return debtQueryService.explanation(currentUserService.require(), id);
    }

    @GetMapping("/{id}/history")
    public DebtDtos.HistoryResponse history(@PathVariable Long id) {
        return debtQueryService.history(currentUserService.require(), id);
    }

    /** Lender marks the debt as fully paid (settles immediately). */
    @PostMapping("/{id}/mark-paid")
    public DebtDtos.DebtSummary markPaid(@PathVariable Long id) {
        return debtService.markAsPaidByCreditor(currentUserService.require(), id);
    }

    @GetMapping("/{id}/comments")
    public List<DebtDtos.CommentResponse> comments(@PathVariable Long id) {
        return debtService.listComments(currentUserService.require(), id);
    }

    @PostMapping("/{id}/comments")
    public DebtDtos.CommentResponse addComment(@PathVariable Long id,
                                               @Valid @RequestBody DebtDtos.CommentRequest request) {
        return debtService.addComment(currentUserService.require(), id, request.body());
    }
}
