package com.gsx.shop.lab.service.impl;

import com.gsx.shop.common.exception.*;
import com.gsx.shop.lab.dto.LabRequest;
import com.gsx.shop.lab.mapper.LabMapper;
import com.gsx.shop.lab.service.LabService;
import com.gsx.shop.lab.warning.LabWarning;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.env.*;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 真正持有行锁/连接或消耗受限资源；不修改数据库结构，不执行外部 shell。 */
@Service
public class LabServiceImpl implements LabService {
    private final LabMapper mapper;
    private final TransactionTemplate tx;
    private final KafkaListenerEndpointRegistry registry;
    private final Environment env;
    private final ExecutorService pool = Executors.newFixedThreadPool(6, r -> {
        var t = new Thread(r, "shop-lab");
        t.setDaemon(true);
        return t;
    });
    private final List<Future<?>> tasks = new ArrayList<>();
    private final AtomicInteger active = new AtomicInteger();
    private volatile Instant until = Instant.EPOCH;
    private volatile String scenario = "NONE";

    public LabServiceImpl(
            LabMapper mapper,
            PlatformTransactionManager manager,
            KafkaListenerEndpointRegistry registry,
            Environment env) {
        this.mapper = mapper;
        this.tx = new TransactionTemplate(manager);
        this.registry = registry;
        this.env = env;
    }

    private void requireDemo() {
        if (!env.getProperty("lab.enabled", Boolean.class, false)
                || !env.acceptsProfiles(Profiles.of("demo & !prod & !production")))
            throw new ShopException(ShopError.LAB_DISABLED);
    }

    public synchronized Map<String, Object> start(LabRequest request) {
        requireDemo();
        if (active.get() > 0 || Instant.now().isBefore(until)) throw new ShopException(ShopError.LAB_BUSY);
        int seconds = Math.min(
                request.seconds(),
                switch (request.scenario()) {
                    case DB_LOCK, DB_POOL -> 20;
                    case CPU -> 5;
                    case HEAP -> 30;
                    default -> 60;
                });
        tasks.clear();
        until = Instant.now().plusSeconds(seconds);
        scenario = request.scenario().name();
        int count = request.scenario() == LabRequest.Scenario.DB_POOL
                ? 6
                : request.scenario() == LabRequest.Scenario.CONSUMER_PAUSE ? 0 : 1;
        for (int i = 0; i < count; i++)
            tasks.add(pool.submit(() -> {
                active.incrementAndGet();
                try {
                    switch (request.scenario()) {
                        case DB_LOCK -> tx.executeWithoutResult(s -> {
                            mapper.lockProduct();
                            hold();
                        });
                        case DB_POOL -> tx.executeWithoutResult(s -> {
                            while (running()) mapper.waitSecond();
                        });
                        case CPU -> {
                            double value = .5;
                            while (running()) value = Math.sin(value) + Math.cos(value);
                            if (!Double.isFinite(value)) throw new IllegalStateException("CPU exercise result invalid");
                        }
                        case HEAP -> {
                            byte[] bytes = new byte[32 * 1024 * 1024];
                            Arrays.fill(bytes, (byte) 1);
                            hold();
                            java.lang.ref.Reference.reachabilityFence(bytes);
                        }
                        default -> {}
                    }
                } catch (RuntimeException failure) {
                    if (running())
                        org.slf4j.LoggerFactory.getLogger(getClass())
                                .warn(
                                        "{}; scenario={}, errorType={}",
                                        LabWarning.RESOURCE_EXERCISE_FAILED,
                                        request.scenario(),
                                        failure.getClass().getSimpleName());
                } finally {
                    active.decrementAndGet();
                }
            }));
        return status();
    }

    private boolean running() {
        return Instant.now().isBefore(until) && !Thread.currentThread().isInterrupted();
    }

    private void hold() {
        while (running())
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
    }

    public synchronized Map<String, Object> clear() {
        requireDemo();
        until = Instant.EPOCH;
        for (var task : tasks) task.cancel(true);
        reconcileConsumer();
        return status();
    }

    public Map<String, Object> status() {
        return Map.of(
                "scenario",
                Instant.now().isBefore(until) ? scenario : "NONE",
                "expiresAt",
                until,
                "activeTasks",
                active.get(),
                "consumerPaused",
                registry.getListenerContainers().stream().anyMatch(c -> c.isContainerPaused()));
    }

    public void reconcileConsumer() {
        boolean pause = scenario.equals("CONSUMER_PAUSE") && Instant.now().isBefore(until);
        for (var c : registry.getListenerContainers()) {
            if (pause && !c.isPauseRequested()) c.pause();
            else if (!pause && c.isPauseRequested()) c.resume();
        }
    }

    @PreDestroy
    public void close() {
        until = Instant.EPOCH;
        pool.shutdownNow();
    }
}
