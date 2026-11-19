package com.gsx.shop.delivery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 即便误配 lab.enabled，生产 profile 也不注册演练接口；监控与业务身份隔离。 */
@SpringBootTest(
        properties = {
            "SHOP_API_TOKEN=api-test",
            "SHOP_METRICS_TOKEN=metrics-test",
            "SHOP_LAB_TOKEN=lab-test",
            "lab.enabled=true"
        })
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionBoundaryTest {
    @Autowired
    MockMvc mvc;

    @Test
    void productionHasNoFaultEntryAndMetricsTokenCannotPlaceOrders() throws Exception {
        mvc.perform(get("/internal/faults")
                        .header("Authorization", "Bearer api-test")
                        .header("X-Lab-Token", "lab-test"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/delivery/quote?region=LOCAL&quantity=1").header("Authorization", "Bearer metrics-test"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/delivery/quote?region=LOCAL&quantity=1").header("Authorization", "Bearer api-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeCents").value(550));
        mvc.perform(get("/api/delivery/quote?region=LOCAL").header("Authorization", "Bearer api-test"))
                .andExpect(status().isBadRequest());
    }
}
