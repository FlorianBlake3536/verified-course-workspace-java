package edu.example.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Component
public class InfraiGateway implements WorkspaceJoin.Directory {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String key;

    public InfraiGateway(@Value("${infrai.base-url}") String baseUrl,
                         @Value("${infrai.api-key}") String key) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.key = key;
    }

    public Map<String, Object> verifyDomain(String domain) {
        return call("POST", "/v1/dns/domain/verify", Map.of("domain", domain));
    }

    public Map<String, Object> createUser(String email, String name, String idempotencyKey) {
        return call("POST", "/v1/auth/user/create", Map.of("email", email, "name", name,
                "idempotency_key", idempotencyKey));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> body) {
        try {
            String payload = json.writeValueAsString(body);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(payload)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 && attempt < 3) {
                    long delay = response.headers().firstValue("Retry-After").map(InfraiGateway::retrySeconds)
                            .orElse(1L << attempt);
                    Thread.sleep(Math.min(30, delay) * 1000);
                    continue;
                }
                // Business rejections carry an envelope even when the HTTP status is 4xx.
                JsonNode envelope = json.readTree(response.body());
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new ApiError(error.path("code").asText("REQUEST_REJECTED"),
                            error.path("message").asText("Request rejected"), response.statusCode());
                }
                if (response.statusCode() >= 500) throw new ApiError("UPSTREAM_ERROR", "Upstream request failed", 502);
                return json.convertValue(envelope.path("data"), Map.class);
            }
            throw new ApiError("RATE_LIMIT", "Retry later", 429);
        } catch (ApiError error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted", error);
        } catch (Exception error) {
            throw new IllegalStateException("Could not complete request", error);
        }
    }

    private static long retrySeconds(String value) {
        try { return Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 1; }
    }

    public static class ApiError extends RuntimeException {
        public final String code;
        public final int status;
        public ApiError(String code, String message, int status) {
            super(message); this.code = code; this.status = status;
        }
    }
}
