package com.gsx.shop.common.lab;

import com.gsx.shop.common.lab.dto.HttpFaultRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** 只有显式 demo 才注册故障入口；生产 profile 不注册，访问仍受两把独立口令保护。 */
@RestController
@Profile("demo & !prod & !production")
@ConditionalOnProperty(name = "lab.enabled", havingValue = "true")
public class HttpFaultController {

    private final FaultState state;

    public HttpFaultController(FaultState state) {
        this.state = state;
    }

    @GetMapping("/internal/faults")
    public FaultState.State get() {
        return state.current();
    }

    @PostMapping("/internal/faults")
    public FaultState.State set(@Valid @RequestBody HttpFaultRequest request) {
        return state.set(request.mode(), request.seconds());
    }

    @DeleteMapping("/internal/faults")
    public FaultState.State clear() {
        return state.set(FaultState.Mode.NONE, 1);
    }
}
