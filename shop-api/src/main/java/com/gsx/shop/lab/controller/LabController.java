package com.gsx.shop.lab.controller;

import com.gsx.shop.lab.dto.LabRequest;
import com.gsx.shop.lab.service.LabService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** 生产 profile 完全没有资源故障端点，认证由公共边界过滤器完成。 */
@RestController
@Profile("demo & !prod & !production")
@ConditionalOnProperty(name = "lab.enabled", havingValue = "true")
public class LabController {
    private final LabService service;

    public LabController(LabService service) {
        this.service = service;
    }

    @GetMapping("/internal/lab")
    public Object status() {
        return service.status();
    }

    @PostMapping("/internal/lab")
    public Object start(@Valid @RequestBody LabRequest request) {
        return service.start(request);
    }

    @DeleteMapping("/internal/lab")
    public Object clear() {
        return service.clear();
    }
}
