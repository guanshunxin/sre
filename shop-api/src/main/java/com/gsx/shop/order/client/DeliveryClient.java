package com.gsx.shop.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsx.shop.common.config.AccessProperties;
import com.gsx.shop.common.exception.*;
import com.gsx.shop.order.config.DeliveryProperties;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** 固定上游的完整请求预算，不重试；调用在数据库事务外，超时不能留下预留库存。 */
@Component
public class DeliveryClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final DeliveryProperties properties;
    private final AccessProperties access;
    private final ObjectMapper json;

    public DeliveryClient(DeliveryProperties properties, AccessProperties access, ObjectMapper json) {
        this.properties = properties;
        this.access = access;
        this.json = json;
    }

    public long quote(String region, int quantity) {
        var request = HttpRequest.newBuilder(URI.create(
                        properties.baseUrl() + "/api/delivery/quote?region=" + region + "&quantity=" + quantity))
                .timeout(Duration.ofMillis(900))
                .header("Authorization", "Bearer " + access.apiToken())
                .header(
                        "X-Request-ID",
                        java.util.Optional.ofNullable(MDC.get("requestId")).orElse("internal"))
                .GET()
                .build();
        var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            var response = future.get(900, TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200 || response.body().length() > 4096)
                throw new ShopException(ShopError.DEPENDENCY_UNAVAILABLE);
            var fee = json.readTree(response.body()).path("feeCents");
            if (!fee.isIntegralNumber() || fee.asLong() < 0 || fee.asLong() > 100000)
                throw new ShopException(ShopError.DEPENDENCY_UNAVAILABLE);
            return fee.asLong();
        } catch (TimeoutException timedOut) {
            future.cancel(true);
            throw new ShopException(ShopError.DELIVERY_TIMEOUT);
        } catch (ExecutionException failed) {
            future.cancel(true);
            throw new ShopException(
                    failed.getCause() instanceof java.net.http.HttpTimeoutException
                            ? ShopError.DELIVERY_TIMEOUT
                            : ShopError.DEPENDENCY_UNAVAILABLE);
        } catch (java.io.IOException invalid) {
            throw new ShopException(ShopError.DEPENDENCY_UNAVAILABLE);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ShopException(ShopError.DEPENDENCY_UNAVAILABLE);
        }
    }
}
