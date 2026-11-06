package com.stagepass.payment.service;

import com.stagepass.payment.config.RazorpayProperties;
import com.stagepass.payment.service.RazorpayModels.Order;
import com.stagepass.payment.service.RazorpayModels.Payment;
import com.stagepass.payment.service.RazorpayModels.Refund;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Razorpay REST API v1 (orders, payments, captures, refunds) with HTTP Basic auth. */
@Component
public class RazorpayClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RazorpayProperties props;
    private final RestClient http;

    public RazorpayClient(RazorpayProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        String auth = props.configured() ? "Basic " + Base64.getEncoder()
                .encodeToString((props.keyId() + ":" + props.keySecret()).getBytes(StandardCharsets.UTF_8)) : "";
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, auth).build();
    }

    public String keyId() {
        return props.keyId();
    }

    public Order createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode n = call(() -> post("/v1/orders", body));
        return new Order(n.path("id").asString(), n.path("amount").asLong(), n.path("status").asString());
    }

    public Payment fetchPayment(String paymentId) {
        return payment(call(() -> http.get().uri("/v1/payments/{id}", paymentId).retrieve().body(String.class)));
    }

    public Payment capture(String paymentId, long amountPaise) {
        return payment(call(() -> post("/v1/payments/" + paymentId + "/capture",
                Map.of("amount", amountPaise, "currency", "INR"))));
    }

    /** Refunds Razorpay holds for a payment, with our {@code notes.refundId} (to reconcile unanswered refund calls). */
    public java.util.List<RazorpayModels.RefundRecord> listRefunds(String paymentId) {
        JsonNode n = call(() -> http.get().uri("/v1/payments/{id}/refunds?count=100", paymentId).retrieve().body(String.class));
        java.util.List<RazorpayModels.RefundRecord> out = new java.util.ArrayList<>();
        for (JsonNode r : n.path("items")) {
            String ours = r.path("notes").path("refundId").asString("");
            out.add(new RazorpayModels.RefundRecord(r.path("id").asString(), r.path("amount").asLong(),
                    r.path("status").asString(), ours.isEmpty() ? null : ours));
        }
        return out;
    }

    public Refund refund(String paymentId, long amountPaise, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("speed", "normal");
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode n = call(() -> post("/v1/payments/" + paymentId + "/refund", body));
        return new Refund(n.path("id").asString(), n.path("payment_id").asString(), n.path("amount").asLong(),
                n.path("status").asString());
    }

    private String post(String path, Object body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
    }

    private JsonNode call(Supplier<String> request) {
        if (!props.configured()) {
            throw new RazorpayException("Razorpay keys are not configured", 0, false);
        }
        try {
            return JSON.readTree(request.get());
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String description = JSON.readTree(e.getResponseBodyAsString().isBlank() ? "{}" : e.getResponseBodyAsString())
                    .path("error").path("description").asString("Razorpay error");
            throw new RazorpayException(description, status, status >= 500 || status == 429);
        } catch (ResourceAccessException e) {
            throw new RazorpayException("Razorpay is unreachable: " + e.getMessage(), 0, true);
        }
    }

    private static Payment payment(JsonNode n) {
        return new Payment(n.path("id").asString(), n.path("order_id").asString(null), n.path("amount").asLong(),
                n.path("currency").asString(), n.path("status").asString(), n.path("method").asString(null),
                n.hasNonNull("fee") ? n.path("fee").asLong() : null, n.hasNonNull("tax") ? n.path("tax").asLong() : null,
                n.path("error_description").asString(null), n.toString());
    }
}
