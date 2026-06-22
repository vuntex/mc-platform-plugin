package com.mcplatform.plugin.feature.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.protocol.report.ChatMessage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** Bukkit-free proof of the global chat ring: bounded, newest-retained, immutable snapshot, thread-safe. */
class ChatRingBufferTest {

    private ChatMessage msg(String text) {
        return new ChatMessage(UUID.randomUUID(), text, 1_000L);
    }

    @Test
    void keepsOnlyTheLastCapacityMessagesOldestEvicted() {
        ChatRingBuffer ring = new ChatRingBuffer();
        int total = ChatRingBuffer.CAPACITY + 5;
        for (int i = 0; i < total; i++) {
            ring.add(msg("m" + i));
        }
        List<ChatMessage> snapshot = ring.snapshot();
        assertEquals(ChatRingBuffer.CAPACITY, snapshot.size());
        // Oldest five evicted → window is m5 .. m24, in order.
        assertEquals("m5", snapshot.get(0).text());
        assertEquals("m" + (total - 1), snapshot.get(snapshot.size() - 1).text());
    }

    @Test
    void snapshotIsImmutable() {
        ChatRingBuffer ring = new ChatRingBuffer();
        ring.add(msg("a"));
        List<ChatMessage> snapshot = ring.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(msg("b")));
    }

    @Test
    void concurrentAddsNeverExceedCapacityNorThrow() throws InterruptedException {
        ChatRingBuffer ring = new ChatRingBuffer();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 200; i++) {
                        ring.add(msg("x"));
                        ring.snapshot();
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertTrue(ring.snapshot().size() <= ChatRingBuffer.CAPACITY);
    }
}
