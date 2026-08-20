package com.shipflow.sf.client;

import com.shipflow.sf.application.SfIntegrationException;
import com.shipflow.sf.config.SfProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSfApiClient implements SfApiClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultSfApiClient.class);
    private static final Pattern BUSINESS_CODE = Pattern.compile("\\\"(?:code|errorCode|resultCode)\\\"\\s*:\\s*\\\"([^\\\"]{1,64})\\\"");
    private final SfProperties properties;
    private final SfSignUtil signUtil;
    private final HttpClient httpClient;

    public DefaultSfApiClient(SfProperties properties, SfSignUtil signUtil) {
        this(properties, signUtil, HttpClient.newBuilder().build());
    }

    DefaultSfApiClient(SfProperties properties, SfSignUtil signUtil, HttpClient httpClient) {
        this.properties = properties;
        this.signUtil = signUtil;
        this.httpClient = httpClient;
    }

    @Override
    public SfApiResponse execute(SfApiRequest request) {
        properties.validateForCall();
        SfApiRequest signed = request.withDigest(signUtil.digest(request, properties.getCheckWord()));
        String body = form(signed.formFields());
        int attempts = 0;
        while (true) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.getApiBaseUrl()))
                        .timeout(Duration.ofMillis(properties.getApiTimeoutMs()))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (retryable(response.statusCode()) && attempts < properties.getApiRetryLimit()) {
                    attempts++;
                    continue;
                }
                String businessCode = extractBusinessCode(response.body());
                log.info("sf_call serviceCode={} requestID={} httpStatus={} businessCode={}",
                        request.serviceCode(), request.requestId(), response.statusCode(), businessCode);
                return new SfApiResponse(response.statusCode(), request.requestId(), response.body(),
                        request.serviceCode(), businessCode);
            } catch (IOException exception) {
                if (attempts++ < properties.getApiRetryLimit()) {
                    continue;
                }
                throw new SfIntegrationException("SF-1005", 503, "顺丰服务暂时不可用，请稍后重试");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SfIntegrationException("SF-1006", 503, "顺丰请求被中断，请稍后重试");
            }
        }
    }

    private boolean retryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String extractBusinessCode(String body) {
        if (body == null || body.length() > 16_384) return null;
        Matcher matcher = BUSINESS_CODE.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
