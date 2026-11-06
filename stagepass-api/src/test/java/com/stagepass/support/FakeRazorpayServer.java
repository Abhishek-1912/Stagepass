package com.stagepass.support;

import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Stand-in for the Razorpay REST API. Orders are auto-created; payments are scripted per id; any path can be
 * forced to an error status. Records every request.
 */
public final class FakeRazorpayServer implements AutoCloseable {

    public record Received(String method, String path, String authorization, String body) {
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Map<String, String> payments = new ConcurrentHashMap<>();
    private final Map<String, Integer> forcedStatus = new ConcurrentHashMap<>();
    private final AtomicInteger orderSeq = new AtomicInteger();
    private final AtomicInteger refundSeq = new AtomicInteger();

    public FakeRazorpayServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), path,
                    exchange.getRequestHeaders().getFirst("Authorization"), body));
            int status = 200;
            String reply;
            Integer forced = forcedFor(path);
            if (forced != null) {
                status = forced;
                reply = "{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"forced\"}}";
            } else if (path.equals("/v1/orders")) {
                String amount = field(body, "amount");
                reply = "{\"id\":\"order_fake" + orderSeq.incrementAndGet() + "\",\"amount\":" + amount
                        + ",\"currency\":\"INR\",\"status\":\"created\"}";
            } else if (path.matches("/v1/payments/[^/]+/capture")) {
                String id = path.split("/")[3];
                payments.computeIfPresent(id, (k, v) -> v.replace("\"authorized\"", "\"captured\""));
                reply = payments.getOrDefault(id, "{}");
            } else if (path.matches("/v1/payments/[^/]+/refund")) {
                String id = path.split("/")[3];
                reply = "{\"id\":\"rfnd_fake" + refundSeq.incrementAndGet() + "\",\"payment_id\":\"" + id
                        + "\",\"amount\":" + field(body, "amount") + ",\"status\":\"pending\"}";
            } else if (path.matches("/v1/payments/[^/]+")) {
                String id = path.split("/")[3];
                reply = payments.get(id);
                if (reply == null) {
                    status = 400;
                    reply = "{\"error\":{\"description\":\"The id provided does not exist\"}}";
                }
            } else {
                status = 404;
                reply = "{}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    private Integer forcedFor(String path) {
        return forcedStatus.entrySet().stream().filter(e -> path.startsWith(e.getKey())).map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private static String field(String json, String name) {
        var m = java.util.regex.Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : "0";
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Scripts {@code GET /v1/payments/{id}}. status: created | authorized | captured | failed. */
    public void payment(String paymentId, String orderId, long amountPaise, String status) {
        payments.put(paymentId, """
                {"id":"%s","entity":"payment","amount":%d,"currency":"INR","status":"%s","order_id":"%s",
                 "method":"upi","fee":2480,"tax":378,"error_description":null}
                """.formatted(paymentId, amountPaise, status, orderId));
    }

    public void failPath(String pathPrefix, int status) {
        forcedStatus.put(pathPrefix, status);
    }

    public void clearFailures() {
        forcedStatus.clear();
    }

    public List<Received> received() {
        return received;
    }

    public long count(Function<Received, Boolean> filter) {
        return received.stream().filter(filter::apply).count();
    }

    public static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
