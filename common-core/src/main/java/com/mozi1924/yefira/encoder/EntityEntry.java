package com.mozi1924.yefira.encoder;

import java.nio.charset.StandardCharsets;

/**
 * Immutable record capturing relative coordinates and custom JSON payload of a BlockEntity.
 */
public record EntityEntry(int relX, int relY, int relZ, byte[] payload) {

    public EntityEntry(int relX, int relY, int relZ, byte[] payload) {
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.payload = payload != null ? payload : new byte[0];
    }

    public EntityEntry(int relX, int relY, int relZ, String json) {
        this(relX, relY, relZ, json != null ? json.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    public String getJson() {
        return payload != null ? new String(payload, StandardCharsets.UTF_8) : "";
    }
}
