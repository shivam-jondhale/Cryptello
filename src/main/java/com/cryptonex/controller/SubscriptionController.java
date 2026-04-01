package com.cryptonex.controller;

import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import com.cryptonex.service.SubscriptionService;
import com.cryptonex.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserService userService;

    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlan> createPlan(
            @RequestHeader("Authorization") String jwt,
            @RequestBody Map<String, Object> request) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        BigDecimal price = new BigDecimal(request.get("price").toString());
        int durationMonths = (int) request.get("durationMonths");

        SubscriptionPlan plan = subscriptionService.createTraderPlan(user, name, description, price, durationMonths);
        return new ResponseEntity<>(plan, HttpStatus.CREATED);
    }

    @GetMapping("/plans/trader/{traderId}")
    public ResponseEntity<List<SubscriptionPlan>> getTraderPlans(@PathVariable Long traderId) {
        List<SubscriptionPlan> plans = subscriptionService.getTraderPlans(traderId);
        return new ResponseEntity<>(plans, HttpStatus.OK);
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        List<SubscriptionPlan> plans = subscriptionService.getAllPlans();
        return new ResponseEntity<>(plans, HttpStatus.OK);
    }

    @Autowired
    private com.cryptonex.payment.PaymentService paymentService;

    @PostMapping("/subscribe/{planId}")
    public ResponseEntity<com.cryptonex.response.PaymentResponse> subscribe(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long planId) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        SubscriptionPlan plan = subscriptionService.getPlan(planId);

        Long traderId = null;
        if (plan.getPlanType() == com.cryptonex.domain.PlanType.TRADER) {
            traderId = plan.getTrader().getId();
        }

        com.cryptonex.response.PaymentResponse response = paymentService.createSubscriptionPayment(user, planId,
                traderId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
