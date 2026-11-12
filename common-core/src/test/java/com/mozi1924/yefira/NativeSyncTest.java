package com.mozi1924.yefira;

import com.mozi1924.yefira.encoder.NativeSync;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NativeSyncTest {

    @Test
    public void testPlatformDirResolution() {
        // Windows
        assertEquals("windows-x64", NativeSync.resolvePlatformDir("Windows 11", "amd64"));
        assertEquals("windows-x64", NativeSync.resolvePlatformDir("Windows 10", "x86_64"));
        assertEquals("windows-arm64", NativeSync.resolvePlatformDir("Windows 11", "aarch64"));
        assertEquals("windows-arm64", NativeSync.resolvePlatformDir("Windows 11", "arm64"));
        assertEquals("mtk_jni.dll", NativeSync.resolveLibName("Windows 11"));

        // macOS
        assertEquals("macos-arm64", NativeSync.resolvePlatformDir("Mac OS X", "aarch64"));
        assertEquals("macos-arm64", NativeSync.resolvePlatformDir("macOS", "arm64"));
        assertEquals("macos-x64", NativeSync.resolvePlatformDir("Mac OS X", "x86_64"));
        assertEquals("libmtk_jni.dylib", NativeSync.resolveLibName("Mac OS X"));

        // Linux
        assertEquals("linux-arm64", NativeSync.resolvePlatformDir("Linux", "aarch64"));
        assertEquals("linux-x64", NativeSync.resolvePlatformDir("Linux", "amd64"));
        assertEquals("libmtk_jni.so", NativeSync.resolveLibName("Linux"));
    }

    @Test
    public void testNativeLibraryLoaded() {
        assertTrue(NativeSync.isAvailable(), "NativeSync library must be available and loaded: " + NativeSync.getLoadError());
    }

    @Test
    public void testNativeServerSubmitBaselineAndDeltas() {
        assertTrue(NativeSync.isAvailable());

        long handle = NativeSync.nativeServerStart("127.0.0.1", 0, null, true);
        assertNotEquals(0L, handle, "Native server must start and return non-zero handle");

        try {
            int[] states = new int[]{0, 1, 0, 2}; // 4 blocks
            String[] statePalette = new String[]{"minecraft:air", "minecraft:stone", "minecraft:dirt"};
            int[] biomes = new int[]{0};
            String[] biomePalette = new String[]{"minecraft:plains"};

            // 1. Submit baseline with empty visual entities
            boolean success = NativeSync.nativeServerSubmitBaseline(
                    handle,
                    0L,
                    0, 64, 0,
                    2, 1, 2, // 2x1x2
                    "minecraft:overworld",
                    states, statePalette,
                    biomes, biomePalette,
                    new int[0], new String[0],
                    new byte[0]
            );
            assertTrue(success, "Baseline submission must succeed");

            // 2. Submit visual entity UPSERT & REMOVE
            NativeSync.nativeServerSubmitVisualEntityUpsert(
                    handle,
                    0x1234567890abcdefL,
                    0xfedcba0987654321L,
                    "minecraft:painting",
                    10.5, 65.0, 20.5,
                    0.0f, 0.0f, 0.0f,
                    1.0f, 1.0f, 1.0f,
                    (byte) 2, // North
                    "{\"variant\":\"minecraft:kebab\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            NativeSync.nativeServerSubmitVisualEntityRemove(
                    handle,
                    0x1234567890abcdefL,
                    0xfedcba0987654321L
            );

            // 3. Query subscribed visual entities (initially empty)
            String[] subs = NativeSync.nativeServerGetSubscribedVisualEntities(handle);
            assertNotNull(subs);

            // 4. Submit deltas with dynamic palette additions
            int[] relCoords = new int[]{0, 0, 0, 1, 0, 0};
            String[] stateStrings = new String[]{"minecraft:gold_block", "minecraft:diamond_block"};
            NativeSync.nativeServerSubmitDeltas(
                    handle,
                    relCoords,
                    stateStrings,
                    new int[0],
                    new String[0]
            );

            // 5. Clear selection
            NativeSync.nativeServerClearSelection(handle);
        } finally {
            NativeSync.nativeServerStop(handle);
        }
    }

    @Test
    public void testNativeXxHash64() {
        assertTrue(NativeSync.isAvailable());

        long emptyDigest = NativeSync.nativeXxHash64(new byte[0], 0L);
        assertEquals(0xef46db3751d8e999L, emptyDigest);

        byte[] data = "Hello Antigravity Live Sync RFC v3.1 World!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long digest = NativeSync.nativeXxHash64(data, 0L);
        assertNotEquals(0L, digest);
    }

    @Test
    public void testNativeServerLifecycle() {
        assertTrue(NativeSync.isAvailable());

        long handle = NativeSync.nativeServerStart("127.0.0.1", 0, null, true);
        assertNotEquals(0L, handle, "Native server must start and return non-zero handle");

        assertTrue(NativeSync.nativeServerIsRunning(handle));
        int port = NativeSync.nativeServerGetPort(handle);
        assertTrue(port > 0, "Server must have a valid dynamic port > 0, got: " + port);

        NativeSync.nativeServerSetSelection(handle, 0, 64, 0, 16, 16, 16, "minecraft:overworld");

        NativeSync.ServerEvent[] events = NativeSync.nativeServerPollEvents(handle);
        assertNotNull(events);

        NativeSync.nativeServerClearSelection(handle);
        NativeSync.nativeServerStop(handle);
    }
}
