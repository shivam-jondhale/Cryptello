package com.cryptonex.payment;

import com.stripe.exception.StripeException;
import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.common.exception.UserException;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.User;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.response.PaymentResponse;
import com.cryptonex.payment.PaymentService;
import com.cryptonex.user.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@RestController
@org.springframework.web.bind.annotation.RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private UserService userService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MeterRegistry meterRegistry;

    @PostMapping("/payment/{paymentMethod}/amount/{amount}")
    public ResponseEntity<PaymentResponse> paymentHandler(
            @PathVariable PaymentMethod paymentMethod,
            @PathVariable String amount,
            @RequestHeader("Authorization") String jwt) throws Exception {

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            User user = userService.findUserProfileByJwt(jwt);
            java.math.BigDecimal bigDecimalAmount;
            try {
                bigDecimalAmount = new java.math.BigDecimal(amount);
            } catch (NumberFormatException e) {
                meterRegistry.counter("cryptello.payment.failure", "type", "adhoc", "reason", "invalid_amount")
                        .increment();
                throw new Exception("Invalid amount format");
            }

            PaymentResponse paymentResponse;

            if (paymentMethod.equals(PaymentMethod.RAZORPAY)) {
                meterRegistry.counter("cryptello.payment.failure", "type", "adhoc", "reason", "razorpay_disabled")
                        .increment();
                throw new Exception("Razorpay is disabled");
            } else if (paymentMethod.equals(PaymentMethod.CASHFREE)) {
                paymentResponse = paymentService.createCashfreePaymentLink(user, bigDecimalAmount);
                meterRegistry.counter("cryptello.payment.success", "type", "adhoc", "provider", "cashfree").increment();
            } else {
                paymentResponse = paymentService.createStripePaymentLink(user, bigDecimalAmount);
                meterRegistry.counter("cryptello.payment.success", "type", "adhoc", "provider", "stripe").increment();
            }

            return new ResponseEntity<>(paymentResponse, HttpStatus.CREATED);
        } catch (Exception e) {
            meterRegistry.counter("cryptello.payment.failure", "type", "adhoc", "reason", "exception").increment();
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("cryptello.payment.latency", "type", "adhoc"));
        }
    }

    @PostMapping("/subscribe/{planId}")
    public ResponseEntity<PaymentResponse> subscribe(
            @PathVariable Long planId,
            @RequestParam(required = false) Long traderId,
            @RequestHeader("Authorization") String jwt) throws Exception {

        User user = userService.findUserProfileByJwt(jwt);
        PaymentResponse response = paymentService.createSubscriptionPayment(user, planId, traderId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

}