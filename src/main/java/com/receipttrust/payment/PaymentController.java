package com.receipttrust.payment;

import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentController(PaymentService paymentService, CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/debts/{debtId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDtos.PaymentResponse submit(@PathVariable Long debtId,
                                              @Valid @RequestBody PaymentDtos.PaymentCreateRequest request) {
        User me = currentUserService.require();
        return paymentService.submit(me, debtId, request.amount(), request.method(), request.notes());
    }

    @GetMapping("/api/debts/{debtId}/payments")
    public List<PaymentDtos.PaymentResponse> list(@PathVariable Long debtId) {
        User me = currentUserService.require();
        return paymentService.list(me, debtId);
    }

    @PostMapping("/api/payments/{id}/approve")
    public PaymentDtos.PaymentResponse approve(@PathVariable Long id) {
        User me = currentUserService.require();
        return paymentService.approve(me, id);
    }

    @PostMapping("/api/payments/{id}/reject")
    public PaymentDtos.PaymentResponse reject(@PathVariable Long id) {
        User me = currentUserService.require();
        return paymentService.reject(me, id);
    }
}
