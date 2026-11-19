package com.gsx.shop.common.lab.dto;

import com.gsx.shop.common.lab.FaultState;
import jakarta.validation.constraints.*;

/** HTTP 演练的边界请求，持续时间最多六十秒。 */
public record HttpFaultRequest(@NotNull FaultState.Mode mode, @Min(1) @Max(60) int seconds) {}
