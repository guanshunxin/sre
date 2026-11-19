package com.gsx.shop.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 业务、取证和演练使用不同口令；不允许空配置启动。 */
@Validated
@ConfigurationProperties("access")
public record AccessProperties(@NotBlank String apiToken, @NotBlank String metricsToken, @NotBlank String labToken) {
    @Override
    public String toString() {
        return "AccessProperties[redacted]";
    }
}
