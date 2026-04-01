package com.cryptonex.controller;

import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.WebhookEvent;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.WebhookEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @GetMapping("/reconcile")
    public ResponseEntity<List<WebhookEvent>> getAllWebhookEvents() {
        // In a real app, add pagination
        return ResponseEntity.ok(webhookEventRepository.findAll());
    }

    @PostMapping("/reconcile/{id}/retry")
    public ResponseEntity<String> retryWebhook(@PathVariable Long id) {
        // Logic to re-process a failed webhook event
        // For now, just a placeholder
        return ResponseEntity.ok("Retry initiated for event " + id);
    }

    @Autowired
    private com.cryptonex.user.UserService userService;

    @PutMapping("/users/{userId}/role/{role}")
    public ResponseEntity<String> updateUserRole(@PathVariable Long userId, @PathVariable String role)
            throws Exception {
        com.cryptonex.model.User user = userService.findUserById(userId);
        com.cryptonex.domain.USER_ROLE userRole = com.cryptonex.domain.USER_ROLE.valueOf(role);
        user.getRoles().add(userRole);
        userService.saveUser(user); // Assuming updateUser saves the user
        return ResponseEntity.ok("Role " + role + " added to user " + userId);
    }
}
