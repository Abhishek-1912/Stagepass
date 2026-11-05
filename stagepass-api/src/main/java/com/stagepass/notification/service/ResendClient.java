package com.stagepass.notification.service;

import com.stagepass.notification.config.ResendProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Thin client for Resend's {@code POST /emails}. Never throws: callers get a {@link Result}. */
@Component
public class ResendClient {

    public enum Outcome { SENT, RETRYABLE, REJECTED }

    public record Result(Outcome outcome, String error) {
    }

    private final ResendProperties props;
    private final RestClient http;

    public ResendClient(ResendProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    public boolean configured() {
        return props.configured();
    }

    /** @param idempotencyKey stable per email, so a retry after a timeout cannot send the email twice */
    public Result send(String idempotencyKey, String to, RenderedEmail email) {
        try {
            http.post().uri("/emails")
                    .header("Authorization", "Bearer " + props.apiKey())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", props.from(), "to", List.of(to), "subject", email.subject(),
                            "html", email.html(), "text", email.text()))
                    .retrieve()
                    .toBodilessEntity();
            return new Result(Outcome.SENT, null);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            Outcome outcome = status == 429 || status >= 500 ? Outcome.RETRYABLE : Outcome.REJECTED;
            return new Result(outcome, "HTTP " + status + ": " + truncate(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            return new Result(Outcome.RETRYABLE, "I/O: " + truncate(e.getMessage()));
        }
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 500 ? s.substring(0, 500) : s;
    }
}
