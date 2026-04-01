package com.cryptonex.controller;

import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.payment.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentService paymentService;

    @GetMapping
    public ResponseEntity<Page<PaymentOrder>> getAllPayments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new ResponseEntity<>(paymentOrderRepository.findAll(pageable), HttpStatus.OK);
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<PaymentOrder> updatePaymentStatus(
            @PathVariable Long orderId,
            @RequestParam PaymentOrderStatus status) {

        PaymentOrder order = paymentOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(status);
        paymentOrderRepository.save(order);

        if (status == PaymentOrderStatus.SUCCESS) {
            // Trigger success logic (subscription creation/extension)
            paymentService.processPaymentSuccess(order);
        }

        return new ResponseEntity<>(order, HttpStatus.OK);
    }
}
