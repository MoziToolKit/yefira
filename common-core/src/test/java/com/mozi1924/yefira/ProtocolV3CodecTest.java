package com.mozi1924.yefira;

import com.mozi1924.yefira.encoder.BlockDataEncoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ProtocolV3CodecTest {

    @Test
    public void testHelloDecode() {
        ByteBuffer buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(BlockDataEncoder.MAGIC);
        buf.put(BlockDataEncoder.PROTOCOL_VERSION);
        buf.put(BlockDataEncoder.OPCODE_HELLO);

        buf.putShort((short) 3); // minVer
        buf.putShort((short) 3); // maxVer
        buf.putInt(0x03); // capabilities
        buf.putInt(0); // flags
        buf.put(new byte[16]); // cachedWorldUuid
        buf.putLong(1L); // cachedEpoch
        buf.putLong(20L); // cachedSeq
        buf.putInt(10); // minX
        buf.putInt(20); // minY
        buf.putInt(30); // minZ
        buf.putInt(40); // sizeX
        buf.putInt(50); // sizeY
        buf.putInt(60); // sizeZ
        buf.putLong(0x12345678L); // cachedStateHash
        // authToken string (u16 len + UTF8)
        buf.putShort((short) 0);
        // dimension string (u16 len + UTF8)
        byte[] dim = "minecraft:the_nether".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.putShort((short) dim.length);
        buf.put(dim);

        buf.position(4); // Advance past header
        BlockDataEncoder.HelloPacket hello = BlockDataEncoder.decodeHello(buf);
        Assertions.assertNotNull(hello);
        Assertions.assertEquals(3, hello.minVersion);
        Assertions.assertEquals(3, hello.maxVersion);
        Assertions.assertEquals(1L, hello.cachedEpoch);
        Assertions.assertEquals(20L, hello.cachedSeq);
        Assertions.assertEquals(10, hello.cachedMinX);
        Assertions.assertEquals(20, hello.cachedMinY);
        Assertions.assertEquals(30, hello.cachedMinZ);
        Assertions.assertEquals(40, hello.cachedSizeX);
        Assertions.assertEquals(50, hello.cachedSizeY);
        Assertions.assertEquals(60, hello.cachedSizeZ);
        Assertions.assertEquals(0x12345678L, hello.cachedStateHash);
        Assertions.assertEquals("minecraft:the_nether", hello.cachedDimension);
    }
}
