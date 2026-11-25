package com.mozi1924.yefira.client;

import com.mozi1924.yefira.client.ghost.GhostModeManager;
import com.mozi1924.yefira.compat.CommonClientHooks;
import com.mozi1924.yefira.compat.IClientCompat;
import com.mozi1924.yefira.compat.VersionCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class YefiraClient {

    public static KeyMapping keyGhostMode;
    public static KeyMapping keyOpenGui;
    public static KeyMapping keyFocus;
    public static KeyMapping keyClear;
    public static KeyMapping keyPresetBox;

    private static volatile boolean initialized = false;

    public static void init(IClientCompat compat) {
        if (compat != null) {
            VersionCompat.setClientCompat(compat);
        }
        if (!initialized) {
            initialized = true;
            CommonClientHooks.setOpenGuiHandler(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> openConfigScreen(mc));
            });
        }
    }

    public static void openConfigScreen(Minecraft mc) {
        VersionCompat.client().openScreen(mc, VersionCompat.client().createConfigScreen());
    }

    public static void createKeyMappings() {
        IClientCompat compat = VersionCompat.client();
        keyGhostMode = compat.createKeyMapping("key.yefira.ghost_mode", GLFW.GLFW_KEY_G);
        keyOpenGui = compat.createKeyMapping("key.yefira.open_gui", GLFW.GLFW_KEY_O);
        keyFocus = compat.createKeyMapping("key.yefira.focus", GLFW.GLFW_KEY_V);
        keyClear = compat.createKeyMapping("key.yefira.clear", GLFW.GLFW_KEY_X);
        keyPresetBox = compat.createKeyMapping("key.yefira.preset_box", GLFW.GLFW_KEY_C);
    }

    public static void onClientJoinServer(Minecraft client) {
        if (GhostModeManager.getInstance().isActive()) {
            GhostModeManager.getInstance().disable();
        }
        String serverIp = (client.getCurrentServer() != null) ? client.getCurrentServer().ip : null;
        CommonClientHooks.onClientJoin(client::execute, client.hasSingleplayerServer(), serverIp, client.level);
    }

    public static void onClientDisconnectServer(Minecraft client) {
        if (GhostModeManager.getInstance().isActive()) {
            GhostModeManager.getInstance().disable();
        }
        CommonClientHooks.onClientDisconnect(client.hasSingleplayerServer());
    }

    public static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        if (keyGhostMode != null) {
            while (keyGhostMode.consumeClick()) {
                GhostModeManager.getInstance().toggle();
            }
        }

        if (keyOpenGui != null) {
            while (keyOpenGui.consumeClick()) {
                openConfigScreen(client);
            }
        }

        if (!GhostModeManager.getInstance().isActive()) {
            if (keyFocus != null) while (keyFocus.consumeClick()) {}
            if (keyClear != null) while (keyClear.consumeClick()) {}
            if (keyPresetBox != null) while (keyPresetBox.consumeClick()) {}
            return;
        }

        GhostModeManager.getInstance().tickMovement();

        if (keyFocus != null) {
            while (keyFocus.consumeClick()) {
                GhostModeManager.getInstance().focusSelection();
            }
        }

        if (keyClear != null) {
            while (keyClear.consumeClick()) {
                GhostModeManager.getInstance().clearSelectionAndSync();
            }
        }

        if (keyPresetBox != null) {
            while (keyPresetBox.consumeClick()) {
                GhostModeManager.getInstance().createPresetBoxAtCursorOrPivot(16);
            }
        }
    }
}
