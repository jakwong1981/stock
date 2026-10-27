package com.cyberstock;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end API test against a real TimescaleDB container. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "app.cors.allowed-origins=http://localhost",
        "app.market.tick-interval-ms=100"
})
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb:2.31.0-pg17").asCompatibleSubstituteFor("postgres"));

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = JsonMapper.builder().build();

    private HttpResponse<String> call(String method, String path, String body, String token, String lang) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (lang != null) b.header("Accept-Language", lang);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String q(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    void instrumentsAreLocalisedAndSearchableAcrossScripts() throws Exception {
        JsonNode cn = json.readTree(call("GET", "/api/instruments/0700.HK", null, null, "zh-CN").body());
        assertThat(cn.path("name").asString()).isEqualTo("腾讯控股");
        JsonNode hk = json.readTree(call("GET", "/api/instruments/0700.HK", null, null, "zh-HK").body());
        assertThat(hk.path("name").asString()).isEqualTo("騰訊控股");

        // Simplified query finds the instrument even when the UI is in Traditional Chinese
        JsonNode s1 = json.readTree(call("GET", "/api/instruments/search?q=" + q("腾讯"), null, null, "zh-HK").body());
        assertThat(s1.get(0).path("symbol").asString()).isEqualTo("0700.HK");
        assertThat(s1.get(0).path("name").asString()).isEqualTo("騰訊控股");
        JsonNode s2 = json.readTree(call("GET", "/api/instruments/search?q=nvd", null, null, "en-US").body());
        assertThat(s2.get(0).path("symbol").asString()).isEqualTo("NVDA");

        assertThat(call("GET", "/api/instruments/NOPE", null, null, null).statusCode()).isEqualTo(404);
    }

    @Test
    void quotesAndCandlesUseStringDecimals() throws Exception {
        JsonNode quotes = json.readTree(call("GET", "/api/quotes?symbols=AAPL", null, null, null).body());
        assertThat(quotes.get(0).path("price").isString()).isTrue();

        JsonNode candles = json.readTree(call("GET", "/api/candles?symbol=AAPL&interval=1d&limit=50", null, null, null).body());
        assertThat(candles.size()).isEqualTo(50);
        assertThat(candles.get(0).path("close").isString()).isTrue();

        HttpResponse<String> bad = call("GET", "/api/candles?symbol=AAPL&interval=7x", null, null, null);
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(json.readTree(bad.body()).path("code").asString()).isEqualTo("INVALID_INTERVAL");
    }

    @Test
    void portfolioRequiresAuthAndEnforcesHoldings() throws Exception {
        assertThat(call("GET", "/api/portfolio/positions", null, null, null).statusCode()).isEqualTo(401);

        String email = "u" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> reg = call("POST", "/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"correct-horse-1\",\"locale\":\"zh-TW\"}", null, null);
        assertThat(reg.statusCode()).isEqualTo(201);
        String token = json.readTree(reg.body()).path("accessToken").asString();
        assertThat(json.readTree(reg.body()).path("user").path("preferredLocale").asString()).isEqualTo("zh-TW");

        assertThat(call("POST", "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}", null, null).statusCode()).isEqualTo(401);

        HttpResponse<String> buy = call("POST", "/api/portfolio/transactions",
                "{\"symbol\":\"aapl\",\"side\":\"BUY\",\"quantity\":\"10\",\"price\":\"100.10\",\"fee\":\"1\"}", token, null);
        assertThat(buy.statusCode()).isEqualTo(201);
        assertThat(json.readTree(buy.body()).path("currency").asString()).isEqualTo("USD");

        HttpResponse<String> oversell = call("POST", "/api/portfolio/transactions",
                "{\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":\"11\",\"price\":\"100\"}", token, null);
        assertThat(oversell.statusCode()).isEqualTo(422);
        assertThat(json.readTree(oversell.body()).path("code").asString()).isEqualTo("INSUFFICIENT_POSITION");

        JsonNode pos = json.readTree(call("GET", "/api/portfolio/positions", null, token, null).body());
        assertThat(pos.path("positions").get(0).path("quantity").asString()).isEqualTo("10.000000");
        assertThat(pos.path("positions").get(0).path("costBasis").asString()).isEqualTo("1002.000000");

        // Another user cannot see this user's ledger
        HttpResponse<String> reg2 = call("POST", "/api/auth/register",
                "{\"email\":\"o" + UUID.randomUUID() + "@example.com\",\"password\":\"correct-horse-2\"}", null, null);
        assertThat(reg2.statusCode()).isEqualTo(201);
        String token2 = json.readTree(reg2.body()).path("accessToken").asString();
        HttpResponse<String> other = call("GET", "/api/portfolio/transactions", null, token2, null);
        assertThat(other.statusCode()).isEqualTo(200);
        assertThat(json.readTree(other.body()).isArray()).isTrue();
        assertThat(json.readTree(other.body()).size()).isZero();

        HttpResponse<String> wl = call("PUT", "/api/watchlist", "{\"symbols\":[\"NVDA\",\"bogus\",\"0700.hk\"]}", token, null);
        assertThat(json.readTree(wl.body()).path("symbols").toString()).isEqualTo("[\"NVDA\",\"0700.HK\"]");
    }

    @Test
    void webSocketStreamsSnapshotAndQuotes() throws Exception {
        CompletableFuture<String> snapshot = new CompletableFuture<>();
        CompletableFuture<String> quote = new CompletableFuture<>();
        WebSocket ws = http.newWebSocketBuilder()
                .header("Origin", "http://localhost")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/quotes"), new WebSocket.Listener() {
                    final StringBuilder buf = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            String msg = buf.toString();
                            buf.setLength(0);
                            if (msg.contains("\"snapshot\"")) snapshot.complete(msg);
                            if (msg.contains("\"quote\"")) quote.complete(msg);
                        }
                        w.request(1);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
        ws.sendText("{\"action\":\"subscribe\",\"symbols\":[\"*\"]}", true);
        assertThat(json.readTree(snapshot.get(5, TimeUnit.SECONDS)).path("data").size()).isGreaterThan(5);
        assertThat(json.readTree(quote.get(10, TimeUnit.SECONDS)).path("data").path("price").isString()).isTrue();
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }
}
