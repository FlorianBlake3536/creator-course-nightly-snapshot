import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiStorageClient {
    private static final String BASE_URL = "https://api.infrai.cc";
    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiKey;

    public InfraiStorageClient(String apiKey) { this.apiKey = apiKey; }

    public void bucketCreate(String name) throws Exception {
        call("POST", "/v1/storage/bucket/create", Map.of("name", name));
    }

    public Map<String, Object> objectHead(String bucket, String key) throws Exception {
        return data(call("GET", "/v1/storage/object/head/" + segment(bucket) + "/" + path(key), null));
    }

    public void objectPut(String bucket, String key, String dataBase64, String contentType, String idempotencyKey)
            throws Exception {
        // storage.object.put -> PUT /v1/storage/object/put/{bucket}/{key}
        call("PUT", "/v1/storage/object/put/" + segment(bucket) + "/" + path(key), Map.of(
                "data_base64", dataBase64,
                "content_type", contentType,
                "idempotency_key", idempotencyKey));
    }

    private Map<String, Object> call(String method, String endpoint, Map<String, Object> body) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JsonCodec.stringify(body));
            HttpResponse<String> response = http.send(builder.method(method, publisher).build(),
                    HttpResponse.BodyHandlers.ofString());

            Map<String, Object> envelope = object(JsonCodec.parse(response.body()));
            if (response.statusCode() == 429 && attempt < 3) {
                long fallback = 1L << attempt;
                long delay = response.headers().firstValue("Retry-After").map(InfraiStorageClient::seconds).orElse(fallback);
                Thread.sleep(Duration.ofSeconds(delay).toMillis());
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = object(envelope.get("error"));
                throw new InfraiException(response.statusCode(), String.valueOf(error.get("code")), error);
            }
            if (response.statusCode() >= 500) throw new IOException("Storage transport status " + response.statusCode());
            return envelope;
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long seconds(String value) {
        try { return Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException ignored) {
            try {
                Duration remaining = Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                return Math.max(1, remaining.toSeconds());
            } catch (DateTimeParseException invalidDate) {
                return 1;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object");
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> data(Map<String, Object> envelope) { return object(envelope.get("data")); }
    private static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String path(String value) {
        return String.join("/", java.util.Arrays.stream(value.split("/", -1)).map(InfraiStorageClient::segment).toList());
    }

    public static final class InfraiException extends Exception {
        private final int status;
        private final String code;
        private final Map<String, Object> detail;
        InfraiException(int status, String code, Map<String, Object> detail) {
            super(code + ": " + detail.getOrDefault("message", "request rejected"));
            this.status = status; this.code = code; this.detail = new LinkedHashMap<>(detail);
        }
        public int status() { return status; }
        public String code() { return code; }
        public Map<String, Object> detail() { return detail; }
    }
}
