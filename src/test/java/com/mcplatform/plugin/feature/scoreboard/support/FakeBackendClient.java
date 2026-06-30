package com.mcplatform.plugin.feature.scoreboard.support;

import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.core.EndpointDescriptor;

import java.util.concurrent.CompletableFuture;

/**
 * Minimal {@link BackendClient} double: returns a pre-set result (or a pre-set failure) for the next
 * call and counts calls. Each test sets the single response type it needs.
 */
public final class FakeBackendClient implements BackendClient {

    private Object result;
    private Throwable error;
    public int calls = 0;

    public FakeBackendClient result(Object result) {
        this.result = result;
        this.error = null;
        return this;
    }

    public FakeBackendClient error(Throwable error) {
        this.error = error;
        this.result = null;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> endpoint, REQ body, String... pathVars) {
        calls++;
        if (error != null) {
            return CompletableFuture.failedFuture(error);
        }
        return CompletableFuture.completedFuture((RES) result);
    }

    @Override
    public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> endpoint, REQ body, String... pathVars) {
        return call(endpoint, body, pathVars);
    }
}
