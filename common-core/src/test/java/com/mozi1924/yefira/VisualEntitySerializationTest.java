package com.mozi1924.yefira;

import com.mozi1924.yefira.encoder.BlockChangeEntry;
import com.mozi1924.yefira.encoder.EntityEntry;
import com.mozi1924.yefira.encoder.VisualEntityEntry;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class VisualEntitySerializationTest {

    @Test
    public void testEmptyListReturnsEmptyBytes() {
        byte[] empty = VisualEntityEntry.serializeList(List.of());
        Assertions.assertEquals(0, empty.length);

        byte[] nullList = VisualEntityEntry.serializeList(null);
        Assertions.assertEquals(0, nullList.length);
    }

    @Test
    public void testVisualEntityBinaryPackingForJni() {
        UUID u1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String payload = "{\"variant\":\"minecraft:kebab\"}";
        VisualEntityEntry e1 = new VisualEntityEntry(
                u1, "minecraft:painting",
                10.5, 64.5, 20.0,
                0.0f, 1.57f, 0.0f,
                1.0f, 1.0f, 1.0f,
                (byte) 2,
                payload.getBytes(StandardCharsets.UTF_8)
        );

        byte[] packed = VisualEntityEntry.serializeList(List.of(e1));
        Assertions.assertTrue(packed.length > 0);

        ByteBuffer buf = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
        long most = buf.getLong();
        long least = buf.getLong();
        Assertions.assertEquals(u1.getMostSignificantBits(), most);
        Assertions.assertEquals(u1.getLeastSignificantBits(), least);

        Assertions.assertEquals(10.5, buf.getDouble(), 1e-6);
        Assertions.assertEquals(64.5, buf.getDouble(), 1e-6);
        Assertions.assertEquals(20.0, buf.getDouble(), 1e-6);
        Assertions.assertEquals(0.0f, buf.getFloat(), 1e-6f);
        Assertions.assertEquals(1.57f, buf.getFloat(), 1e-6f);
        Assertions.assertEquals(0.0f, buf.getFloat(), 1e-6f);
        Assertions.assertEquals(1.0f, buf.getFloat(), 1e-6f);
        Assertions.assertEquals(1.0f, buf.getFloat(), 1e-6f);
        Assertions.assertEquals(1.0f, buf.getFloat(), 1e-6f);
        Assertions.assertEquals((byte) 2, buf.get());

        int idLen = buf.getShort() & 0xFFFF;
        byte[] idBytes = new byte[idLen];
        buf.get(idBytes);
        Assertions.assertEquals("minecraft:painting", new String(idBytes, StandardCharsets.UTF_8));

        int payloadLen = buf.getInt();
        byte[] payloadBytes = new byte[payloadLen];
        buf.get(payloadBytes);
        Assertions.assertEquals(payload, new String(payloadBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testEntityEntryRecord() {
        EntityEntry ee = new EntityEntry(1, 2, 3, "{\"test\":true}");
        Assertions.assertEquals(1, ee.relX());
        Assertions.assertEquals(2, ee.relY());
        Assertions.assertEquals(3, ee.relZ());
        Assertions.assertEquals("{\"test\":true}", ee.getJson());
    }

    @Test
    public void testBlockChangeEntryRecord() {
        BlockPos pos = new BlockPos(10, 20, 30);
        BlockChangeEntry change = new BlockChangeEntry(pos, null);
        Assertions.assertEquals(pos, change.pos());
        Assertions.assertNull(change.state());
    }
}
