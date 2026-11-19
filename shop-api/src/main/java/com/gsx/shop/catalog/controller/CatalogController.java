package com.gsx.shop.catalog.controller;

import com.gsx.shop.catalog.dto.RestockRequest;
import com.gsx.shop.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

/** 商品与补货入口，不直接操作 Mapper。 */
@RestController
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/products")
    public Object list() {
        return service.list();
    }

    @PostMapping("/api/products/{id}/restock")
    public Object restock(@PathVariable long id, @Valid @RequestBody RestockRequest request) {
        service.restock(id, request.quantity());
        return java.util.Map.of("updated", true);
    }
}
