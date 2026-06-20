package com.mcplatform.plugin.transport;

import com.google.gson.Gson;

/**
 * Gson-backed {@link JsonCodec}. Gson is bundled (and relocated) into the plugin jar; it is confined
 * to this class. Gson 2.10+ maps Java records via their canonical constructor and component names,
 * which match the {@code plugin-protocol} wire field names one-to-one (UUIDs via Gson's built-in
 * adapter), so the protocol DTOs (de)serialize without any annotations.
 */
public final class GsonJsonCodec implements JsonCodec {

    private final Gson gson;

    public GsonJsonCodec() {
        this(new Gson());
    }

    public GsonJsonCodec(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String toJson(Object value) {
        return gson.toJson(value);
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        return gson.fromJson(json, type);
    }
}
