package com.gsx.shop.common.log;

import com.gsx.shop.common.exception.*;
import java.time.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 受取证口令保护的有界请求日志，不提供文件路径或任意日志查询。 */
@RestController
public class ObservationController {
    private final RequestObservations observations;

    public ObservationController(RequestObservations observations) {
        this.observations = observations;
    }

    @GetMapping("/internal/observations/logs")
    public Map<String, Object> logs(
            @RequestParam Instant start, @RequestParam Instant end, @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1
                || limit > 200
                || end.isBefore(start)
                || Duration.between(start, end).compareTo(Duration.ofHours(24)) > 0)
            throw new ShopException(ShopError.INVALID);
        return observations.read(start, end, limit);
    }
}
