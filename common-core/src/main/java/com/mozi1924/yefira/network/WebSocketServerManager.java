package com.mozi1924.yefira.network;

import com.mozi1924.yefira.Yefira;
import com.mozi1924.yefira.compat.ClientThread;
import com.mozi1924.yefira.compat.ServerThread;
import com.mozi1924.yefira.compat.VersionCompat;
import com.mozi1924.yefira.config.YefiraConfig;
import com.mozi1924.yefira.encoder.BlockChangeEntry;
import com.mozi1924.yefira.encoder.CanonicalVoxelScanner;
import com.mozi1924.yefira.encoder.EntityEntry;
import com.mozi1924.yefira.encoder.NativeSync;
import com.mozi1924.yefira.encoder.VisualEntityEntry;
import com.mozi1924.yefira.encoder.VisualEntityScanner;
import com.mozi1924.yefira.selection.SelectionBox;
import com.mozi1924.yefira.selection.SelectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Slimmed-down WebSocket Server Manager backed by high-performance native Rust core (mtk-sync and mtk-jni).
 * <p>
 * Handles Minecraft selection lifecycle, block change delta queuing, and voxel scanning,
 * while delegating all WebSocket network I/O, RFC v3.1 framing, dynamic palettes,
 * chunking, sequence numbers, and client sessions to native Rust.
 */
public class WebSocketServerManager implements SelectionManager.SelectionChangeListener {

    private static final WebSocketServerManager INSTANCE = new WebSocketServerManager();
    private static String HOST = "127.0.0.1";
    private static int PORT = 8765;

    private static final int MAX_PENDING_DELTA_CHANGES = 262_144;
    private static final long CLIENT_SCAN_TIMEOUT_MILLIS = 30_000L;
    private static final long BASELINE_SCAN_TIMEOUT_SECONDS =
            TimeUnit.MILLISECONDS.toSeconds(CanonicalVoxelScanner.DEFAULT_MAX_SCAN_MILLIS) + 10L;

    private volatile long serverHandle = 0;
    private volatile Thread eventPollerThread;
    private volatile ExecutorService streamingExecutor = createStreamingExecutor();

    private final Map<BlockPos, BlockChangeEntry> pendingDeltaChanges = new LinkedHashMap<>();
    private SelectionBox pendingDeltaSelection;
    private final Set<BlockPos> trackedEntityPositions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, VisualEntityEntry> trackedVisualEntities = new ConcurrentHashMap<>();

    public static WebSocketServerManager getInstance() {
        return INSTANCE;
    }

    public static synchronized void setHost(String host) {
        if (host != null && !host.trim().isEmpty()) {
            HOST = host.trim();
        }
    }

    public static synchronized String getHost() {
        return HOST;
    }

    public static synchronized void setPort(int port) {
        if (port >= 1024 && port <= 65535) {
            PORT = port;
        }
    }

    public static synchronized int getPort() {
        if (INSTANCE.serverHandle != 0) {
            int nativePort = NativeSync.nativeServerGetPort(INSTANCE.serverHandle);
            if (nativePort > 0) {
                return nativePort;
            }
        }
        return PORT;
    }

    public synchronized boolean isRunning() {
        return serverHandle != 0 && NativeSync.nativeServerIsRunning(serverHandle);
    }

    public synchronized int getConnectedCount() {
        return serverHandle != 0 ? NativeSync.nativeServerGetClientCount(serverHandle) : 0;
    }

    private WebSocketServerManager() {}

    private static ExecutorService createStreamingExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Yefira-NativeSync-Stream");
            t.setDaemon(true);
            return t;
        });
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(host.trim()).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean startServer() {
        return startServer(HOST, PORT);
    }

    public synchronized boolean startServer(String host, int port) {
        if (isRunning()) {
            Yefira.LOGGER.info("WebSocket Server is already running on {}:{}", HOST, PORT);
            return true;
        }
        setHost(host);
        setPort(port);

        YefiraConfig cfg = YefiraConfig.getInstance();
        String token = cfg.getAuthToken();
        boolean allowLoopback = cfg.isAllowLoopbackWithoutToken();

        if (!isLoopbackHost(HOST) && (token == null || token.isBlank())) {
            Yefira.LOGGER.error(
                    "Refusing to bind WebSocket Server to non-loopback host {} without authToken. "
                            + "Set /yefira config token <secret> or bind 127.0.0.1.",
                    HOST
            );
            return false;
        }

        try {
            serverHandle = NativeSync.nativeServerStart(HOST, PORT, token, allowLoopback);
            if (serverHandle == 0) {
                Yefira.LOGGER.error("Native WebSocket Server failed to bind {}:{}", HOST, PORT);
                return false;
            }

            eventPollerThread = new Thread(this::pollNativeEventsLoop, "Yefira-NativeServer-Events");
            eventPollerThread.setDaemon(true);
            eventPollerThread.start();

            SelectionManager.getInstance().addListener(this);
            SelectionManager sm = SelectionManager.getInstance();
            if (sm.hasSelection() && sm.getCurrentLevel() != null) {
                onSelectionChanged(sm.getCurrentLevel(), sm.getCurrentSelection());
            }

            Yefira.LOGGER.info("Native WebSocket Server started on {}:{}", HOST, getPort());
            return true;
        } catch (Exception e) {
            Yefira.LOGGER.error("Failed to start native WebSocket Server on " + HOST + ":" + PORT, e);
            stopServer();
            return false;
        }
    }

    public synchronized boolean restartServer(String host, int port) {
        stopServer();
        return startServer(host, port);
    }

    public synchronized boolean restartServer() {
        stopServer();
        return startServer(HOST, PORT);
    }

    public synchronized void stopServer() {
        SelectionManager.getInstance().removeListener(this);
        clearPendingDeltaChanges();

        if (eventPollerThread != null) {
            eventPollerThread.interrupt();
            eventPollerThread = null;
        }

        if (streamingExecutor != null) {
            streamingExecutor.shutdownNow();
            streamingExecutor = createStreamingExecutor();
        }

        if (serverHandle != 0) {
            try {
                NativeSync.nativeServerStop(serverHandle);
                Yefira.LOGGER.info("Native WebSocket Server stopped.");
            } catch (Exception e) {
                Yefira.LOGGER.error("Error stopping native WebSocket Server", e);
            } finally {
                serverHandle = 0;
            }
        }
    }

    private void pollNativeEventsLoop() {
        while (isRunning() && !Thread.currentThread().isInterrupted()) {
            try {
                NativeSync.ServerEvent[] events = NativeSync.nativeServerPollEvents(serverHandle);
                if (events != null && events.length > 0) {
                    for (NativeSync.ServerEvent ev : events) {
                        handleServerEvent(ev);
                    }
                }
                Thread.sleep(20);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Yefira.LOGGER.error("Error polling native server events", e);
            }
        }
    }

    private void handleServerEvent(NativeSync.ServerEvent ev) {
        switch (ev.type) {
            case NativeSync.EVENT_CLIENT_CONNECTED:
                Yefira.LOGGER.info("DCC client connected: id={}", ev.clientId);
                break;
            case NativeSync.EVENT_CLIENT_DISCONNECTED:
                Yefira.LOGGER.info("DCC client disconnected: id={}, reason={}", ev.clientId, ev.message);
                break;
            case NativeSync.EVENT_BASELINE_REQUESTED:
            case NativeSync.EVENT_CLIENT_RESYNC: {
                Yefira.LOGGER.info("Baseline requested for client id={}", ev.clientId);
                SelectionManager sm = SelectionManager.getInstance();
                if (sm.hasSelection() && sm.getCurrentLevel() != null) {
                    streamingExecutor.submit(() -> streamBaseline(ev.clientId, sm.getCurrentLevel(), sm.getCurrentSelection()));
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onSelectionChanged(Level level, SelectionBox selection) {
        if (serverHandle == 0 || !isRunning() || level == null || selection == null) {
            return;
        }
        clearPendingDeltaChanges();
        broadcastBaseline(level, selection);
    }

    @Override
    public void onSelectionCleared() {
        clearPendingDeltaChanges();
        if (serverHandle != 0 && isRunning()) {
            NativeSync.nativeServerClearSelection(serverHandle);
        }
    }

    public void broadcastBaseline(Level level, SelectionBox selection) {
        if (serverHandle == 0 || !isRunning() || level == null || selection == null) {
            return;
        }
        streamingExecutor.submit(() -> streamBaseline(0L, level, selection));
    }

    public void streamBaseline(long clientId, Level level, SelectionBox selection) {
        if (serverHandle == 0 || !isRunning() || level == null || selection == null) {
            return;
        }

        try {
            CompletableFuture<CanonicalVoxelScanner.BaselineScanResult> scanFuture = new CompletableFuture<>();
            CompletableFuture<List<VisualEntityEntry>> visualScanFuture = new CompletableFuture<>();
            boolean clientLevel = level.isClientSide();
            Runnable scanTask = () -> {
                try {
                    String dimension = VersionCompat.level().getDimensionId(level);
                    scanFuture.complete(CanonicalVoxelScanner.scanBaseline(
                            level, selection, dimension,
                            () -> !isRunning(),
                            clientLevel ? CLIENT_SCAN_TIMEOUT_MILLIS : CanonicalVoxelScanner.DEFAULT_MAX_SCAN_MILLIS
                    ));

                    String[] subscribedArr = NativeSync.nativeServerGetSubscribedVisualEntities(serverHandle);
                    if (subscribedArr != null && subscribedArr.length > 0) {
                        visualScanFuture.complete(VisualEntityScanner.scanVisualEntities(level, selection, Set.of(subscribedArr)));
                    } else {
                        visualScanFuture.complete(Collections.emptyList());
                    }
                } catch (Throwable t) {
                    scanFuture.completeExceptionally(t);
                    visualScanFuture.completeExceptionally(t);
                }
            };

            if (clientLevel) {
                ClientThread.executeChecked(scanTask);
            } else {
                ServerThread.executeChecked(scanTask);
            }

            CanonicalVoxelScanner.BaselineScanResult scan = scanFuture.get(BASELINE_SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (scan == null || !isRunning()) {
                return;
            }

            List<VisualEntityEntry> visualEntities = visualScanFuture.get(BASELINE_SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (visualEntities != null) {
                trackedVisualEntities.clear();
                for (VisualEntityEntry ve : visualEntities) {
                    trackedVisualEntities.put(ve.getUuid(), ve);
                }
            }
            byte[] visualEntitiesBytes = VisualEntityEntry.serializeList(visualEntities);

            int numEntities = scan.entities != null ? scan.entities.size() : 0;
            int[] entityCoords = new int[numEntities * 3];
            String[] entityJsons = new String[numEntities];
            for (int i = 0; i < numEntities; i++) {
                EntityEntry ee = scan.entities.get(i);
                entityCoords[i * 3] = ee.relX();
                entityCoords[i * 3 + 1] = ee.relY();
                entityCoords[i * 3 + 2] = ee.relZ();
                entityJsons[i] = ee.getJson();
            }

            NativeSync.nativeServerSubmitBaseline(
                    serverHandle,
                    clientId,
                    scan.minPos.getX(), scan.minPos.getY(), scan.minPos.getZ(),
                    scan.sizeX, scan.sizeY, scan.sizeZ,
                    scan.dimension,
                    scan.voxelIndices,
                    scan.blockPalette.toArray(new String[0]),
                    scan.biomeIndices,
                    scan.biomePalette.toArray(new String[0]),
                    entityCoords,
                    entityJsons,
                    visualEntitiesBytes
            );
        } catch (Exception e) {
            Yefira.LOGGER.error("Failed to stream baseline for client " + clientId, e);
        }
    }

    public void queueDeltaUpdate(SelectionBox selection, BlockChangeEntry change) {
        if (serverHandle == 0 || !isRunning() || getConnectedCount() == 0) return;
        synchronized (pendingDeltaChanges) {
            if (pendingDeltaSelection != null && !sameBounds(pendingDeltaSelection, selection)) {
                pendingDeltaChanges.clear();
            }
            pendingDeltaSelection = selection;
            pendingDeltaChanges.put(change.pos().immutable(), change);
            if (pendingDeltaChanges.size() > MAX_PENDING_DELTA_CHANGES) {
                pendingDeltaChanges.clear();
            }
        }
    }

    public void flushQueuedDeltaUpdates() {
        if (serverHandle == 0 || !isRunning() || getConnectedCount() == 0) return;
        List<BlockChangeEntry> changes;
        SelectionBox selection;
        synchronized (pendingDeltaChanges) {
            if (pendingDeltaChanges.isEmpty() || pendingDeltaSelection == null) return;
            selection = pendingDeltaSelection;
            changes = new ArrayList<>(pendingDeltaChanges.values());
            pendingDeltaChanges.clear();
            pendingDeltaSelection = null;
        }
        broadcastDeltaUpdate(selection, changes);
    }

    public boolean broadcastDeltaUpdate(SelectionBox selection, List<BlockChangeEntry> changes) {
        if (serverHandle == 0 || !isRunning() || getConnectedCount() == 0 || changes == null || changes.isEmpty()) {
            return true;
        }
        try {
            BlockPos min = selection.getMin();
            int numChanges = changes.size();
            int[] relCoords = new int[numChanges * 3];
            String[] stateStrings = new String[numChanges];

            for (int i = 0; i < numChanges; i++) {
                BlockChangeEntry c = changes.get(i);
                relCoords[i * 3] = c.pos().getX() - min.getX();
                relCoords[i * 3 + 1] = c.pos().getY() - min.getY();
                relCoords[i * 3 + 2] = c.pos().getZ() - min.getZ();
                stateStrings[i] = CanonicalVoxelScanner.normalizeCanonicalBlockState(c.state());
            }

            Level currentLevel = SelectionManager.getInstance().getCurrentLevel();
            List<Integer> entityCoordsList = new ArrayList<>();
            List<String> entityJsonsList = new ArrayList<>();

            if (currentLevel != null) {
                for (BlockChangeEntry c : changes) {
                    int rx = c.pos().getX() - min.getX();
                    int ry = c.pos().getY() - min.getY();
                    int rz = c.pos().getZ() - min.getZ();
                    boolean hasEntity = false;

                    if (c.state().hasBlockEntity()) {
                        net.minecraft.world.level.block.entity.BlockEntity be = currentLevel.getBlockEntity(c.pos());
                        if (be != null) {
                            String json = VersionCompat.level().serializeBlockEntityJson(be);
                            if (json != null && !json.isEmpty()) {
                                hasEntity = true;
                                trackedEntityPositions.add(c.pos().immutable());
                                entityCoordsList.add(rx);
                                entityCoordsList.add(ry);
                                entityCoordsList.add(rz);
                                entityJsonsList.add(json);
                            }
                        }
                    }

                    if (!hasEntity && trackedEntityPositions.remove(c.pos())) {
                        entityCoordsList.add(rx);
                        entityCoordsList.add(ry);
                        entityCoordsList.add(rz);
                        entityJsonsList.add(null);
                    }
                }
            }

            int numEntities = entityJsonsList.size();
            int[] entityCoords = new int[numEntities * 3];
            for (int i = 0; i < entityCoords.length; i++) {
                entityCoords[i] = entityCoordsList.get(i);
            }
            String[] entityJsons = entityJsonsList.toArray(new String[0]);

            NativeSync.nativeServerSubmitDeltas(serverHandle, relCoords, stateStrings, entityCoords, entityJsons);
            return true;
        } catch (Exception e) {
            Yefira.LOGGER.error("Error submitting delta update to native server", e);
            return false;
        }
    }

    public boolean sendSafe(byte[] data) {
        if (serverHandle != 0 && isRunning() && data != null && data.length > 0) {
            NativeSync.nativeServerBroadcastPacket(serverHandle, data);
            return true;
        }
        return false;
    }

    public void tickVisualEntities(long currentTick) {
        if (currentTick % 5 != 0) return;
        if (serverHandle == 0 || !isRunning() || getConnectedCount() == 0) return;
        SelectionManager sm = SelectionManager.getInstance();
        if (!sm.hasSelection() || sm.getCurrentLevel() == null) return;
        syncVisualEntities(sm.getCurrentLevel(), sm.getCurrentSelection());
    }

    public void syncVisualEntities(Level level, SelectionBox selection) {
        if (serverHandle == 0 || !isRunning() || getConnectedCount() == 0 || selection == null || level == null) {
            return;
        }
        String[] subscribedArr = NativeSync.nativeServerGetSubscribedVisualEntities(serverHandle);
        if (subscribedArr == null || subscribedArr.length == 0) {
            trackedVisualEntities.clear();
            return;
        }
        Set<String> subscribed = Set.of(subscribedArr);
        List<VisualEntityEntry> currentEntities = VisualEntityScanner.scanVisualEntities(level, selection, subscribed);
        Map<UUID, VisualEntityEntry> currentMap = new HashMap<>(currentEntities.size());
        for (VisualEntityEntry e : currentEntities) {
            currentMap.put(e.getUuid(), e);
        }

        // 1. Detect additions and modifications -> UPSERT
        for (VisualEntityEntry curr : currentEntities) {
            VisualEntityEntry prev = trackedVisualEntities.get(curr.getUuid());
            if (prev == null || !Arrays.equals(prev.getCustomPayload(), curr.getCustomPayload())
                    || prev.getX() != curr.getX() || prev.getY() != curr.getY() || prev.getZ() != curr.getZ()
                    || prev.getYaw() != curr.getYaw() || prev.getPitch() != curr.getPitch()
                    || prev.getFacing() != curr.getFacing()) {
                NativeSync.nativeServerSubmitVisualEntityUpsert(
                        serverHandle,
                        curr.getUuid().getMostSignificantBits(),
                        curr.getUuid().getLeastSignificantBits(),
                        curr.getId(),
                        curr.getX(), curr.getY(), curr.getZ(),
                        curr.getPitch(), curr.getYaw(), curr.getRoll(),
                        curr.getScaleX(), curr.getScaleY(), curr.getScaleZ(),
                        curr.getFacing(),
                        curr.getCustomPayload()
                );
                trackedVisualEntities.put(curr.getUuid(), curr);
            }
        }

        // 2. Detect removals -> REMOVE
        Iterator<Map.Entry<UUID, VisualEntityEntry>> it = trackedVisualEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, VisualEntityEntry> entry = it.next();
            if (!currentMap.containsKey(entry.getKey())) {
                NativeSync.nativeServerSubmitVisualEntityRemove(
                        serverHandle,
                        entry.getKey().getMostSignificantBits(),
                        entry.getKey().getLeastSignificantBits()
                );
                it.remove();
            }
        }
    }

    private void clearPendingDeltaChanges() {
        synchronized (pendingDeltaChanges) {
            pendingDeltaChanges.clear();
            pendingDeltaSelection = null;
        }
        trackedEntityPositions.clear();
        trackedVisualEntities.clear();
    }

    private static boolean sameBounds(SelectionBox a, SelectionBox b) {
        return a.getMin().equals(b.getMin()) && a.getMax().equals(b.getMax());
    }
}
