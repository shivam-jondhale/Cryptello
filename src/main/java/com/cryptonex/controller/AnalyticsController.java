package com.cryptonex.controller;

import com.cryptonex.model.User;
import com.cryptonex.service.AnalyticsService;
import com.cryptonex.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UserService userService;

    @Autowired
    private com.cryptonex.repository.UserRepository userRepository; // Direct repo access for efficiency

    @GetMapping("/trader/{traderId}/metrics")
    public ResponseEntity<?> getTraderMetrics(@PathVariable Long traderId) {
        // 1. Validate Trader Exists & Has Role
        com.cryptonex.model.User trader = userRepository.findById(traderId).orElse(null);
        if (trader == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trader not found");
        }

        boolean isTrader = trader.getRoles().contains(com.cryptonex.domain.USER_ROLE.ROLE_TRADER) ||
                trader.getRoles().contains(com.cryptonex.domain.USER_ROLE.ROLE_VERIFIED_TRADER);

        if (!isTrader) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User is not a trader");
        }

        // 2. Return Public Metrics
        AnalyticsService.TraderMetrics metrics = analyticsService.getTraderMetrics(traderId);
        return new ResponseEntity<>(metrics, HttpStatus.OK);
    }

    @GetMapping("/user/{userId}/metrics")
    public ResponseEntity<?> getUserMetrics(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String jwt) throws Exception {

        // 1. Authenticate Caller
        User authUser = userService.findUserProfileByJwt(jwt);

        // 2. Enforce Access Control (Owner or Admin)
        boolean isOwner = authUser.getId().equals(userId);
        boolean isAdmin = authUser.getRoles().contains(com.cryptonex.domain.USER_ROLE.ROLE_ADMIN);

        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }

        // 3. Return Metrics
        AnalyticsService.UserMetrics metrics = analyticsService.getUserMetrics(userId);
        return new ResponseEntity<>(metrics, HttpStatus.OK);
    }
}
