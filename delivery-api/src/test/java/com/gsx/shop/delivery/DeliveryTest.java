package com.gsx.shop.delivery;

import static org.assertj.core.api.Assertions.*;

import com.gsx.shop.common.exception.ShopException;
import com.gsx.shop.delivery.service.impl.DeliveryServiceImpl;
import org.junit.jupiter.api.Test;

/** 校验真实计算规则，金额为分，不模拟外部支付。 */
class DeliveryTest {
    @Test
    void computesRegionsAndRejectsInvalidInputs() {
        var s = new DeliveryServiceImpl();
        assertThat(s.quote("LOCAL", 2)).isEqualTo(600);
        assertThat(s.quote("REMOTE", 1)).isEqualTo(1550);
        assertThatThrownBy(() -> s.quote("OTHER", 1)).isInstanceOf(ShopException.class);
        assertThatThrownBy(() -> s.quote("LOCAL", 0)).isInstanceOf(ShopException.class);
    }
}
