package com.cryptonex.security;

import com.cryptonex.service.RateLimitingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private final RateLimitingService rateLimitingService;
    private final IpBanService ipBanService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitingFilter(RateLimitingService rateLimitingService, IpBanService ipBanService,
            MeterRegistry meterRegistry) {
        this.rateLimitingService = rateLimitingService;
        this.ipBanService = ipBanService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = getClientIp(request);

        // 1. Check if IP is banned
        if (ipBanService.isBanned(clientIp)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Your IP has been temporarily banned due to excessive requests.");
            return;
        }

        // 2. Global Rate Limit Check
        Bucket globalBucket = rateLimitingService.resolveBucket("global_ip_" + clientIp);
        ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);
        if (!globalProbe.isConsumed()) {
            meterRegistry.counter("cryptello.ratelimit.hit", "type", "global", "ip", clientIp).increment();
            ipBanService.recordViolation(clientIp);
            sendRateLimitResponse(response, globalProbe.getNanosToWaitForRefill() / 1_000_000_000);
            return;
        }

        String bucketKey = null;

        if (path.startsWith("/api/webhooks/")) {
            // Webhook Logic
            String stripeSig = request.getHeader("Stripe-Signature");
            String cashfreeSig = request.getHeader("x-webhook-signature");

            if (stripeSig != null || cashfreeSig != null) {
                bucketKey = "webhook_trusted_" + clientIp;
            } else {
                bucketKey = "webhook_untrusted_" + clientIp;
            }

        } else if (path.startsWith("/api/payments/")) {
            // Payment Logic
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                bucketKey = "payment_user_" + auth.getName();
            } else {
                bucketKey = "payment_ip_" + clientIp;
            }

        } else if (path.startsWith("/auth/signin")) {
            bucketKey = "auth_signin_ip_" + clientIp;
        } else if (path.startsWith("/auth/signup")) {
            bucketKey = "auth_signup_ip_" + clientIp;
        }

        if (bucketKey != null) {
            Bucket bucket = rateLimitingService.resolveBucket(bucketKey);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (!probe.isConsumed()) {
                long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
                meterRegistry.counter("cryptello.ratelimit.hit", "type", "specific", "key", bucketKey).increment();
                logger.warn("Rate limit exceeded for key: {} (IP: {}). Wait: {}s", bucketKey, clientIp, waitForRefill);
                sendRateLimitResponse(response, waitForRefill);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendRateLimitResponse(HttpServletResponse response, long waitForRefill) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 429);
        errorResponse.put("error", "Too Many Requests");
        errorResponse.put("message", "Rate limit exceeded. Please try again later. Wait " + waitForRefill + "s");

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
