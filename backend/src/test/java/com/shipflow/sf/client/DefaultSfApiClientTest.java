package com.shipflow.sf.client;

import com.shipflow.sf.config.SfProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSfApiClientTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void postsSignedFormWithoutExposingPayloadToLogs() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":\"A1000\",\"msgData\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        TestProperties properties = new TestProperties("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        DefaultSfApiClient client = new DefaultSfApiClient(properties, new SfSignUtil(), HttpClient.newHttpClient());
        SfApiResponse response = client.execute(new SfApiRequest("partner", "request-1", "SERVICE", 1_700_000_000L, null, "{\"a\":1}"));

        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.businessCode()).isEqualTo("A1000");
        String form = body.get();
        assertThat(form).contains("serviceCode=SERVICE", "requestID=request-1", "msgDigest=");
    }

    private static final class TestProperties extends SfProperties {
        private final String url;
        private TestProperties(String url) { this.url = url; }
        @Override public String getApiBaseUrl() { return url; }
        @Override public String getCheckWord() { return "secret"; }
        @Override public long getApiTimeoutMs() { return 2_000; }
        @Override public int getApiRetryLimit() { return 0; }
        @Override public void validateForCall() { }
    }
}
