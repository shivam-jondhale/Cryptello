package com.cryptonex.payment.service;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.response.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.HashMap;
import java.util.Map;

@Service
public class CashfreePaymentProvider implements PaymentProviderStrategy {

    @Value("${cashfree.api.url}")
    private String apiUrl;

    @Value("${cashfree.app.id}")
    private String appId;

    @Value("${cashfree.secret.key}")
    private String secretKey;

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    @Override
    public PaymentProvider providerType() {
        return PaymentProvider.CASHFREE;
    }

    @Override
    public PaymentResponse createOrder(PaymentOrder order) {
        try {
            // Construct Request Body
            Map<String, Object> customerDetails = new HashMap<>();
            customerDetails.put("customer_id", String.valueOf(order.getUser().getId()));
            customerDetails.put("customer_phone",
                    order.getUser().getMobile() != null ? order.getUser().getMobile() : "9999999999");
            customerDetails.put("customer_email", order.getUser().getEmail());
            customerDetails.put("customer_name", order.getUser().getFullName());

            Map<String, Object> orderMeta = new HashMap<>();
            orderMeta.put("return_url", "http://localhost:3000/payment/success?order_id={order_id}");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("order_amount", order.getAmount().doubleValue());
            requestBody.put("order_currency", order.getCurrency());
            requestBody.put("customer_details", customerDetails);
            requestBody.put("order_meta", orderMeta);

            // Generate a unique order ID if not present, but usually we send one or let
            // Cashfree generate?
            // Cashfree requires us to send 'order_id'.
            // Let's generate one if not set, or use a prefix + timestamp + random.
            // But wait, PaymentOrder ID is generated after save. We can use "ORDER_" +
            // order.getId()
            String orderId = "ORDER_" + order.getId() + "_" + System.currentTimeMillis();
            requestBody.put("order_id", orderId);

            String json = gson.toJson(requestBody);

            Request request = new Request.Builder()
                    .url(apiUrl + "/orders") // e.g. https://sandbox.cashfree.com/pg/orders
                    .addHeader("x-client-id", appId)
                    .addHeader("x-client-secret", secretKey)
                    .addHeader("x-api-version", "2023-08-01")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Cashfree API failed: " + response.body().string());
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                String cfOrderId = jsonResponse.get("order_id").getAsString();
                String paymentSessionId = jsonResponse.get("payment_session_id").getAsString();

                order.setProviderOrderId(cfOrderId);
                order.setProviderPaymentId(paymentSessionId);

                PaymentResponse paymentResponse = new PaymentResponse();
                paymentResponse.setPaymentLinkUrl(paymentSessionId);
                paymentResponse.setProviderOrderId(cfOrderId);

                return paymentResponse;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to create Cashfree order", e);
        }
    }
}
