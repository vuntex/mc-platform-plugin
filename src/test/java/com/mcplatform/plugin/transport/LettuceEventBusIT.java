package com.mcplatform.plugin.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.protocol.PlatformProtocol;
import com.mcplatform.protocol.core.MessageProtocol;
import com.mcplatform.protocol.economy.BalanceChangedEvent;
import com.mcplatform.protocol.economy.BalanceChangedEventCodec;
import com.mcplatform.protocol.economy.EconomyChannels;

import io.lettuce.core.KillArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Live proof of the read-only event path against a real Redis (Testcontainers), like the backend's
 * infra-cache tests: a published envelope (encoded with the SAME {@code plugin-protocol} codec the
 * backend uses) is decoded and delivered as a typed event; out-of-order versions don't overwrite a
 * newer cache; and the subscription survives a forced reconnect. Skipped when Docker is unavailable.
 */
class LettuceEventBusIT {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TXN = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private static GenericContainer<?> redis;

    private final MessageProtocol protocol = PlatformProtocol.create();
    private final Logger logger = Logger.getLogger("test");

    /** runSync inline; runAsync on a thread. The bus only uses runSync (handler delivery). */
    private final PlatformScheduler scheduler = new PlatformScheduler() {
        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void runAsync(Runnable task) {
            Thread t = new Thread(task, "it-async");
            t.setDaemon(true);
            t.start();
        }
    };

    private LettuceEventBus bus;
    private RedisClient control;
    private StatefulRedisConnection<String, String> controlConnection;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
    }

    @AfterAll
    static void stopContainer() {
        if (redis != null) {
            redis.stop();
        }
    }

    @AfterEach
    void tearDown() {
        if (bus != null) {
            bus.close();
        }
        if (controlConnection != null) {
            controlConnection.close();
        }
        if (control != null) {
            control.shutdown();
        }
    }

    private String host() {
        return redis.getHost();
    }

    private int port() {
        return redis.getMappedPort(6379);
    }

    private BalanceChangedEvent event(long balance, long version) {
        return new BalanceChangedEvent(PLAYER, "COINS", "CREDITED", 10L, balance, version,
                TXN, "PLUGIN:test", null, 1_700_000_000_000L);
    }

    private void publish(long balance, long version) {
        controlConnection.sync().publish(EconomyChannels.BALANCE,
                protocol.encode(BalanceChangedEventCodec.INSTANCE, event(balance, version)));
    }

    private void openControl() {
        control = RedisClient.create(RedisURI.builder().withHost(host()).withPort(port()).build());
        controlConnection = control.connect();
    }

    private static void pollUntil(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Publish until the handler confirms it's receiving — proves the subscription is live. */
    private void awaitSubscription(AtomicInteger receivedCount) {
        int before = receivedCount.get();
        pollUntil(() -> {
            publish(1L, 1L);
            return receivedCount.get() > before;
        }, 10_000);
        assertTrue(receivedCount.get() > before, "subscription never became active");
    }

    @Test
    void publishDeliversTypedEvent() {
        AtomicReference<BalanceChangedEvent> received = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        bus = new LettuceEventBus(protocol, host(), port(), null, scheduler, logger);
        bus.subscribe(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE, e -> {
            received.set(e);
            count.incrementAndGet();
        });
        bus.start();
        openControl();

        awaitSubscription(count);
        publish(350L, 42L);
        pollUntil(() -> {
            BalanceChangedEvent e = received.get();
            return e != null && e.version() == 42L;
        }, 5_000);

        BalanceChangedEvent result = received.get();
        assertNotNull(result);
        assertEquals(42L, result.version());
        assertEquals(350L, result.balance());
        assertEquals("COINS", result.currencyCode());
        assertEquals(PLAYER, result.playerUuid());
    }

    @Test
    void outOfOrderDoesNotOverwriteNewerCache() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();
        AtomicInteger count = new AtomicInteger();
        bus = new LettuceEventBus(protocol, host(), port(), null, scheduler, logger);
        bus.subscribe(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE, e -> {
            cache.put(e.playerUuid(), e.balance(), e.version());
            count.incrementAndGet();
        });
        bus.start();
        openControl();

        awaitSubscription(count); // warmup uses version 1 (overwritten below)
        int base = count.get();

        publish(250L, 8L); // newer
        publish(999L, 3L); // stale
        pollUntil(() -> count.get() >= base + 2, 5_000);

        assertEquals(250L, cache.get(PLAYER).orElseThrow());
        assertEquals(8L, cache.version(PLAYER).orElseThrow());
    }

    @Test
    void resubscribesAfterReconnect() {
        AtomicInteger count = new AtomicInteger();
        bus = new LettuceEventBus(protocol, host(), port(), null, scheduler, logger);
        bus.subscribe(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE, e -> count.incrementAndGet());
        bus.start();
        openControl();

        awaitSubscription(count);

        // Force a reconnect: kill the bus's pub/sub connection server-side (control is TYPE normal).
        controlConnection.sync().clientKill(KillArgs.Builder.typePubsub());

        // After Lettuce reconnects, the state listener re-subscribes; a fresh publish must arrive.
        int afterKill = count.get();
        pollUntil(() -> {
            publish(500L, 99L);
            return count.get() > afterKill;
        }, 15_000);

        assertTrue(count.get() > afterKill, "no message received after reconnect — resubscribe failed");
    }
}
