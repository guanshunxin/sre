package com.gsx.shop.common.log;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** 每实例最多保留 2000 条真实业务请求；重启丢失历史，查询结果明确披露。 */
@Component
public class RequestObservations {
    /** 不含请求体、人员资料、原始地址或认证字段。 */
    public record Entry(Instant time, String requestId, String route, int status, long durationMs) {}

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Instant processStartedAt = Instant.now();

    public synchronized void append(Entry entry) {
        entries.addLast(entry);
        while (entries.size() > 2000) entries.removeFirst();
    }

    public synchronized Map<String, Object> read(Instant start, Instant end, int limit) {
        return Map.of(
                "processStartedAt",
                processStartedAt,
                "retention",
                "Newest 2000 requests in this process; restart loses history",
                "items",
                entries.stream()
                        .filter(e -> !e.time().isBefore(start) && !e.time().isAfter(end))
                        .toList()
                        .reversed()
                        .stream()
                        .limit(limit)
                        .toList());
    }
}
