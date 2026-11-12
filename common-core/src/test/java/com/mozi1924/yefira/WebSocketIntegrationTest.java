package com.mozi1924.yefira;

import com.mozi1924.yefira.config.YefiraConfig;
import com.mozi1924.yefira.network.WebSocketServerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class WebSocketIntegrationTest {

    private static final int TEST_PORT = 18765;
    private static final String TEST_HOST = "127.0.0.1";

    private static byte[] helloBytes(String token) {
        byte[] auth = token == null ? new byte[0] : token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] dim = "minecraft:overworld".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + 2 + 2 + 4 + 4 + 16 + 8 + 8 + 24 + 8 + 2 + auth.length + 2 + dim.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.MAGIC);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.PROTOCOL_VERSION);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.OPCODE_HELLO);
        buf.putShort((short) 3);
        buf.putShort((short) 3);
        buf.putInt(0);
        buf.putInt(0);
        buf.put(new byte[16]);
        buf.putLong(0L);
        buf.putLong(0L);
        for (int i = 0; i < 6; i++) buf.putInt(0);
        buf.putLong(0L);
        buf.putShort((short) auth.length);
        buf.put(auth);
        buf.putShort((short) dim.length);
        buf.put(dim);
        return buf.array();
    }

    private static byte[] pingBytes(long timestamp) {
        ByteBuffer buf = ByteBuffer.allocate(4 + 8);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.MAGIC);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.PROTOCOL_VERSION);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.OPCODE_PING);
        buf.putLong(timestamp);
        return buf.array();
    }

    static class TestClient implements WebSocket.Listener {
        final WebSocket ws;
        final CompletableFuture<Boolean> openFuture = new CompletableFuture<>();
        final CompletableFuture<Integer> closeFuture = new CompletableFuture<>();
        final Consumer<ByteBuffer> onBinaryHandler;

        TestClient(URI uri, Consumer<ByteBuffer> onBinaryHandler) {
            this.onBinaryHandler = onBinaryHandler;
            HttpClient client = HttpClient.newHttpClient();
            this.ws = client.newWebSocketBuilder()
                    .buildAsync(uri, this)
                    .join();
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            openFuture.complete(true);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            if (onBinaryHandler != null) {
                onBinaryHandler.accept(ByteBuffer.wrap(bytes));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeFuture.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeFuture.complete(-1);
        }

        public void send(byte[] bytes) {
            ws.sendBinary(ByteBuffer.wrap(bytes), true).join();
        }

        public void sendPing() {
            ws.sendPing(ByteBuffer.allocate(0)).join();
        }

        public void close() {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
            } catch (Exception ignored) {}
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        WebSocketServerManager.getInstance().startServer(TEST_HOST, TEST_PORT);
        Thread.sleep(100);
    }

    @AfterEach
    public void tearDown() throws Exception {
        WebSocketServerManager.getInstance().stopServer();
        Thread.sleep(100);
    }

    @Test
    public void testWrongAuthTokenIsRejected() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        server.stopServer();
        YefiraConfig cfg = YefiraConfig.getInstance();
        String previous = cfg.getAuthToken();
        boolean previousBypass = cfg.isAllowLoopbackWithoutToken();
        cfg.setAllowLoopbackWithoutToken(false);
        cfg.setAuthToken("secret-token");

        TestClient client = null;
        try {
            Assertions.assertTrue(server.startServer(TEST_HOST, 18767));
            CompletableFuture<Boolean> rejected = new CompletableFuture<>();
            client = new TestClient(URI.create("ws://" + TEST_HOST + ":18767"), buf -> {
                buf.order(ByteOrder.LITTLE_ENDIAN);
                if (buf.remaining() >= 4) {
                    byte b0 = buf.get();
                    byte b1 = buf.get();
                    byte ver = buf.get();
                    byte op = buf.get();
                    if (b0 == 'M' && b1 == 'T' && ver == 3) {
                        if (op == com.mozi1924.yefira.encoder.BlockDataEncoder.OPCODE_ERROR) {
                            rejected.complete(true);
                        } else if (op == com.mozi1924.yefira.encoder.BlockDataEncoder.OPCODE_WELCOME) {
                            // Welcome decision check: header(4) + version(2) + uuid(16) + epoch(8) + seq(8) + min(12) + size(12) + hash(8)
                            if (buf.remaining() >= 66) {
                                buf.position(buf.position() + 66);
                                byte decision = buf.get();
                                if ((decision & 0xFF) == 0xFF) { // SyncDecision::Rejected
                                    rejected.complete(true);
                                }
                            }
                        }
                    }
                }
            });
            client.openFuture.get(5, TimeUnit.SECONDS);
            client.send(helloBytes("wrong-token"));

            try {
                rejected.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            client.closeFuture.get(5, TimeUnit.SECONDS);
            Thread.sleep(100);
            Assertions.assertEquals(0, server.getConnectedCount());
        } finally {
            if (client != null) {
                client.close();
            }
            cfg.setAuthToken(previous);
            cfg.setAllowLoopbackWithoutToken(previousBypass);
            server.stopServer();
        }
    }

    @Test
    public void testMatchingAuthTokenIsAccepted() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        server.stopServer();
        YefiraConfig cfg = YefiraConfig.getInstance();
        String previous = cfg.getAuthToken();
        boolean previousBypass = cfg.isAllowLoopbackWithoutToken();
        cfg.setAllowLoopbackWithoutToken(false);
        cfg.setAuthToken("secret-token");
        TestClient client = null;
        try {
            Assertions.assertTrue(server.startServer(TEST_HOST, 18768));
            client = new TestClient(URI.create("ws://" + TEST_HOST + ":18768"), null);
            client.openFuture.get(5, TimeUnit.SECONDS);
            client.send(helloBytes("  secret-token  "));
            Thread.sleep(200);
            Assertions.assertEquals(1, server.getConnectedCount(),
                    "A matching token must authenticate the DCC client");
        } finally {
            if (client != null) {
                client.close();
            }
            cfg.setAuthToken(previous);
            cfg.setAllowLoopbackWithoutToken(previousBypass);
            server.stopServer();
        }
    }

    @Test
    public void testLoopbackTokenBypassAcceptsEmptyToken() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        server.stopServer();
        YefiraConfig cfg = YefiraConfig.getInstance();
        String previous = cfg.getAuthToken();
        boolean previousBypass = cfg.isAllowLoopbackWithoutToken();
        cfg.setAuthToken("secret-token");
        cfg.setAllowLoopbackWithoutToken(true);
        TestClient client = null;
        try {
            Assertions.assertTrue(server.startServer(TEST_HOST, 18769));
            client = new TestClient(URI.create("ws://" + TEST_HOST + ":18769"), null);
            client.openFuture.get(5, TimeUnit.SECONDS);
            client.send(helloBytes(""));
            Thread.sleep(200);
            Assertions.assertEquals(1, server.getConnectedCount(),
                    "Loopback binds may opt out of token validation");
        } finally {
            if (client != null) {
                client.close();
            }
            cfg.setAuthToken(previous);
            cfg.setAllowLoopbackWithoutToken(previousBypass);
            server.stopServer();
        }
    }

    @Test
    public void testLoopbackBypassWorksWithWildcardBind() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        server.stopServer();
        YefiraConfig cfg = YefiraConfig.getInstance();
        String previous = cfg.getAuthToken();
        boolean previousBypass = cfg.isAllowLoopbackWithoutToken();
        cfg.setAuthToken("secret-token");
        cfg.setAllowLoopbackWithoutToken(true);
        TestClient client = null;
        try {
            Assertions.assertTrue(server.startServer("0.0.0.0", 18770));
            client = new TestClient(URI.create("ws://" + TEST_HOST + ":18770"), null);
            client.openFuture.get(5, TimeUnit.SECONDS);
            client.send(helloBytes(""));
            Thread.sleep(200);
            Assertions.assertEquals(1, server.getConnectedCount(),
                    "A loopback client must bypass the token even when the bind host is 0.0.0.0");
        } finally {
            if (client != null) {
                client.close();
            }
            cfg.setAuthToken(previous);
            cfg.setAllowLoopbackWithoutToken(previousBypass);
            server.stopServer();
        }
    }

    @Test
    public void testNonLoopbackWithoutTokenIsRejected() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        server.stopServer();
        YefiraConfig cfg = YefiraConfig.getInstance();
        String previous = cfg.getAuthToken();
        cfg.setAuthToken("");
        try {
            Assertions.assertFalse(server.startServer("0.0.0.0", 18766),
                    "A non-loopback bind must be refused without an auth token");
        } finally {
            cfg.setAuthToken(previous);
        }
    }

    @Test
    public void testServerLifecycleAndPingPong() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        Assertions.assertTrue(server.isRunning(), "Server should be running");
        Assertions.assertEquals(TEST_PORT, server.getPort());
        Assertions.assertEquals(TEST_HOST, server.getHost());

        CompletableFuture<Long> pongFuture = new CompletableFuture<>();

        TestClient client = new TestClient(URI.create("ws://" + TEST_HOST + ":" + TEST_PORT), bytes -> {
            bytes.order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.remaining() >= 12) {
                byte b0 = bytes.get();
                byte b1 = bytes.get();
                byte ver = bytes.get();
                byte op = bytes.get();
                if (b0 == 'M' && b1 == 'T' && ver == 3 && op == com.mozi1924.yefira.encoder.BlockDataEncoder.OPCODE_PONG) {
                    pongFuture.complete(bytes.getLong());
                }
            }
        });

        client.openFuture.get(5, TimeUnit.SECONDS);
        client.send(helloBytes(""));
        client.send(pingBytes(123456789L));

        Long response = pongFuture.get(5, TimeUnit.SECONDS);
        Assertions.assertEquals(123456789L, response);

        Thread.sleep(150);
        Assertions.assertTrue(server.getConnectedCount() >= 1, "Server should register HELLO-authenticated client");

        client.close();
    }

    @Test
    public void testProtocolPingAndDisconnectionResilience() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        TestClient client = new TestClient(URI.create("ws://" + TEST_HOST + ":" + TEST_PORT), null);
        client.openFuture.get(5, TimeUnit.SECONDS);

        // Send standard WebSocket ping frame
        client.sendPing();
        Thread.sleep(100);

        // Test safe send with null handles gracefully
        boolean sent = server.sendSafe(null, new byte[4]);
        Assertions.assertTrue(sent); // nativeServerBroadcastDelta handles valid bytes
        server.broadcastBaseline(null, null); // should safely no-op without crash

        // Abrupt close
        client.close();
        Thread.sleep(100);

        // Server should still be healthy and running
        Assertions.assertTrue(server.isRunning(), "Server should remain running and healthy after client disconnect");
    }

    @Test
    public void testUnknownOpcodeReturnsErrorAndDisconnects() throws Exception {
        WebSocketServerManager server = WebSocketServerManager.getInstance();
        CompletableFuture<Short> errorCodeFuture = new CompletableFuture<>();

        TestClient client = new TestClient(URI.create("ws://" + TEST_HOST + ":" + TEST_PORT), bytes -> {
            bytes.order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.remaining() >= 14) {
                byte b0 = bytes.get();
                byte b1 = bytes.get();
                byte ver = bytes.get();
                byte op = bytes.get();
                if (b0 == 'M' && b1 == 'T' && ver == 3 && op == com.mozi1924.yefira.encoder.BlockDataEncoder.OPCODE_ERROR) {
                    bytes.getLong(); // skip epoch
                    errorCodeFuture.complete(bytes.getShort());
                }
            }
        });

        client.openFuture.get(5, TimeUnit.SECONDS);
        // Send unknown opcode 0x99
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.MAGIC);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.PROTOCOL_VERSION);
        buf.put((byte) 0x99);
        client.send(buf.array());

        short err = errorCodeFuture.get(5, TimeUnit.SECONDS);
        Assertions.assertEquals(com.mozi1924.yefira.encoder.BlockDataEncoder.ERROR_UNKNOWN_OPCODE, err);
        client.close();
    }

    @Test
    public void testSyncConfigGatedBeforeAuth() throws Exception {
        TestClient client = new TestClient(URI.create("ws://" + TEST_HOST + ":" + TEST_PORT), null);
        client.openFuture.get(5, TimeUnit.SECONDS);

        // Send SYNC_CONFIG without HELLO
        ByteBuffer buf = ByteBuffer.allocate(4 + 3);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.MAGIC);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.PROTOCOL_VERSION);
        buf.put(com.mozi1924.yefira.encoder.BlockDataEncoder.OPCODE_SYNC_CONFIG);
        buf.put((byte) 2); // throttleMode
        buf.put((byte) 30); // fps
        buf.put((byte) 1); // active
        client.send(buf.array());

        Thread.sleep(150);

        // Client must NOT be authenticated
        Assertions.assertEquals(0, WebSocketServerManager.getInstance().getConnectedCount());
        client.close();
    }
}
