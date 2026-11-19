package com.gsx.shop.common.lab;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** 进程内有界 HTTP 演练状态；每个请求先检查到期时间，重启默认无故障。 */
@Component
public class FaultState {
    public enum Mode {
        NONE,
        HTTP_5XX,
        SLOW
    }
    /** 记录操作结果的实际到期时间，不承诺已经触发告警。 */
    public record State(Mode mode, Instant expiresAt) {}

    private final AtomicReference<State> state = new AtomicReference<>(new State(Mode.NONE, Instant.EPOCH));

    public State current() {
        var s = state.get();
        return Instant.now().isBefore(s.expiresAt()) ? s : new State(Mode.NONE, Instant.EPOCH);
    }

    public State set(Mode mode, int seconds) {
        var s = new State(mode, Instant.now().plusSeconds(seconds));
        state.set(s);
        return s;
    }
}
