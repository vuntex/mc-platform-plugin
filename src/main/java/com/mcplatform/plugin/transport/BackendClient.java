package com.mcplatform.plugin.transport;

import com.mcplatform.protocol.core.EndpointDescriptor;

import java.util.concurrent.CompletableFuture;

/**
 * Generic, feature-agnostic REST client: a call is described entirely by an
 * {@link EndpointDescriptor} from {@code plugin-protocol} (method, path template, request/response
 * types), so features never assemble URLs or pick verbs. Non-blocking — the returned future
 * completes off the main thread.
 *
 * <h2>Idempotency &amp; retries</h2>
 * Only idempotent calls are retried (and only on transient failures: timeout/connection or 5xx):
 * <ul>
 *   <li><b>GET</b> endpoints (e.g. {@code EconomyEndpoints.GET_BALANCE}) are always idempotent →
 *       use {@link #call}; they are retried automatically.</li>
 *   <li><b>Writes</b> ({@code CREDIT/DEBIT/SET/TRANSFER}, {@code UPSERT_PLAYER}, {@code JOIN}) are
 *       retried ONLY via {@link #callIdempotent}, which the caller uses to assert the request carries
 *       a stable {@code transactionId}/{@code correlationId} (the backend dedupes on it) or is a
 *       natural upsert. Via plain {@link #call} a write is sent exactly once (no retry).</li>
 * </ul>
 */
public interface BackendClient {

    /**
     * Call {@code endpoint}. Retried automatically only if it is a GET; any other method is sent
     * exactly once.
     *
     * @param body     request body to encode, or {@code null} for {@link Void} request endpoints
     * @param pathVars values for the {@code {...}} placeholders, in template order
     */
    <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> endpoint, REQ body, String... pathVars);

    /**
     * Call {@code endpoint} treating it as idempotent → retried on transient failures. Use ONLY when
     * the request carries a stable {@code transactionId}/{@code correlationId} or is a natural upsert,
     * so a retried duplicate is deduped by the backend.
     */
    <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> endpoint, REQ body, String... pathVars);
}
