package com.mcplatform.plugin.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.protocol.PlatformProtocol;
import com.mcplatform.protocol.core.MessageEnvelope;
import com.mcplatform.protocol.core.MessageProtocol;
import com.mcplatform.protocol.economy.BalanceChangedEvent;
import com.mcplatform.protocol.economy.BalanceChangedEventCodec;
import com.mcplatform.protocol.economy.EconomyChannels;

import org.junit.jupiter.api.Test;

/**
 * Proves the decoding/routing + version-aware staleness against the real {@code plugin-protocol}, no
 * Redis: decode a wire into the typed event, route only matching types, isolate bad input, and — via
 * a {@link FeatureCache} keyed on {@code version} — never let an out-of-order event overwrite a newer
 * cached state.
 */
class EventDispatcherTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TXN = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private final MessageProtocol protocol = PlatformProtocol.create();
    private final Logger logger = Logger.getLogger("test");

    /** runSync inline so handlers execute synchronously in the test. */
    private final PlatformScheduler scheduler = new PlatformScheduler() {
        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }
    };

    private BalanceChangedEvent event(long balance, long version) {
        return new BalanceChangedEvent(PLAYER, "COINS", "CREDITED", 10L, balance, version,
                TXN, "PLUGIN:test", null, 1_700_000_000_000L);
    }

    @Test
    void decodesAndDeliversTypedEvent() {
        AtomicReference<BalanceChangedEvent> received = new AtomicReference<>();
        EventDispatcher dispatcher = new EventDispatcher(protocol, scheduler, logger);
        dispatcher.register(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE, received::set);

        String wire = protocol.encode(BalanceChangedEventCodec.INSTANCE, event(350L, 42L));
        dispatcher.dispatch(EconomyChannels.BALANCE, wire);

        assertEquals(event(350L, 42L), received.get());
    }

    @Test
    void outOfOrderEventDoesNotOverwriteNewerCache() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();
        EventDispatcher dispatcher = new EventDispatcher(protocol, scheduler, logger);
        dispatcher.register(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE,
                e -> cache.put(e.playerUuid(), e.balance(), e.version()));

        // Newer event first (version 8), then a stale one (version 3).
        dispatcher.dispatch(EconomyChannels.BALANCE,
                protocol.encode(BalanceChangedEventCodec.INSTANCE, event(250L, 8L)));
        dispatcher.dispatch(EconomyChannels.BALANCE,
                protocol.encode(BalanceChangedEventCodec.INSTANCE, event(999L, 3L)));

        assertEquals(250L, cache.get(PLAYER).orElseThrow());
        assertEquals(8L, cache.version(PLAYER).orElseThrow());
    }

    @Test
    void unparseableMessageIsDropped() {
        AtomicReference<BalanceChangedEvent> received = new AtomicReference<>();
        EventDispatcher dispatcher = new EventDispatcher(protocol, scheduler, logger);
        dispatcher.register(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE, received::set);

        dispatcher.dispatch(EconomyChannels.BALANCE, "not a valid envelope");

        assertNull(received.get());
    }

    @Test
    void otherMessageTypeIsNotDelivered() {
        AtomicReference<BalanceChangedEvent> received = new AtomicReference<>();
        EventDispatcher dispatcher = new EventDispatcher(protocol, scheduler, logger);
        dispatcher.register(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE, received::set);

        String wire = new MessageEnvelope(MessageProtocol.PROTOCOL_VERSION, "other.type", "x|y").toWire();
        dispatcher.dispatch(EconomyChannels.BALANCE, wire);

        assertNull(received.get());
    }

    @Test
    void channelsReflectRegistrations() {
        EventDispatcher dispatcher = new EventDispatcher(protocol, scheduler, logger);
        dispatcher.register(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE, e -> { });

        assertTrue(dispatcher.channels().contains(EconomyChannels.BALANCE));
        assertEquals(1, dispatcher.channels().size());
    }
}
