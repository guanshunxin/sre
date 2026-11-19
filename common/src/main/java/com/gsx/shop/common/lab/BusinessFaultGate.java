package com.gsx.shop.common.lab;

import com.gsx.shop.common.exception.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** 在 MVC 内触发真实 503/延迟，使 Actuator 正确记录路由、状态和耗时；不影响健康/取证。 */
@Component
@Profile("demo & !prod & !production")
@ConditionalOnProperty(name = "lab.enabled", havingValue = "true")
public class BusinessFaultGate implements WebMvcConfigurer {
    private final FaultState state;

    public BusinessFaultGate(FaultState state) {
        this.state = state;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(
                            jakarta.servlet.http.HttpServletRequest r,
                            jakarta.servlet.http.HttpServletResponse s,
                            Object h)
                            throws Exception {
                        switch (state.current().mode()) {
                            case HTTP_5XX -> throw new ShopException(ShopError.INJECTED_FAILURE);
                            case SLOW -> Thread.sleep(1500);
                            default -> {}
                        }
                        return true;
                    }
                })
                .addPathPatterns("/api/**");
    }
}
