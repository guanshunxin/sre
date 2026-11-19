package com.gsx.shop.lab.dto;

import jakarta.validation.constraints.*;

/** 有限演练类型与持续时间；资源类场景另有更小上限。 */
public record LabRequest(@NotNull Scenario scenario, @Min(1) @Max(60) int seconds) {
    public enum Scenario {
        DB_LOCK,
        DB_POOL,
        CPU,
        HEAP,
        CONSUMER_PAUSE
    }
}
