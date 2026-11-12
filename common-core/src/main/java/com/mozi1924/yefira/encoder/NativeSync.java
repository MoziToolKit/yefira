package com.mozi1924.yefira.encoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance JNI bridge for libmtk Native Live Sync (RFC v3.1 / v3.2 SSOT).
 */
public final class NativeSync {

    private static final Logger LOGGER = Logger.getLogger(NativeSync.class.getName());
    private static volatile boolean isLoaded = false;
    private static Throwable loadError = null;

    static {
        loadNativeLibrary();
    }

    public static boolean isAvailable() {
        return isLoaded;
    }

    public static Throwable getLoadError() {
        return loadError;
    }

    private static synchronized void loadNativeLibrary() {
        if (isLoaded) {
            return;
        }

        // 1. Explicit system property override
        String explicitPath = System.getProperty("mtk.jni.path");
        if (explicitPath != null && !explicitPath.trim().isEmpty()) {
            File f = new File(explicitPath);
            if (f.exists()) {
                try {
                    System.load(f.getAbsolutePath());
                    isLoaded = true;
                    LOGGER.info("[NativeSync] Successfully loaded mtk-jni from explicit property: " + f.getAbsolutePath());
                    return;
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "[NativeSync] Failed to load explicit path: " + explicitPath, t);
                }
            }
        }

        String osName = System.getProperty("os.name", "");
        String osArch = System.getProperty("os.arch", "");
        String platformDir = resolvePlatformDir(osName, osArch);
        String libName = resolveLibName(osName);

        // 2. Local development workspace fallback
        String[] devPaths = new String[]{
                "../libmozitoolkit/target/release/" + libName,
                "../../libmozitoolkit/target/release/" + libName,
                "/Volumes/Data/code_projects/mtk/libmozitoolkit/target/release/" + libName,
                "../libmozitoolkit/target/release/libmtk_jni.dylib",
                "/Volumes/Data/code_projects/mtk/libmozitoolkit/target/release/libmtk_jni.dylib"
        };
        for (String dp : devPaths) {
            File f = new File(dp);
            if (f.exists()) {
                try {
                    System.load(f.getAbsolutePath());
                    isLoaded = true;
                    LOGGER.info("[NativeSync] Successfully loaded mtk-jni from local dev path: " + f.getAbsolutePath());
                    return;
                } catch (Throwable ignored) {
                }
            }
        }

        // 3. Extract and load from bundled resources
        String resourcePath = "/natives/" + platformDir + "/" + libName;
        try (InputStream in = NativeSync.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                File tempDir = Files.createTempDirectory("mtk_jni_").toFile();
                tempDir.deleteOnExit();
                File tempFile = new File(tempDir, libName);
                tempFile.deleteOnExit();

                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                System.load(tempFile.getAbsolutePath());
                isLoaded = true;
                LOGGER.info("[NativeSync] Successfully loaded bundled native library from: " + resourcePath);
                return;
            }
        } catch (Throwable t) {
            loadError = t;
            LOGGER.log(Level.WARNING, "[NativeSync] Failed to extract bundled native library: " + resourcePath, t);
        }

        // 4. Fallback to standard java.library.path
        try {
            System.loadLibrary("mtk_jni");
            isLoaded = true;
            LOGGER.info("[NativeSync] Successfully loaded mtk_jni from java.library.path");
        } catch (Throwable t) {
            loadError = t;
            LOGGER.log(Level.SEVERE, "[NativeSync] Could not load mtk_jni native library for " + platformDir, t);
        }
    }

    public static String resolvePlatformDir(String osName, String osArch) {
        String os = osName != null ? osName.toLowerCase() : "";
        String arch = osArch != null ? osArch.toLowerCase() : "";
        boolean isArm = arch.contains("aarch64") || arch.contains("arm64");

        if (os.contains("win")) {
            return isArm ? "windows-arm64" : "windows-x64";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return isArm ? "macos-arm64" : "macos-x64";
        } else {
            return isArm ? "linux-arm64" : "linux-x64";
        }
    }

    public static String resolveLibName(String osName) {
        String os = osName != null ? osName.toLowerCase() : "";
        if (os.contains("win")) {
            return "mtk_jni.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "libmtk_jni.dylib";
        } else {
            return "libmtk_jni.so";
        }
    }

    /**
     * Authoritative 64-bit xxHash (xxHash64) over byte array.
     */
    public static native long nativeXxHash64(byte[] data, long seed);

    public static final int EVENT_CLIENT_CONNECTED = 1;
    public static final int EVENT_CLIENT_DISCONNECTED = 2;
    public static final int EVENT_BASELINE_REQUESTED = 3;
    public static final int EVENT_CLIENT_RESYNC = 4;

    public static class ServerEvent {
        public final int type;
        public final long clientId;
        public final long epoch;
        public final long sequence;
        public final String message;

        public ServerEvent(int type, long clientId, long epoch, long sequence, String message) {
            this.type = type;
            this.clientId = clientId;
            this.epoch = epoch;
            this.sequence = sequence;
            this.message = message;
        }

        @Override
        public String toString() {
            return "ServerEvent{" +
                    "type=" + type +
                    ", clientId=" + clientId +
                    ", epoch=" + epoch +
                    ", sequence=" + sequence +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

    public static native long nativeServerStart(String host, int port, String token, boolean allowLoopback);
    public static native void nativeServerStop(long handle);
    public static native boolean nativeServerIsRunning(long handle);
    public static native int nativeServerGetPort(long handle);
    public static native int nativeServerGetClientCount(long handle);
    public static native void nativeServerSetSelection(
            long handle,
            int minX, int minY, int minZ,
            int sizeX, int sizeY, int sizeZ,
            String dimension
    );
    public static native void nativeServerClearSelection(long handle);
    public static native boolean nativeServerSubmitBaseline(
            long handle,
            long clientId,
            int minX, int minY, int minZ,
            int sizeX, int sizeY, int sizeZ,
            String dimension,
            int[] rawStates,
            String[] statePalette,
            int[] rawBiomes,
            String[] biomePalette,
            int[] entityCoords,
            String[] entityPayloads,
            byte[] visualEntitiesBytes
    );
    public static native void nativeServerSubmitDeltas(
            long handle,
            int[] relCoords,
            String[] stateStrings,
            int[] entityCoords,
            String[] entityPayloads
    );
    public static native String[] nativeServerGetSubscribedVisualEntities(long handle);
    public static native void nativeServerSubmitVisualEntityUpsert(
            long handle,
            long mostSig,
            long leastSig,
            String id,
            double x, double y, double z,
            float pitch, float yaw, float roll,
            float scaleX, float scaleY, float scaleZ,
            byte facing,
            byte[] customPayload
    );
    public static native void nativeServerSubmitVisualEntityRemove(
            long handle,
            long mostSig,
            long leastSig
    );
    public static native void nativeServerBroadcastPacket(long handle, byte[] data);
    public static native ServerEvent[] nativeServerPollEvents(long handle);

    private NativeSync() {}
}
