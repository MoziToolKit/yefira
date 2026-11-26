package com.mozi1924.yefira.client.gui;

import com.mozi1924.yefira.compat.CommonClientHooks;
import com.mozi1924.yefira.compat.VersionCompat;
import com.mozi1924.yefira.config.YefiraConfig;
import com.mozi1924.yefira.network.WebSocketServerManager;
import com.mozi1924.yefira.selection.SelectionBox;
import com.mozi1924.yefira.selection.SelectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared Yefira control panel logic across all Minecraft versions.
 * Version modules only provide a tiny Screen subclass bridging render() vs extractRenderState().
 */
public abstract class YefiraScreenBase extends Screen {

    public enum SyncMode {
        CLIENT,
        SERVER
    }

    public enum ClientTab {
        OVERVIEW,
        WEBSOCKET
    }

    private static final int PANEL_HALF_WIDTH = 120;
    private static final int FULL_WIDTH = PANEL_HALF_WIDTH * 2;
    private static final int HALF_COL_WIDTH = 118;

    private SyncMode currentMode = SyncMode.CLIENT;
    private ClientTab activeTab = ClientTab.OVERVIEW;
    private boolean tokenVisible = false;
    private boolean wsAdvancedExpanded = false;
    private long copiedFeedbackUntilMs = 0L;
    private long refreshFeedbackUntilMs = 0L;

    // Mode / tab navigation
    private Button clientModeBtn;
    private Button serverModeBtn;
    private Button overviewTabBtn;
    private Button wsTabBtn;

    // Overview tab controls
    private Button wsQuickToggleBtn;
    private Button wsConfigureBtn;
    private Button refreshSnapshotBtn;
    private Button clearSelectionBtn;

    // WebSocket tab controls
    private Button startStopWsBtn;
    private Button restartWsBtn;
    private EditBox hostEdit;
    private Button hostPresetBtn;
    private EditBox portEdit;
    private EditBox tokenEdit;
    private Button toggleTokenVisBtn;
    private Button genTokenBtn;
    private Button copyConnBtn;
    private Button advancedToggleBtn;
    private Checkbox autoStartCheckbox;
    private Button loopbackAuthBtn;
    private Button ipcTokenBtn;

    // Remote server controls
    private Button remoteStartBtn;
    private Button remoteRestartBtn;
    private Button remoteStopBtn;
    private Button remoteRefreshBtn;
    private Button remoteClearBtn;

    protected YefiraScreenBase() {
        super(Component.translatable("yefira.gui.title"));
        YefiraConfig.load();
    }

    private boolean isMultiplayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.hasSingleplayerServer();
    }

    private String remoteServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
            return mc.getCurrentServer().ip;
        }
        return "";
    }

    private boolean hasOpPermission() {
        return VersionCompat.client().hasOpPermission(Minecraft.getInstance());
    }

    private void applySelectionSource(boolean multi) {
        SelectionManager.getInstance().setSelectionSource(
                currentMode == SyncMode.CLIENT && multi
                        ? SelectionManager.SelectionSource.LOCAL_CLIENT
                        : SelectionManager.SelectionSource.SERVER_AUTHORITATIVE);
    }

    private int contentStartY(boolean multi) {
        if (!multi) {
            return 48;
        }
        return currentMode == SyncMode.CLIENT ? 62 : 44;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        boolean multi = isMultiplayer();
        YefiraConfig cfg = YefiraConfig.getInstance();
        boolean op = hasOpPermission();

        if (!multi) {
            this.currentMode = SyncMode.CLIENT;
        } else if (this.currentMode == SyncMode.SERVER && !op) {
            this.currentMode = SyncMode.CLIENT;
        }

        if (multi) {
            int modeY = 18;
            this.clientModeBtn = Button.builder(Component.translatable("yefira.gui.mode.client"), btn -> {
                if (this.currentMode != SyncMode.CLIENT) {
                    this.currentMode = SyncMode.CLIENT;
                    applySelectionSource(true);
                    Minecraft mc = Minecraft.getInstance();
                    CommonClientHooks.ensureLocalIpcRunning(remoteServerAddress(), mc.level);
                    this.rebuildWidgets();
                }
            }).bounds(centerX - PANEL_HALF_WIDTH, modeY, HALF_COL_WIDTH, 18).build();
            this.clientModeBtn.active = (currentMode != SyncMode.CLIENT);
            this.addRenderableWidget(this.clientModeBtn);

            this.serverModeBtn = Button.builder(Component.translatable("yefira.gui.mode.server"), btn -> {
                applyConfigFromFields();
                this.currentMode = SyncMode.SERVER;
                applySelectionSource(true);
                WebSocketServerManager.getInstance().stopServer();
                this.rebuildWidgets();
            }).bounds(centerX + 2, modeY, HALF_COL_WIDTH, 18).build();
            this.serverModeBtn.active = op && (currentMode != SyncMode.SERVER);
            this.addRenderableWidget(this.serverModeBtn);
        }
        applySelectionSource(multi);

        if (currentMode == SyncMode.CLIENT) {
            int tabY = multi ? 38 : 22;
            int tabH = multi ? 18 : 20;
            this.overviewTabBtn = Button.builder(Component.translatable("yefira.gui.tab.overview"), btn -> {
                if (this.activeTab != ClientTab.OVERVIEW) {
                    applyConfigFromFields();
                    this.activeTab = ClientTab.OVERVIEW;
                    this.rebuildWidgets();
                }
            }).bounds(centerX - PANEL_HALF_WIDTH, tabY, HALF_COL_WIDTH, tabH).build();
            this.overviewTabBtn.active = (activeTab != ClientTab.OVERVIEW);
            this.addRenderableWidget(this.overviewTabBtn);

            this.wsTabBtn = Button.builder(Component.translatable("yefira.gui.tab.websocket"), btn -> {
                if (this.activeTab != ClientTab.WEBSOCKET) {
                    this.activeTab = ClientTab.WEBSOCKET;
                    this.rebuildWidgets();
                }
            }).bounds(centerX + 2, tabY, HALF_COL_WIDTH, tabH).build();
            this.wsTabBtn.active = (activeTab != ClientTab.WEBSOCKET);
            this.addRenderableWidget(this.wsTabBtn);

            int contentY = contentStartY(multi);
            if (activeTab == ClientTab.OVERVIEW) {
                initOverviewTab(centerX, contentY);
            } else {
                initWebSocketTab(centerX, contentY, cfg);
            }
        } else {
            initRemoteServerView(centerX, contentStartY(true), op);
        }

        Button doneButton = Button.builder(Component.translatable("gui.done"), btn -> {
            applyConfigFromFields();
            this.onClose();
        }).bounds(centerX - 100, this.height - 26, 200, 20).build();
        this.addRenderableWidget(doneButton);
    }

    private void initOverviewTab(int centerX, int contentY) {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        YefiraConfig cfg = YefiraConfig.getInstance();
        SelectionManager mgr = SelectionManager.getInstance();
        Minecraft mc = Minecraft.getInstance();

        Component wsToggleLabel = server.isWsRunning()
                ? Component.translatable("yefira.gui.ws.stop")
                : Component.translatable("yefira.gui.ws.start");
        this.wsQuickToggleBtn = Button.builder(wsToggleLabel, btn -> {
            if (server.isWsRunning()) {
                server.stopWs();
            } else if (!server.startServer(cfg.getHost(), cfg.getPort())) {
                reportStartFailure(mc, cfg, server);
            }
            this.rebuildWidgets();
        }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 66, HALF_COL_WIDTH, 20).build();
        this.addRenderableWidget(this.wsQuickToggleBtn);

        this.wsConfigureBtn = Button.builder(Component.translatable("yefira.gui.ws.configure"), btn -> {
            this.activeTab = ClientTab.WEBSOCKET;
            this.rebuildWidgets();
        }).bounds(centerX + 2, contentY + 66, HALF_COL_WIDTH, 20).build();
        this.addRenderableWidget(this.wsConfigureBtn);

        boolean canRefresh = mgr.hasSelection()
                && mgr.getCurrentLevel() != null
                && server.isRunning()
                && server.getConnectedCount() > 0;
        this.refreshSnapshotBtn = Button.builder(refreshButtonLabel(), btn -> {
            if (mgr.hasSelection() && mgr.getCurrentLevel() != null && server.isRunning() && server.getConnectedCount() > 0) {
                server.broadcastBaseline(mgr.getCurrentLevel(), mgr.getCurrentSelection());
                this.refreshFeedbackUntilMs = System.currentTimeMillis() + 2000L;
                btn.setMessage(refreshButtonLabel());
            }
        }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 118, HALF_COL_WIDTH, 20).build();
        this.refreshSnapshotBtn.active = canRefresh;
        this.addRenderableWidget(this.refreshSnapshotBtn);

        this.clearSelectionBtn = Button.builder(Component.translatable("yefira.gui.selection.clear"), btn -> {
            SelectionManager.getInstance().clearSelection();
            this.rebuildWidgets();
        }).bounds(centerX + 2, contentY + 118, HALF_COL_WIDTH, 20).build();
        this.clearSelectionBtn.active = mgr.hasSelection();
        this.addRenderableWidget(this.clearSelectionBtn);
    }

    private void initWebSocketTab(int centerX, int contentY, YefiraConfig cfg) {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        Minecraft mc = Minecraft.getInstance();

        Component wsBtnText = server.isWsRunning()
                ? Component.translatable("yefira.gui.ws.stop")
                : Component.translatable("yefira.gui.ws.start");
        this.startStopWsBtn = Button.builder(wsBtnText, btn -> {
            applyConfigFromFields();
            if (server.isWsRunning()) {
                server.stopWs();
            } else if (!server.startServer(cfg.getHost(), cfg.getPort())) {
                reportStartFailure(mc, cfg, server);
            }
            this.rebuildWidgets();
        }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 14, HALF_COL_WIDTH, 20).build();
        this.addRenderableWidget(this.startStopWsBtn);

        this.restartWsBtn = Button.builder(Component.translatable("yefira.gui.ws.restart"), btn -> {
            applyConfigFromFields();
            if (!server.restartWs(cfg.getHost(), cfg.getPort())) {
                reportStartFailure(mc, cfg, server);
            }
            this.rebuildWidgets();
        }).bounds(centerX + 2, contentY + 14, HALF_COL_WIDTH, 20).build();
        this.addRenderableWidget(this.restartWsBtn);

        this.hostEdit = new EditBox(this.font, centerX - PANEL_HALF_WIDTH, contentY + 50, 114, 20, Component.translatable("yefira.gui.host"));
        this.hostEdit.setMaxLength(128);
        this.hostEdit.setValue(cfg.getHost());
        this.addRenderableWidget(this.hostEdit);

        this.hostPresetBtn = Button.builder(hostPresetLabel(cfg.getHost()), btn -> {
            applyConfigFromFields();
            boolean currentlyLoopback = WebSocketServerManager.isLoopbackHost(cfg.getHost());
            String nextHost = currentlyLoopback ? "0.0.0.0" : "127.0.0.1";
            cfg.setHost(nextHost);
            if (!currentlyLoopback) {
                // Switching back to loopback
            } else if (cfg.getAuthToken().isEmpty()) {
                // Switching to LAN bind (0.0.0.0): auto-generate a token so starting WS works immediately
                cfg.setAuthToken(YefiraConfig.generateRandomToken());
            }
            YefiraConfig.save();
            this.hostEdit.setValue(cfg.getHost());
            if (this.tokenEdit != null) {
                this.tokenEdit.setValue(cfg.getAuthToken());
            }
            btn.setMessage(hostPresetLabel(cfg.getHost()));
            server.applyAuthConfig();
        }).bounds(centerX - 2, contentY + 50, 60, 20).build();
        this.addRenderableWidget(this.hostPresetBtn);

        this.portEdit = new EditBox(this.font, centerX + 62, contentY + 50, 58, 20, Component.translatable("yefira.gui.port"));
        this.portEdit.setMaxLength(5);
        this.portEdit.setValue(String.valueOf(cfg.getPort()));
        this.addRenderableWidget(this.portEdit);

        this.tokenEdit = new EditBox(this.font, centerX - PANEL_HALF_WIDTH, contentY + 84, 148, 20, Component.translatable("yefira.gui.token"));
        this.tokenEdit.setMaxLength(256);
        this.tokenEdit.setValue(cfg.getAuthToken());
        VersionCompat.client().attachPasswordMask(this.tokenEdit, () -> !this.tokenVisible);
        this.addRenderableWidget(this.tokenEdit);

        this.toggleTokenVisBtn = Button.builder(tokenVisLabel(), btn -> {
            this.tokenVisible = !this.tokenVisible;
            btn.setMessage(tokenVisLabel());
        }).bounds(centerX + 32, contentY + 84, 38, 20).build();
        this.addRenderableWidget(this.toggleTokenVisBtn);

        this.genTokenBtn = Button.builder(Component.translatable("yefira.gui.token.generate"), btn -> {
            applyConfigFromFields();
            String generated = YefiraConfig.generateRandomToken();
            cfg.setAuthToken(generated);
            YefiraConfig.save();
            this.tokenEdit.setValue(generated);
            server.applyAuthConfig();
        }).bounds(centerX + 74, contentY + 84, 46, 20).build();
        this.addRenderableWidget(this.genTokenBtn);

        this.copyConnBtn = Button.builder(copyButtonLabel(), btn -> {
            applyConfigFromFields();
            String connHost = WebSocketServerManager.isLoopbackHost(cfg.getHost())
                    ? cfg.getHost()
                    : ("0.0.0.0".equals(cfg.getHost()) ? "<LAN_IP>" : cfg.getHost());
            int port = server.isWsRunning() ? WebSocketServerManager.getPort() : cfg.getPort();
            StringBuilder payload = new StringBuilder();
            payload.append("ws://").append(connHost).append(':').append(port);
            if (!cfg.getAuthToken().isEmpty()) {
                payload.append("\nToken: ").append(cfg.getAuthToken());
            }
            VersionCompat.client().copyToClipboard(mc, payload.toString());
            this.copiedFeedbackUntilMs = System.currentTimeMillis() + 2000L;
            btn.setMessage(copyButtonLabel());
        }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 108, HALF_COL_WIDTH, 20).build();
        this.addRenderableWidget(this.copyConnBtn);

        this.advancedToggleBtn = Button.builder(advancedToggleLabel(), btn -> {
            applyConfigFromFields();
            this.wsAdvancedExpanded = !this.wsAdvancedExpanded;
            this.rebuildWidgets();
        }).bounds(centerX + 2, contentY + 108, HALF_COL_WIDTH, 20).build();
        this.addRenderableWidget(this.advancedToggleBtn);

        if (this.wsAdvancedExpanded) {
            boolean multi = isMultiplayer();
            this.autoStartCheckbox = VersionCompat.client().createCheckbox(
                    centerX - PANEL_HALF_WIDTH, contentY + 134, FULL_WIDTH, 20,
                    this.font,
                    Component.translatable("yefira.gui.autostart"),
                    cfg.isAutoStartOnWorldLoad(),
                    val -> {
                        cfg.setAutoStartOnWorldLoad(val);
                        YefiraConfig.save();
                    }
            );
            this.autoStartCheckbox.active = !multi;
            this.addRenderableWidget(this.autoStartCheckbox);

            this.loopbackAuthBtn = Button.builder(loopbackAuthLabel(cfg), btn -> {
                applyConfigFromFields();
                cfg.setAllowLoopbackWithoutToken(!cfg.isAllowLoopbackWithoutToken());
                YefiraConfig.save();
                server.applyAuthConfig();
                btn.setMessage(loopbackAuthLabel(cfg));
            }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 156, HALF_COL_WIDTH, 20).build();
            this.addRenderableWidget(this.loopbackAuthBtn);

            this.ipcTokenBtn = Button.builder(ipcTokenLabel(cfg), btn -> {
                applyConfigFromFields();
                cfg.setRequireTokenOnIpc(!cfg.isRequireTokenOnIpc());
                YefiraConfig.save();
                server.applyAuthConfig();
                btn.setMessage(ipcTokenLabel(cfg));
            }).bounds(centerX + 2, contentY + 156, HALF_COL_WIDTH, 20).build();
            this.addRenderableWidget(this.ipcTokenBtn);
        }
    }

    private void initRemoteServerView(int centerX, int contentY, boolean op) {
        Minecraft mc = Minecraft.getInstance();

        this.remoteStartBtn = Button.builder(Component.translatable("yefira.gui.remote.start"), btn -> {
            if (op && mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("yefira server start");
            }
        }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 38, HALF_COL_WIDTH, 20).build();
        this.remoteStartBtn.active = op;
        this.addRenderableWidget(this.remoteStartBtn);

        this.remoteRestartBtn = Button.builder(Component.translatable("yefira.gui.remote.restart"), btn -> {
            if (op && mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("yefira server restart");
            }
        }).bounds(centerX + 2, contentY + 38, HALF_COL_WIDTH, 20).build();
        this.remoteRestartBtn.active = op;
        this.addRenderableWidget(this.remoteRestartBtn);

        this.remoteStopBtn = Button.builder(Component.translatable("yefira.gui.remote.stop"), btn -> {
            if (op && mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("yefira server stop");
            }
        }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 62, HALF_COL_WIDTH, 20).build();
        this.remoteStopBtn.active = op;
        this.addRenderableWidget(this.remoteStopBtn);

        this.remoteRefreshBtn = Button.builder(Component.translatable("yefira.gui.remote.refresh"), btn -> {
            if (op && mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("yefira refresh");
            }
        }).bounds(centerX + 2, contentY + 62, HALF_COL_WIDTH, 20).build();
        this.remoteRefreshBtn.active = op;
        this.addRenderableWidget(this.remoteRefreshBtn);

        this.remoteClearBtn = Button.builder(Component.translatable("yefira.gui.selection.clear"), btn -> {
            if (op && mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("yefira clear");
            }
        }).bounds(centerX - PANEL_HALF_WIDTH, contentY + 86, FULL_WIDTH, 20).build();
        this.remoteClearBtn.active = op;
        this.addRenderableWidget(this.remoteClearBtn);
    }

    private Component hostPresetLabel(String host) {
        return Component.translatable(
                WebSocketServerManager.isLoopbackHost(host)
                        ? "yefira.gui.host.preset.local"
                        : "yefira.gui.host.preset.lan");
    }

    private Component tokenVisLabel() {
        return Component.translatable(this.tokenVisible ? "yefira.gui.token.hide" : "yefira.gui.token.show");
    }

    private Component copyButtonLabel() {
        boolean copied = System.currentTimeMillis() < this.copiedFeedbackUntilMs;
        return Component.translatable(copied ? "yefira.gui.ws.copied" : "yefira.gui.ws.copy");
    }

    private Component refreshButtonLabel() {
        boolean refreshed = System.currentTimeMillis() < this.refreshFeedbackUntilMs;
        return Component.translatable(refreshed ? "yefira.gui.selection.refreshed" : "yefira.gui.selection.refresh");
    }

    private Component advancedToggleLabel() {
        return Component.translatable(
                this.wsAdvancedExpanded ? "yefira.gui.ws.advanced.collapse" : "yefira.gui.ws.advanced.expand");
    }

    private Component ipcTokenLabel(YefiraConfig cfg) {
        return Component.translatable("yefira.gui.ipc_token",
                cfg.isRequireTokenOnIpc() ? "ON" : "OFF");
    }

    private Component loopbackAuthLabel(YefiraConfig cfg) {
        return Component.translatable("yefira.gui.loopback_auth",
                cfg.isAllowLoopbackWithoutToken() ? "ON" : "OFF");
    }

    private void reportStartFailure(Minecraft mc, YefiraConfig cfg, WebSocketServerManager localServer) {
        if (mc.player == null) return;
        String err = localServer.getLastStartError();
        Component msg = err != null && !err.isBlank()
                ? Component.literal("§c[Yefira] " + err)
                : Component.translatable("yefira.command.server.start_failed", cfg.getHost(), cfg.getPort());
        VersionCompat.client().sendClientMessage(mc, msg);
    }

    private void applyConfigFromFields() {
        if (currentMode == SyncMode.SERVER || activeTab != ClientTab.WEBSOCKET) {
            return;
        }
        YefiraConfig cfg = YefiraConfig.getInstance();
        if (hostEdit != null) {
            String host = hostEdit.getValue().trim();
            if (!host.isEmpty()) {
                cfg.setHost(host);
            }
        }
        if (portEdit != null) {
            try {
                int port = Integer.parseInt(portEdit.getValue().trim());
                if (port >= 1024 && port <= 65535) {
                    cfg.setPort(port);
                }
            } catch (NumberFormatException ignored) {}
        }
        if (tokenEdit != null) {
            cfg.setAuthToken(tokenEdit.getValue().trim());
        }
        YefiraConfig.save();
        WebSocketServerManager.getInstance().applyAuthConfig();
    }

    @Override
    public void onClose() {
        applyConfigFromFields();
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.copyConnBtn != null && this.copiedFeedbackUntilMs > 0L
                && System.currentTimeMillis() >= this.copiedFeedbackUntilMs) {
            this.copiedFeedbackUntilMs = 0L;
            this.copyConnBtn.setMessage(copyButtonLabel());
        }
        if (this.refreshSnapshotBtn != null && this.refreshFeedbackUntilMs > 0L
                && System.currentTimeMillis() >= this.refreshFeedbackUntilMs) {
            this.refreshFeedbackUntilMs = 0L;
            this.refreshSnapshotBtn.setMessage(refreshButtonLabel());
        }
    }

    protected void renderOverlayText(IGuiDrawer graphics) {
        int centerX = this.width / 2;
        boolean multi = isMultiplayer();
        graphics.drawCenteredText(this.font, this.title, centerX, multi ? 6 : 8, 0xFFFFFF);

        int contentY = contentStartY(multi);

        if (currentMode == SyncMode.SERVER) {
            renderRemoteServerOverlay(graphics, centerX, contentY);
            return;
        }

        if (activeTab == ClientTab.OVERVIEW) {
            renderOverviewOverlay(graphics, centerX, contentY, multi);
        } else {
            renderWebSocketOverlay(graphics, centerX, contentY);
        }
    }

    private void renderOverviewOverlay(IGuiDrawer graphics, int centerX, int contentY, boolean multi) {
        int left = centerX - PANEL_HALF_WIDTH;
        int right = centerX + PANEL_HALF_WIDTH;
        WebSocketServerManager server = WebSocketServerManager.getInstance();

        // Card 1: Local IPC (zero-config)
        graphics.fill(left - 4, contentY, right + 4, contentY + 40, 0x44000000);
        boolean ipcRunning = server.isIpcRunning();
        Component ipcStatus = ipcRunning
                ? Component.translatable("yefira.gui.ipc.ready", String.valueOf(server.getIpcClientCount()))
                : Component.translatable("yefira.gui.ipc.offline");
        graphics.drawText(this.font, ipcStatus, left, contentY + 5, ipcRunning ? 0x55FF55 : 0xFF5555, true);
        Component ipcHint = multi
                ? Component.translatable("yefira.gui.local.note")
                : Component.translatable("yefira.gui.ipc.hint");
        graphics.drawText(this.font, ipcHint, left, contentY + 21, 0xAAAAAA, false);

        // Card 2: WebSocket optional sharing
        graphics.fill(left - 4, contentY + 44, right + 4, contentY + 90, 0x44000000);
        boolean wsRunning = server.isWsRunning();
        String host = WebSocketServerManager.getHost() != null ? WebSocketServerManager.getHost() : "127.0.0.1";
        Component wsStatus = wsRunning
                ? Component.translatable("yefira.gui.ws.status.running", host, String.valueOf(WebSocketServerManager.getPort()), String.valueOf(server.getWsClientCount()))
                : Component.translatable("yefira.gui.ws.status.stopped");
        graphics.drawText(this.font, wsStatus, left, contentY + 50, wsRunning ? 0x55FFFF : 0xAAAAAA, true);

        // Card 3: Selection
        graphics.fill(left - 4, contentY + 94, right + 4, contentY + 142, 0x44000000);
        renderSelectionSummary(graphics, centerX, contentY + 100);
    }

    private void renderWebSocketOverlay(IGuiDrawer graphics, int centerX, int contentY) {
        int left = centerX - PANEL_HALF_WIDTH;
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        YefiraConfig cfg = YefiraConfig.getInstance();

        boolean wsRunning = server.isWsRunning();
        String startErr = !wsRunning ? server.getLastStartError() : null;
        String host = WebSocketServerManager.getHost() != null ? WebSocketServerManager.getHost() : cfg.getHost();
        Component wsLine;
        int wsColor;
        if (wsRunning) {
            wsLine = Component.translatable("yefira.gui.ws.status.running", host, String.valueOf(WebSocketServerManager.getPort()), String.valueOf(server.getWsClientCount()));
            wsColor = 0x55FF55;
        } else if (startErr != null && !startErr.isBlank()) {
            wsLine = Component.literal("○ " + startErr);
            wsColor = 0xFF5555;
        } else {
            wsLine = Component.translatable("yefira.gui.ws.status.stopped");
            wsColor = 0xAAAAAA;
        }
        graphics.drawCenteredText(this.font, wsLine, centerX, contentY + 2, wsColor);

        graphics.drawText(this.font, Component.translatable("yefira.gui.host.label"), left, contentY + 40, 0xAAAAAA, true);
        graphics.drawText(this.font, Component.translatable("yefira.gui.port.label"), centerX + 62, contentY + 40, 0xAAAAAA, true);
        graphics.drawText(this.font, Component.translatable("yefira.gui.token.label"), left, contentY + 74, 0xAAAAAA, true);

        if (this.wsAdvancedExpanded) {
            boolean needToken = !WebSocketServerManager.isLoopbackHost(cfg.getHost()) && cfg.getAuthToken().isEmpty();
            if (needToken) {
                graphics.drawCenteredText(this.font, Component.translatable("yefira.gui.ws.token_required_hint"), centerX, contentY + 180, 0xFFAA55);
            }
        }
    }

    private void renderRemoteServerOverlay(IGuiDrawer graphics, int centerX, int contentY) {
        graphics.drawCenteredText(this.font,
                Component.translatable("yefira.gui.status.remote", remoteServerAddress()),
                centerX, contentY + 4, 0x55FFFF);
        if (hasOpPermission()) {
            graphics.drawCenteredText(this.font,
                    Component.translatable("yefira.gui.mode.server.op_ok"),
                    centerX, contentY + 18, 0x55FF55);
        } else {
            graphics.drawCenteredText(this.font,
                    Component.translatable("yefira.gui.mode.server.no_op"),
                    centerX, contentY + 18, 0xFF5555);
        }
        renderSelectionSummary(graphics, centerX, contentY + 114);
    }

    private void renderSelectionSummary(IGuiDrawer graphics, int centerX, int y) {
        SelectionManager mgr = SelectionManager.getInstance();
        SelectionBox sel = mgr.getCurrentSelection();
        if (sel != null && sel.getMin() != null && sel.getMax() != null) {
            boolean oversized = sel.isOversized();
            String key = oversized ? "yefira.gui.selection.oversized" : "yefira.gui.selection.info";
            int color = oversized ? 0xFF5555 : 0x55FFFF;
            Component selText = Component.translatable(key,
                    sel.getMin().toShortString(), sel.getMax().toShortString(),
                    String.valueOf(sel.getSizeX()), String.valueOf(sel.getSizeY()),
                    String.valueOf(sel.getSizeZ()), String.valueOf(sel.getVolume()));
            graphics.drawCenteredText(this.font, selText, centerX, y, color);
        } else {
            graphics.drawCenteredText(this.font, Component.translatable("yefira.gui.selection.none"), centerX, y, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
