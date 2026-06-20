package com.mcplatform.plugin.transport;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.protocol.core.MessageCodec;
import com.mcplatform.protocol.core.MessageProtocol;

import java.net.SocketAddress;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * Lettuce-backed {@link EventBus} — the read-only live-update path. Lettuce is confined to this class
 * (features only ever see the {@link EventBus} interface and {@code plugin-protocol} types).
 *
 * <p>Subscriptions are collected via {@link #subscribe} (during feature enable); {@link #start()}
 * then opens ONE pub/sub connection and subscribes to all registered channels. Incoming raw strings
 * are routed by the {@link EventDispatcher}. Lettuce auto-reconnects; on every (re)connect a
 * {@link RedisConnectionStateListener} re-subscribes all channels, so a dropped connection recovers
 * its subscriptions.
 *
 * <p>The plugin only ever SUBSCRIBES; it never publishes and never reads {@code mc:bal:*} hash keys
 * directly — initial values come from REST on join (Prinzip 1, PROGRESS §8).
 */
public final class LettuceEventBus implements EventBus {

    private final EventDispatcher dispatcher;
    private final String host;
    private final int port;
    private final String password;
    private final Logger logger;

    private volatile RedisClient client;
    private volatile StatefulRedisPubSubConnection<String, String> connection;

    public LettuceEventBus(MessageProtocol protocol, String host, int port, String password,
                           PlatformScheduler scheduler, Logger logger) {
        this.dispatcher = new EventDispatcher(protocol, scheduler, logger);
        this.host = host;
        this.port = port;
        this.password = password;
        this.logger = logger;
    }

    @Override
    public <T> void subscribe(String channel, MessageCodec<T> codec, Consumer<T> handler) {
        dispatcher.register(channel, codec, handler);
    }

    /** Connect and start listening. Call once, after all features have registered subscriptions. */
    public void start() {
        Set<String> channels = dispatcher.channels();
        if (channels.isEmpty()) {
            logger.info("EventBus: no channels subscribed; not connecting to Redis.");
            return;
        }

        RedisURI.Builder uri = RedisURI.builder().withHost(host).withPort(port);
        if (password != null && !password.isBlank()) {
            uri.withPassword(password.toCharArray());
        }
        this.client = RedisClient.create(uri.build());

        // Re-subscribe on every (re)connect, so a dropped connection recovers its subscriptions.
        client.addListener(new RedisConnectionStateListener() {
            @Override
            public void onRedisConnected(RedisChannelHandler<?, ?> conn, SocketAddress socketAddress) {
                resubscribe();
            }
        });

        this.connection = client.connectPubSub();
        this.connection.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
                dispatcher.dispatch(channel, message);
            }
        });

        subscribeAll(); // initial subscribe (reconnects go through the listener above)
        logger.info("EventBus connected to Redis " + host + ":" + port + ", subscribed to " + channels);
    }

    private void resubscribe() {
        if (connection == null) {
            return; // initial connect: explicit subscribeAll() in start() handles it
        }
        try {
            subscribeAll();
            logger.info("EventBus re-subscribed after (re)connect.");
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "EventBus re-subscribe failed", ex);
        }
    }

    private void subscribeAll() {
        StatefulRedisPubSubConnection<String, String> current = connection;
        Set<String> channels = dispatcher.channels();
        if (current != null && !channels.isEmpty()) {
            current.async().subscribe(channels.toArray(new String[0]));
        }
    }

    /** Stop listening and release the connection. */
    public void close() {
        StatefulRedisPubSubConnection<String, String> current = connection;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        RedisClient currentClient = client;
        if (currentClient != null) {
            try {
                currentClient.shutdown();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }
}
