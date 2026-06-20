package com.mcplatform.plugin.transport;

/**
 * Narrow JSON seam. JSON mapping lives entirely in the transport layer behind this interface, so
 * neither features nor the dependency-free {@code plugin-protocol} ever see a JSON library
 * (Prinzip 3). Swapping the engine (Gson today) touches only the implementation.
 */
public interface JsonCodec {

    /** Serialize a DTO (typically a {@code plugin-protocol} record) to JSON. */
    String toJson(Object value);

    /** Deserialize JSON into the given DTO type. */
    <T> T fromJson(String json, Class<T> type);
}
