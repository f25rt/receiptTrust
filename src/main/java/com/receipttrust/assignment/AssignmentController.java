package com.receipttrust.assignment;

import com.receipttrust.debt.DebtDtos;
import com.receipttrust.debt.DebtService;
import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/receipts/{receiptId}")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final DebtService debtService;
    private final CurrentUserService currentUserService;

    public AssignmentController(AssignmentService assignmentService,
                               DebtService debtService,
                               CurrentUserService currentUserService) {
        this.assignmentService = assignmentService;
        this.debtService = debtService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/items/{itemId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AssignmentDtos.AssignmentResponse> assign(
            @PathVariable Long receiptId,
            @PathVariable Long itemId,
            @Valid @RequestBody AssignmentDtos.AssignmentCreateRequest request) {
        User me = currentUserService.require();
        return assignmentService.assignDtos(
                me, receiptId, itemId, request.splitType(), request.targets());
    }

    @GetMapping("/items/{itemId}/assignments")
    public List<AssignmentDtos.AssignmentResponse> list(
            @PathVariable Long receiptId, @PathVariable Long itemId) {
        User me = currentUserService.require();
        return assignmentService.listForItemDtos(me, receiptId, itemId);
    }

    @DeleteMapping("/items/{itemId}/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long receiptId,
                       @PathVariable Long itemId,
                       @PathVariable Long assignmentId) {
        User me = currentUserService.require();
        assignmentService.remove(me, receiptId, itemId, assignmentId);
    }

    @PostMapping("/finalize")
    @ResponseStatus(HttpStatus.CREATED)
    public List<DebtDtos.DebtSummary> finalize(
            @PathVariable Long receiptId,
            @RequestParam(value = "dueDate", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate dueDate) {
        User me = currentUserService.require();
        return debtService.finalizeReceiptSummaries(me, receiptId, dueDate);
    }
}
