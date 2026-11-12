package com.mozi1924.yefira.encoder;

import com.mozi1924.yefira.compat.VersionCompat;
import com.mozi1924.yefira.selection.SelectionBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BlockDataEncoder {

    public static final byte[] MAGIC = new byte[]{(byte) 0x4D, (byte) 0x54}; // 'M', 'T'
    public static final byte PROTOCOL_VERSION = 0x03;

    // RFC v3.1 Opcodes
    public static final byte OPCODE_HELLO = 0x01;
    public static final byte OPCODE_WELCOME = 0x02;
    public static final byte OPCODE_BASELINE_BEGIN = 0x03;
    public static final byte OPCODE_BASELINE_CHUNK = 0x04;
    public static final byte OPCODE_BASELINE_END = 0x05;
    public static final byte OPCODE_BASELINE_ACK = 0x06;
    public static final byte OPCODE_DELTA_OP = 0x07;
    public static final byte OPCODE_PALETTE_UPDATE = 0x08;
    public static final byte OPCODE_CANCEL = 0x0B;
    public static final byte OPCODE_RESYNC_REQUEST = 0x0D;
    public static final byte OPCODE_PING = 0x0E;
    public static final byte OPCODE_PONG = 0x0F;
    public static final byte OPCODE_ERROR = 0x10;
    // RFC v3.2 Sparse BlockEntity Opcodes
    public static final byte OPCODE_BASELINE_ENTITIES = 0x11;
    public static final byte OPCODE_DELTA_ENTITY = 0x12;
    // RFC v3.2 Continuous Visual Entity Opcodes
    public static final byte OPCODE_BASELINE_VISUAL_ENTITIES = 0x13;
    public static final byte OPCODE_DELTA_VISUAL_ENTITY = 0x14;

    // RFC v3.2 Client Capability Flags
    public static final int CAP_SUPPORTS_VISUAL_ENTITIES = 0x00000004;

    // RFC v3.2 Delta Visual Entity Action Types
    public static final byte DELTA_VISUAL_ACTION_UPSERT = 0x01;
    public static final byte DELTA_VISUAL_ACTION_REMOVE = 0x02;

    // RFC v3.1 C2S Control Opcodes
    public static final byte OPCODE_SYNC_CONFIG = (byte) 0x82;

    /** RFC v3.1 hard selection volume limit (voxels). */
    public static final long MAX_SELECTION_VOLUME = 33_554_432L;

    /** RFC v3.1 diagnostic error code: UnsupportedVersion. */
    public static final short ERROR_UNSUPPORTED_VERSION = 0x0001;

    /** RFC v3.1 diagnostic error code: InvalidBounds. */
    public static final short ERROR_INVALID_BOUNDS = 0x0002;

    /** RFC v3.1 diagnostic error code: UnknownOpcode. */
    public static final short ERROR_UNKNOWN_OPCODE = 0x0007;

    /** RFC v3.1 diagnostic error code: ResourceLimitExceeded. */
    public static final short ERROR_RESOURCE_LIMIT_EXCEEDED = 0x0005;

    // RFC v3.1 SyncDecision Constants (RFC Section 4.2)
    public static final byte DECISION_VERIFIED_SKIP_REBUILD = 0x00;
    public static final byte DECISION_FULL_BASELINE_REQUIRED = 0x02;
    public static final byte DECISION_REJECTED = (byte) 0xFF;

    // RFC v3.1 Data Structures
    public static class ResyncRequestPacket {
        public long epoch;
        public long missingFromSeq;
        public long missingToSeq;
        public byte reason;
    }

    public static class HelloPacket {
        public short minVersion;
        public short maxVersion;
        public int capabilities;
        public int flags;
        public byte[] cachedWorldUuid;
        public long cachedEpoch;
        public long cachedSeq;
        public int cachedMinX, cachedMinY, cachedMinZ;
        public int cachedSizeX, cachedSizeY, cachedSizeZ;
        public long cachedStateHash;
        public String authToken;
        public String cachedDimension;
        public Set<String> subscribedVisualEntities = Collections.emptySet();
    }

    public static class DeltaEntry {
        public final int relX, relY, relZ;
        public final int stateId;
        public DeltaEntry(int relX, int relY, int relZ, int stateId) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.stateId = stateId;
        }
    }

    public static class EntityEntry {
        public final int relX, relY, relZ;
        public final byte[] payload;

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
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    public static HelloPacket decodeHello(ByteBuffer buf) {
        if (buf.remaining() < 2 + 2 + 4 + 4 + 16 + 8 + 8 + 4 * 3 + 4 * 3 + 8 + 2) {
            return null;
        }
        buf.order(ByteOrder.LITTLE_ENDIAN);
        HelloPacket p = new HelloPacket();
        p.minVersion = buf.getShort();
        p.maxVersion = buf.getShort();
        p.capabilities = buf.getInt();
        p.flags = buf.getInt();
        p.cachedWorldUuid = new byte[16];
        buf.get(p.cachedWorldUuid);
        p.cachedEpoch = buf.getLong();
        p.cachedSeq = buf.getLong();
        p.cachedMinX = buf.getInt();
        p.cachedMinY = buf.getInt();
        p.cachedMinZ = buf.getInt();
        p.cachedSizeX = buf.getInt();
        p.cachedSizeY = buf.getInt();
        p.cachedSizeZ = buf.getInt();
        p.cachedStateHash = buf.getLong();
        p.authToken = readString(buf);
        p.cachedDimension = readString(buf);
        if ((p.capabilities & CAP_SUPPORTS_VISUAL_ENTITIES) != 0 && buf.remaining() >= 2) {
            int subCount = buf.getShort() & 0xFFFF;
            Set<String> subscribed = new HashSet<>(subCount);
            for (int i = 0; i < subCount; i++) {
                String sub = readString(buf);
                if (sub != null && !sub.isEmpty()) {
                    subscribed.add(sub);
                }
            }
            p.subscribedVisualEntities = Collections.unmodifiableSet(subscribed);
        }
        return p;
    }

    public static ResyncRequestPacket decodeResyncRequest(ByteBuffer buf) {
        if (buf == null || buf.remaining() < 25) {
            return null;
        }
        ResyncRequestPacket p = new ResyncRequestPacket();
        p.epoch = buf.getLong();
        p.missingFromSeq = buf.getLong();
        p.missingToSeq = buf.getLong();
        p.reason = buf.get();
        return p;
    }

    public static class BaselineVisualEntitiesPacket {
        public long epoch;
        public long snapshotSeq;
        public long entitiesHash;
        public List<VisualEntityEntry> entities;
    }

    public static class DeltaVisualEntityPacket {
        public long epoch;
        public long seqId;
        public byte action;
        public UUID uuid;
        public VisualEntityEntry entity;
    }

    public static void writeVisualEntity(ByteBuffer buf, VisualEntityEntry e) {
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

    public static byte[] serializeVisualEntitiesList(List<VisualEntityEntry> entities) {
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
            writeVisualEntity(buf, e);
        }
        return buf.array();
    }

    public static VisualEntityEntry readVisualEntity(ByteBuffer buf) {
        long most = buf.getLong();
        long least = buf.getLong();
        UUID uuid = new UUID(most, least);
        double x = buf.getDouble();
        double y = buf.getDouble();
        double z = buf.getDouble();
        float pitch = buf.getFloat();
        float yaw = buf.getFloat();
        float roll = buf.getFloat();
        float sx = buf.getFloat();
        float sy = buf.getFloat();
        float sz = buf.getFloat();
        byte facing = buf.get();
        int idLen = buf.getShort() & 0xFFFF;
        byte[] idBytes = new byte[idLen];
        buf.get(idBytes);
        String id = new String(idBytes, StandardCharsets.UTF_8);
        int payloadLen = buf.getInt();
        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            buf.get(payload);
        }
        return new VisualEntityEntry(uuid, id, x, y, z, pitch, yaw, roll, sx, sy, sz, facing, payload);
    }

    public static byte[] encodeBaselineVisualEntities(
            long epoch,
            long snapshotSeq,
            long entitiesHash,
            List<VisualEntityEntry> entities
    ) {
        int totalLen = 4 + 8 + 8 + 8 + 4; // header(4) + epoch(8) + seq(8) + hash(8) + count(4)
        if (entities != null) {
            for (VisualEntityEntry e : entities) {
                byte[] idBytes = e.getId().getBytes(StandardCharsets.UTF_8);
                byte[] payload = e.getCustomPayload();
                totalLen += 16 + 24 + 12 + 12 + 1 + 2 + idBytes.length + 4 + payload.length;
            }
        }
        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(MAGIC);
        buf.put(PROTOCOL_VERSION);
        buf.put(OPCODE_BASELINE_VISUAL_ENTITIES);
        buf.putLong(epoch);
        buf.putLong(snapshotSeq);
        buf.putLong(entitiesHash);
        int count = entities != null ? entities.size() : 0;
        buf.putInt(count);
        if (entities != null) {
            for (VisualEntityEntry e : entities) {
                writeVisualEntity(buf, e);
            }
        }
        return buf.array();
    }

    public static BaselineVisualEntitiesPacket decodeBaselineVisualEntities(ByteBuffer buf) {
        if (buf == null || buf.remaining() < 4 + 8 + 8 + 8 + 4) {
            return null;
        }
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // Header
        byte[] magic = new byte[2];
        buf.get(magic);
        byte ver = buf.get();
        byte op = buf.get();
        if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1] || op != OPCODE_BASELINE_VISUAL_ENTITIES) {
            return null;
        }
        BaselineVisualEntitiesPacket pkt = new BaselineVisualEntitiesPacket();
        pkt.epoch = buf.getLong();
        pkt.snapshotSeq = buf.getLong();
        pkt.entitiesHash = buf.getLong();
        int count = buf.getInt();
        pkt.entities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pkt.entities.add(readVisualEntity(buf));
        }
        return pkt;
    }

    public static byte[] encodeDeltaVisualEntity(
            long epoch,
            long seqId,
            byte action,
            UUID uuid
    ) {
        ByteBuffer buf = ByteBuffer.allocate(4 + 8 + 8 + 1 + 16).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(MAGIC);
        buf.put(PROTOCOL_VERSION);
        buf.put(OPCODE_DELTA_VISUAL_ENTITY);
        buf.putLong(epoch);
        buf.putLong(seqId);
        buf.put(action);
        UUID u = uuid != null ? uuid : new UUID(0L, 0L);
        buf.putLong(u.getMostSignificantBits());
        buf.putLong(u.getLeastSignificantBits());
        return buf.array();
    }

    public static byte[] encodeDeltaVisualEntity(
            long epoch,
            long seqId,
            byte action,
            VisualEntityEntry entity
    ) {
        if (action == DELTA_VISUAL_ACTION_REMOVE) {
            return encodeDeltaVisualEntity(epoch, seqId, action, entity != null ? entity.getUuid() : null);
        } else {
            byte[] idBytes = entity != null ? entity.getId().getBytes(StandardCharsets.UTF_8) : new byte[0];
            byte[] payload = entity != null ? entity.getCustomPayload() : new byte[0];
            int totalLen = 4 + 8 + 8 + 1 + 16 + 24 + 12 + 12 + 1 + 2 + idBytes.length + 4 + payload.length;
            ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(MAGIC);
            buf.put(PROTOCOL_VERSION);
            buf.put(OPCODE_DELTA_VISUAL_ENTITY);
            buf.putLong(epoch);
            buf.putLong(seqId);
            buf.put(action);
            writeVisualEntity(buf, entity);
            return buf.array();
        }
    }

    public static DeltaVisualEntityPacket decodeDeltaVisualEntity(ByteBuffer buf) {
        if (buf == null || buf.remaining() < 4 + 8 + 8 + 1 + 16) {
            return null;
        }
        buf.order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[2];
        buf.get(magic);
        byte ver = buf.get();
        byte op = buf.get();
        if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1] || op != OPCODE_DELTA_VISUAL_ENTITY) {
            return null;
        }
        DeltaVisualEntityPacket pkt = new DeltaVisualEntityPacket();
        pkt.epoch = buf.getLong();
        pkt.seqId = buf.getLong();
        pkt.action = buf.get();
        if (pkt.action == DELTA_VISUAL_ACTION_REMOVE) {
            long most = buf.getLong();
            long least = buf.getLong();
            pkt.uuid = new UUID(most, least);
            pkt.entity = null;
        } else {
            pkt.entity = readVisualEntity(buf);
            pkt.uuid = pkt.entity != null ? pkt.entity.getUuid() : null;
        }
        return pkt;
    }

    private static String readString(ByteBuffer buf) {
        if (buf.remaining() < 2) return "";
        int len = buf.getShort() & 0xFFFF;
        if (buf.remaining() < len) return "";
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }


    private static final Map<BlockState, byte[]> STATE_UTF8_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 判断方块是否为空气或应被规范化为空气的无网格方块（如结构空位等）
     */
    public static boolean isAirOrIgnored(BlockState state) {
        if (state == null || state.isAir()) {
            return true;
        }
        return state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID);
    }

    /**
     * 将 BlockState 序列化为规范字符串标识，例如 "minecraft:oak_log[axis=y,facing=north]"
     */
    public static String serializeBlockState(BlockState state) {
        if (isAirOrIgnored(state)) {
            return "minecraft:air";
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Collection<Property<?>> properties = state.getProperties();
        if (properties.isEmpty()) {
            return blockId;
        }

        List<Property<?>> sorted = new ArrayList<>(properties);
        sorted.sort(Comparator.comparing(Property::getName));

        StringBuilder sb = new StringBuilder(blockId).append("[");
        boolean first = true;
        for (Property<?> property : sorted) {
            if (!first) {
                sb.append(",");
            }
            sb.append(property.getName()).append("=").append(getPropertyValueName(state, property));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueName(BlockState state, Property<T> property) {
        T val = state.getValue(property);
        return property.getName(val);
    }

    public static class BlockChangeEntry {
        private final BlockPos pos;
        private final BlockState state;

        public BlockChangeEntry(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }

        public BlockPos pos() {
            return pos;
        }

        public BlockState state() {
            return state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockChangeEntry that = (BlockChangeEntry) o;
            return Objects.equals(pos, that.pos) && Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos, state);
        }

        @Override
        public String toString() {
            return "BlockChangeEntry[" + "pos=" + pos + ", " + "state=" + state + ']';
        }
    }
}

