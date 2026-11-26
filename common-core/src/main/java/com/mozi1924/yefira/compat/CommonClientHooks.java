package com.mozi1924.yefira.compat;

import com.mozi1924.yefira.network.WebSocketServerManager;
import com.mozi1924.yefira.platform.Services;
import com.mozi1924.yefira.selection.SelectionManager;
import com.mozi1924.yefira.selection.SelectionStorageManager;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Shared client connection lifecycle hooks across all supported Minecraft versions.
 */
public final class CommonClientHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("yefira");
    private static volatile Runnable openGuiHandler;

    private CommonClientHooks() {}

    public static void setOpenGuiHandler(Runnable handler) {
        openGuiHandler = handler;
    }

    public static boolean openGui() {
        Runnable handler = openGuiHandler;
        if (handler == null) {
            return false;
        }
        handler.run();
        return true;
    }

    /**
     * Common client joined server handler.
     */
    public static void onClientJoin(Executor clientExecutor, boolean isSinglePlayer, String serverIp, Level level) {
        ClientThread.setExecutor(clientExecutor);
        boolean multi = !isSinglePlayer;
        SelectionManager.getInstance().setSelectionSource(multi
                ? SelectionManager.SelectionSource.LOCAL_CLIENT
                : SelectionManager.SelectionSource.SERVER_AUTHORITATIVE);
        if (multi) {
            if (serverIp != null && !serverIp.isEmpty() && level != null) {
                Path serverPath = SelectionStorageManager.getServerStoragePath(serverIp);
                boolean loaded = SelectionManager.getInstance().loadSavedSelection(serverPath, level);
                if (loaded) {
                    LOGGER.info("Loaded server selection for IP: {}", serverIp);
                }
            }
            String label = (serverIp != null && !serverIp.isBlank()) ? serverIp : "Multiplayer";
            String dim = level != null ? VersionCompat.level().getDimensionId(level) : "minecraft:overworld";
            WebSocketServerManager.getInstance().startIpcOnly(
                    label,
                    "",
                    Services.PLATFORM.getPlatformName(),
                    dim
            );
            LOGGER.info(
                "Remote server detected; local IPC started for local export. "
                    + "For remote server selection, switch mode in the Yefira GUI or connect the DCC to the server's WebSocket.");
        }
    }

    /**
     * Ensures local IPC is active when switching back to local client export mode in multiplayer.
     */
    public static void ensureLocalIpcRunning(String serverIp, Level level) {
        if (WebSocketServerManager.getInstance().isIpcRunning()) {
            return;
        }
        String label = (serverIp != null && !serverIp.isBlank()) ? serverIp : "Multiplayer";
        String dim = level != null ? VersionCompat.level().getDimensionId(level) : "minecraft:overworld";
        WebSocketServerManager.getInstance().startIpcOnly(
                label,
                "",
                Services.PLATFORM.getPlatformName(),
                dim
        );
    }

    /**
     * Common client disconnected from server handler.
     */
    public static void onClientDisconnect(boolean isSinglePlayer) {
        ClientThread.clear();
        SelectionManager.getInstance().setSelectionSource(SelectionManager.SelectionSource.SERVER_AUTHORITATIVE);
        if (!isSinglePlayer) {
            SelectionManager.getInstance().setActiveStoragePath(null);
            WebSocketServerManager.getInstance().stopServer();
        }
    }
}
