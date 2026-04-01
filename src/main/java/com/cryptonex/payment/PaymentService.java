package com.cryptonex.payment;

import com.stripe.exception.StripeException;
import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.User;
import com.cryptonex.response.PaymentResponse;

public interface PaymentService {

        PaymentResponse createPaymentLink(PaymentOrder order);

        PaymentOrder getPaymentOrderById(Long id) throws Exception;

        Boolean ProccedPaymentOrder(PaymentOrder paymentOrder, String paymentId, String paymentLinkId);

        PaymentResponse createStripePaymentLink(User user, java.math.BigDecimal amount)
                        throws StripeException;

        PaymentResponse createCashfreePaymentLink(User user, java.math.BigDecimal amount);

        PaymentResponse createSubscriptionPayment(User user, Long planId, Long traderId);

        void processPaymentSuccess(PaymentOrder order);

        PaymentOrder createOrder(User user, java.math.BigDecimal amount, PaymentMethod paymentMethod);
}
