package com.mozi1924.yefira.encoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
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
     * Computes a deterministic 64-bit xxHash (xxHash64) over a list of visual entities.
     * Guarantees identical output across Java, Rust, and Python by:
     * 1. Sorting entities strictly by canonical UUID string comparison.
     * 2. Hashing primitive raw binary bits (IEEE 754) in Little-Endian byte order without lossy string conversion.
     */
    public static long computeEntitiesHash(List<VisualEntityEntry> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0L;
        }

        List<VisualEntityEntry> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparing(e -> e.getUuid().toString()));

        int totalLen = 0;
        for (VisualEntityEntry e : sorted) {
            byte[] idBytes = e.getId().getBytes(StandardCharsets.UTF_8);
            byte[] payload = e.getCustomPayload();
            totalLen += 71 + idBytes.length + (payload != null ? payload.length : 0);
        }

        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN);
        for (VisualEntityEntry e : sorted) {
            // 1. UUID: most_sig LE, least_sig LE (16 bytes)
            buf.putLong(e.getUuid().getMostSignificantBits());
            buf.putLong(e.getUuid().getLeastSignificantBits());

            // 2. Entity Type ID (uint16 length + UTF-8 bytes)
            byte[] idBytes = e.getId().getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) idBytes.length);
            buf.put(idBytes);

            // 3. Position (3 x 8 bytes IEEE 754 double LE)
            buf.putLong(Double.doubleToRawLongBits(e.getX()));
            buf.putLong(Double.doubleToRawLongBits(e.getY()));
            buf.putLong(Double.doubleToRawLongBits(e.getZ()));

            // 4. Rotation (3 x 4 bytes IEEE 754 float LE)
            buf.putInt(Float.floatToRawIntBits(e.getPitch()));
            buf.putInt(Float.floatToRawIntBits(e.getYaw()));
            buf.putInt(Float.floatToRawIntBits(e.getRoll()));

            // 5. Scale (3 x 4 bytes IEEE 754 float LE)
            buf.putInt(Float.floatToRawIntBits(e.getScaleX()));
            buf.putInt(Float.floatToRawIntBits(e.getScaleY()));
            buf.putInt(Float.floatToRawIntBits(e.getScaleZ()));

            // 6. Facing (1 byte)
            buf.put(e.getFacing());

            // 7. Custom Payload (uint32 length + UTF-8 JSON bytes)
            byte[] payload = e.getCustomPayload();
            if (payload != null && payload.length > 0) {
                buf.putInt(payload.length);
                buf.put(payload);
            } else {
                buf.putInt(0);
            }
        }
        return NativeSync.nativeXxHash64(buf.array(), 0L);
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
