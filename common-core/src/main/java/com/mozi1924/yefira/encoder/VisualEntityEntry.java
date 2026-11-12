package com.mozi1924.yefira.encoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure Data Transfer Object (DTO) for visual entities living in continuous 3D space.
 * Holds canonical position, rotation, scale, facing, and entity-specific visual payload.
 */
public class VisualEntityEntry {
    private final UUID uuid;
    private final String id;
    private final double x;
    private final double y;
    private final double z;
    private final float pitch;
    private final float yaw;
    private final float roll;
    private final float scaleX;
    private final float scaleY;
    private final float scaleZ;
    private final byte facing; // 0:Down, 1:Up, 2:North, 3:South, 4:West, 5:East, (byte) 255:None
    private final byte[] customPayload; // UTF-8 JSON payload bytes

    public VisualEntityEntry(
            UUID uuid,
            String id,
            double x, double y, double z,
            float pitch, float yaw, float roll,
            float scaleX, float scaleY, float scaleZ,
            byte facing,
            byte[] customPayload
    ) {
        this.uuid = uuid != null ? uuid : new UUID(0L, 0L);
        this.id = id != null ? id : "";
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        this.facing = facing;
        this.customPayload = customPayload != null ? customPayload : new byte[0];
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getPitch() {
        return pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getRoll() {
        return roll;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public float getScaleZ() {
        return scaleZ;
    }

    public byte getFacing() {
        return facing;
    }

    public byte[] getCustomPayload() {
        return customPayload;
    }

    public String getCustomPayloadString() {
        return new String(customPayload, StandardCharsets.UTF_8);
    }

    /**
     * Serializes a list of VisualEntityEntry objects into contiguous Little-Endian binary bytes
     * for JNI ingestion by mtk-sync.
     */
    public static byte[] serializeList(List<VisualEntityEntry> entities) {
        if (entities == null || entities.isEmpty()) {
            return new byte[0];
        }
        int totalLen = 0;
        for (VisualEntityEntry e : entities) {
            byte[] idBytes = e.getId().getBytes(StandardCharsets.UTF_8);
            byte[] payload = e.getCustomPayload();
            totalLen += 16 + 24 + 12 + 12 + 1 + 2 + idBytes.length + 4 + (payload != null ? payload.length : 0);
        }
        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN);
        for (VisualEntityEntry e : entities) {
            writeToBuffer(buf, e);
        }
        return buf.array();
    }

    private static void writeToBuffer(ByteBuffer buf, VisualEntityEntry e) {
        UUID u = e != null ? e.getUuid() : new UUID(0L, 0L);
        buf.putLong(u.getMostSignificantBits());
        buf.putLong(u.getLeastSignificantBits());
        if (e != null) {
            buf.putDouble(e.getX());
            buf.putDouble(e.getY());
            buf.putDouble(e.getZ());
            buf.putFloat(e.getPitch());
            buf.putFloat(e.getYaw());
            buf.putFloat(e.getRoll());
            buf.putFloat(e.getScaleX());
            buf.putFloat(e.getScaleY());
            buf.putFloat(e.getScaleZ());
            buf.put(e.getFacing());
            byte[] idBytes = e.getId().getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) idBytes.length);
            buf.put(idBytes);
            byte[] payload = e.getCustomPayload();
            buf.putInt(payload.length);
            if (payload.length > 0) {
                buf.put(payload);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VisualEntityEntry that = (VisualEntityEntry) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
