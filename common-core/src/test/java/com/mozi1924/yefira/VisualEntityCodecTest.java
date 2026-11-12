package com.mozi1924.yefira;

import com.mozi1924.yefira.compat.visual.VisualEntitySerializer;
import com.mozi1924.yefira.encoder.BlockDataEncoder;
import com.mozi1924.yefira.encoder.VisualEntityEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VisualEntityCodecTest {

    @Test
    public void testBaselineVisualEntitiesEncodeDecode() {
        UUID u1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID u2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        String p1 = VisualEntitySerializer.serializePainting("kebab", 1, 1, 10, 64, 20);
        String p2 = VisualEntitySerializer.serializeItemFrame("minecraft:diamond_sword", 1, 2, false, false, null);

        VisualEntityEntry e1 = new VisualEntityEntry(u1, "minecraft:painting", 10.5, 64.5, 20.0, 0.0f, 1.57f, 0.0f, 1.0f, 1.0f, 1.0f, (byte) 2, p1.getBytes(StandardCharsets.UTF_8));
        VisualEntityEntry e2 = new VisualEntityEntry(u2, "minecraft:item_frame", 15.0, 65.0, 25.5, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, (byte) 3, p2.getBytes(StandardCharsets.UTF_8));

        List<VisualEntityEntry> list = List.of(e1, e2);
        long hash = VisualEntityEntry.computeEntitiesHash(list);
        Assertions.assertNotEquals(0L, hash);

        byte[] encoded = BlockDataEncoder.encodeBaselineVisualEntities(42L, 100L, hash, list);
        Assertions.assertNotNull(encoded);

        BlockDataEncoder.BaselineVisualEntitiesPacket decoded = BlockDataEncoder.decodeBaselineVisualEntities(ByteBuffer.wrap(encoded));
        Assertions.assertNotNull(decoded);
        Assertions.assertEquals(42L, decoded.epoch);
        Assertions.assertEquals(100L, decoded.snapshotSeq);
        Assertions.assertEquals(hash, decoded.entitiesHash);
        Assertions.assertEquals(2, decoded.entities.size());

        VisualEntityEntry d1 = decoded.entities.get(0);
        Assertions.assertEquals(u1, d1.getUuid());
        Assertions.assertEquals("minecraft:painting", d1.getId());
        Assertions.assertEquals(10.5, d1.getX(), 1e-6);
        Assertions.assertEquals(64.5, d1.getY(), 1e-6);
        Assertions.assertEquals(20.0, d1.getZ(), 1e-6);
        Assertions.assertEquals(1.57f, d1.getYaw(), 1e-6f);
        Assertions.assertEquals((byte) 2, d1.getFacing());
        Assertions.assertEquals(p1, d1.getCustomPayloadString());

        VisualEntityEntry d2 = decoded.entities.get(1);
        Assertions.assertEquals(u2, d2.getUuid());
        Assertions.assertEquals("minecraft:item_frame", d2.getId());
        Assertions.assertEquals(p2, d2.getCustomPayloadString());
    }

    @Test
    public void testDeltaVisualEntityEncodeDecode() {
        UUID uuid = UUID.randomUUID();
        String payload = VisualEntitySerializer.serializePainting("alban", 1, 1, 1, 2, 3);
        VisualEntityEntry entry = new VisualEntityEntry(uuid, "minecraft:painting", 1.0, 2.0, 3.0, 0.1f, 0.2f, 0.0f, 1.0f, 1.0f, 1.0f, (byte) 4, payload.getBytes(StandardCharsets.UTF_8));

        // 1. UPSERT
        byte[] upsertBytes = BlockDataEncoder.encodeDeltaVisualEntity(5L, 501L, BlockDataEncoder.DELTA_VISUAL_ACTION_UPSERT, entry);
        BlockDataEncoder.DeltaVisualEntityPacket upsertPkt = BlockDataEncoder.decodeDeltaVisualEntity(ByteBuffer.wrap(upsertBytes));
        Assertions.assertNotNull(upsertPkt);
        Assertions.assertEquals(5L, upsertPkt.epoch);
        Assertions.assertEquals(501L, upsertPkt.seqId);
        Assertions.assertEquals(BlockDataEncoder.DELTA_VISUAL_ACTION_UPSERT, upsertPkt.action);
        Assertions.assertEquals(uuid, upsertPkt.entity.getUuid());
        Assertions.assertEquals("minecraft:painting", upsertPkt.entity.getId());
        Assertions.assertEquals(payload, upsertPkt.entity.getCustomPayloadString());

        // 2. REMOVE
        byte[] removeBytes = BlockDataEncoder.encodeDeltaVisualEntity(5L, 502L, BlockDataEncoder.DELTA_VISUAL_ACTION_REMOVE, uuid);
        Assertions.assertEquals(37, removeBytes.length); // 4 header + 8 epoch + 8 seq + 1 action + 16 uuid
        BlockDataEncoder.DeltaVisualEntityPacket removePkt = BlockDataEncoder.decodeDeltaVisualEntity(ByteBuffer.wrap(removeBytes));
        Assertions.assertNotNull(removePkt);
        Assertions.assertEquals(5L, removePkt.epoch);
        Assertions.assertEquals(502L, removePkt.seqId);
        Assertions.assertEquals(BlockDataEncoder.DELTA_VISUAL_ACTION_REMOVE, removePkt.action);
        Assertions.assertEquals(uuid, removePkt.uuid);
        Assertions.assertNull(removePkt.entity);
    }

    @Test
    public void testDeterministicHashSorting() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        VisualEntityEntry e1 = new VisualEntityEntry(u1, "minecraft:painting", 1.0, 2.0, 3.0, 0f, 0f, 0f, 1f, 1f, 1f, (byte) 2, new byte[0]);
        VisualEntityEntry e2 = new VisualEntityEntry(u2, "minecraft:item_frame", 4.0, 5.0, 6.0, 0f, 0f, 0f, 1f, 1f, 1f, (byte) 3, new byte[0]);

        long h1 = VisualEntityEntry.computeEntitiesHash(List.of(e1, e2));
        long h2 = VisualEntityEntry.computeEntitiesHash(List.of(e2, e1)); // reverse order

        Assertions.assertEquals(h1, h2, "Hash must be order-independent (deterministic sorting by UUID)");
    }

    @Test
    public void testDecodeHelloWithSubscribedVisualEntities() {
        ByteBuffer buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 3); // minVersion
        buf.putShort((short) 3); // maxVersion
        buf.putInt(BlockDataEncoder.CAP_SUPPORTS_VISUAL_ENTITIES); // capabilities
        buf.putInt(0); // flags
        buf.put(new byte[16]); // cachedWorldUuid
        buf.putLong(0L); // cachedEpoch
        buf.putLong(0L); // cachedSeq
        buf.putInt(0); buf.putInt(0); buf.putInt(0); // min
        buf.putInt(0); buf.putInt(0); buf.putInt(0); // size
        buf.putLong(0L); // cachedStateHash

        // authToken
        byte[] token = "secret".getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) token.length);
        buf.put(token);

        // cachedDimension
        byte[] dim = "minecraft:overworld".getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) dim.length);
        buf.put(dim);

        // Subscribed entities: count = 2
        buf.putShort((short) 2);
        byte[] s1 = "minecraft:painting".getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) s1.length);
        buf.put(s1);
        byte[] s2 = "minecraft:item_frame".getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) s2.length);
        buf.put(s2);

        buf.flip();
        BlockDataEncoder.HelloPacket hello = BlockDataEncoder.decodeHello(buf);
        Assertions.assertNotNull(hello);
        Assertions.assertEquals("secret", hello.authToken);
        Assertions.assertEquals("minecraft:overworld", hello.cachedDimension);
        Assertions.assertEquals(Set.of("minecraft:painting", "minecraft:item_frame"), hello.subscribedVisualEntities);
    }
}
