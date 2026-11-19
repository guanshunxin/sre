package com.gsx.shop.order.controller;

import com.gsx.shop.order.dto.CreateOrderRequest;
import com.gsx.shop.order.service.OrderService;
import com.gsx.shop.order.vo.OrderView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 订单 HTTP 边界，幂等键由客户端提交并在 MySQL 唯一约束下校验。 */
@RestController
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/api/orders")
    public java.util.List<OrderView> list() {
        return service.list().stream().map(OrderView::from).toList();
    }

    @GetMapping("/api/orders/{id}")
    public OrderView get(@PathVariable long id) {
        return OrderView.from(service.get(id));
    }

    @PostMapping("/api/orders")
    public OrderView create(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateOrderRequest request) {
        return OrderView.from(service.create(key, request));
    }

    @PostMapping("/api/orders/{id}/cancel")
    public OrderView cancel(@PathVariable long id) {
        return OrderView.from(service.cancel(id));
    }
}
