package com.mcplatform.plugin.transport;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.protocol.core.MessageCodec;
import com.mcplatform.protocol.core.MessageProtocol;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decoding/routing brain of the {@link EventBus}, free of any Redis library so it can be unit-tested
 * with raw wire strings. Given a channel and a raw {@code MessageEnvelope} wire (from the same
 * {@code plugin-protocol} codec the backend publishes with), it peeks the message type, decodes, and
 * delivers the typed event to matching handlers.
 *
 * <p>Threading: decoding runs on the caller (Lettuce/Netty) thread; the handler is invoked on the
 * main thread via {@link PlatformScheduler#runSync} (Prinzip 5), in receive order. Staleness is the
 * handler's job: the typed event carries {@code version}, which the handler feeds into a
 * {@link FeatureCache} so out-of-order events never overwrite a newer state (PROGRESS §8).
 *
 * <p>Resilient: an unparseable message or a throwing handler is logged and isolated.
 */
public final class EventDispatcher {

    private static final class Registration<T> {
        private final MessageCodec<T> codec;
        private final Consumer<T> handler;

        Registration(MessageCodec<T> codec, Consumer<T> handler) {
            this.codec = codec;
            this.handler = handler;
        }

        void deliver(MessageProtocol protocol, PlatformScheduler scheduler, String wire) {
            T event = protocol.decode(wire, codec); // decode on the caller thread
            scheduler.runSync(() -> handler.accept(event)); // touch Bukkit only on main
        }
    }

    private final MessageProtocol protocol;
    private final PlatformScheduler scheduler;
    private final Logger logger;
    private final Map<String, List<Registration<?>>> byChannel = new ConcurrentHashMap<>();

    public EventDispatcher(MessageProtocol protocol, PlatformScheduler scheduler, Logger logger) {
        this.protocol = protocol;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public <T> void register(String channel, MessageCodec<T> codec, Consumer<T> handler) {
        byChannel.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>())
                .add(new Registration<>(codec, handler));
    }

    /** Channels with at least one registration — what the transport must (re)subscribe to. */
    public Set<String> channels() {
        return Set.copyOf(byChannel.keySet());
    }

    /** Decode a raw wire message and deliver it to matching handlers. Never throws. */
    public void dispatch(String channel, String wire) {
        List<Registration<?>> registrations = byChannel.get(channel);
        if (registrations == null || registrations.isEmpty()) {
            return;
        }

        String messageType;
        try {
            messageType = protocol.peek(wire).messageType();
        } catch (RuntimeException ex) {
            logger.warning(() -> "Dropping unparseable message on " + channel + ": " + ex.getMessage());
            return;
        }

        for (Registration<?> registration : registrations) {
            if (!registration.codec.messageType().equals(messageType)) {
                continue;
            }
            try {
                registration.deliver(protocol, scheduler, wire);
            } catch (RuntimeException ex) {
                logger.log(Level.WARNING, "Handler failed for " + messageType + " on " + channel, ex);
            }
        }
    }
}
