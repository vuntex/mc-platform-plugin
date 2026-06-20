package com.mcplatform.plugin.transport;

import com.mcplatform.protocol.core.MessageCodec;

import java.util.function.Consumer;

/**
 * Generic, feature-agnostic live-update bus: features subscribe to a channel with a
 * {@link MessageCodec} from {@code plugin-protocol} and receive already-decoded, typed messages.
 * Read-only by design — the plugin never publishes here.
 *
 * <p>Real (Redis Pub/Sub) implementation arrives in prompt 5; this skeleton ships {@link StubEventBus}.
 */
public interface EventBus {

    /** Subscribe to {@code channel}; each message of the codec's type is decoded and delivered. */
    <T> void subscribe(String channel, MessageCodec<T> codec, Consumer<T> handler);
}
