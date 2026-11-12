package com.gabryelle.agent.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabryelle.agent.service.PrinterService;
import com.gabryelle.agent.util.ConfigManager;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class LocalWebSocketServer extends WebSocketServer {
    public static final int PORT = 38182;
    private static final int MAX_MESSAGE_SIZE = 448 * 1024;
    private final ConfigManager configManager;
    private final PrinterService printerService;
    private final Consumer<Boolean> statusListener;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocket> authenticatedConnections = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor printExecutor = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20), runnable -> {
        Thread thread = new Thread(runnable, "gabryelle-print-worker");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    public LocalWebSocketServer(ConfigManager configManager, PrinterService printerService,
                                Consumer<Boolean> statusListener) throws Exception {
        super(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT),
                1, List.of(new Draft_6455(List.of(), MAX_MESSAGE_SIZE)));
        this.configManager = configManager;
        this.printerService = printerService;
        this.statusListener = statusListener;
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        String origin = handshake.getFieldValue("Origin");
        String host = handshake.getFieldValue("Host");
        boolean originAllowed = configManager.getSettings().getTrustedOrigins().stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(origin));
        if (!originAllowed || !isExpectedLocalHost(host)
                || !"/ws".equals(connection.getResourceDescriptor())) {
            connection.close(1008, "Origem, host ou caminho não autorizado");
            return;
        }
        sendMessage(connection, "{\"type\":\"ready\",\"authenticationRequired\":true}");
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        authenticatedConnections.remove(connection);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        if (message == null || message.length() > MAX_MESSAGE_SIZE) {
            sendError(connection, "Mensagem inválida ou acima do limite.");
            connection.close(1009, "Mensagem muito grande");
            return;
        }

        try {
            JsonNode request = objectMapper.readTree(message);
            String type = request.path("type").asText("");
            if ("authenticate".equals(type)) {
                authenticate(connection, request.path("token").asText(""));
                return;
            }
            if (!authenticatedConnections.contains(connection)) {
                sendError(connection, "Autenticação necessária.");
                connection.close(1008, "Autenticação necessária");
                return;
            }
            if ("list-printers".equals(type)) {
                sendPrinters(connection, request.path("requestId").asText(""));
            } else if ("print".equals(type)) {
                enqueuePrint(connection, request);
            } else {
                sendError(connection, "Tipo de comando não suportado.");
            }
        } catch (Exception exception) {
            sendError(connection, "JSON inválido: " + exception.getMessage());
        }
    }

    @Override
    public void onMessage(WebSocket connection, java.nio.ByteBuffer message) {
        sendError(connection, "Somente mensagens JSON em texto são aceitas.");
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        System.err.println("Erro no servidor WebSocket: " + exception.getMessage());
        if (connection == null) {
            statusListener.accept(false);
        }
    }

    @Override
    public void onStart() {
        System.out.println("Servidor local ativo em ws://127.0.0.1:" + PORT + "/ws");
        statusListener.accept(true);
    }

    @Override
    public void stop(int timeout) throws InterruptedException {
        try {
            super.stop(timeout);
        } finally {
            printExecutor.shutdown();
            statusListener.accept(false);
        }
    }

    private void authenticate(WebSocket connection, String token) {
        byte[] suppliedToken = token.getBytes(StandardCharsets.UTF_8);
        byte[] configuredToken = configManager.getSettings().getPairingToken().getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(suppliedToken, configuredToken)) {
            authenticatedConnections.add(connection);
            sendMessage(connection, "{\"type\":\"authenticated\"}");
        } else {
            sendError(connection, "Token de pareamento inválido.");
            connection.close(1008, "Token inválido");
        }
    }

    private void enqueuePrint(WebSocket connection, JsonNode request) {
        String requestId = request.path("requestId").asText("");
        try {
            String printerName = request.path("printerName").asText("");
            String imageBase64 = request.path("imageBase64").asText(null);
            boolean cut = request.path("cut").asBoolean(false);
            String validationError = validateRasterPrintRequest(printerName, imageBase64);
            if (validationError != null) {
                sendMessage(connection, jsonError(validationError, requestId));
                return;
            }
            printExecutor.execute(() -> {
                try {
                    printerService.printImageToPrinter(printerName, imageBase64, cut);
                    var result = objectMapper.createObjectNode()
                            .put("type", "print-result")
                            .put("success", true);
                    if (!requestId.isBlank()) result.put("requestId", requestId);
                    sendMessage(connection, result.toString());
                } catch (Exception exception) {
                    sendMessage(connection, jsonError("Falha ao imprimir: " + exception.getMessage(), requestId));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            sendMessage(connection, jsonError("Fila de impressão ocupada; tente novamente.", requestId));
        }
    }

    static String validateRasterPrintRequest(String printerName, String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return "A impressão precisa ser enviada como imagem PNG para manter a formatação.";
        }
        if (printerName == null || printerName.isBlank()) {
            return "Selecione uma impressora para imprimir o recibo formatado.";
        }
        return null;
    }

    private void sendPrinters(WebSocket connection, String requestId) {
        var result = objectMapper.createObjectNode()
                .put("type", "printers-result")
                .put("requestId", requestId);
        result.set("printers", objectMapper.valueToTree(printerService.getInstalledPrinters()));
        sendMessage(connection, result.toString());
    }

    private boolean isExpectedLocalHost(String host) {
        return ("127.0.0.1:" + PORT).equalsIgnoreCase(host)
                || ("localhost:" + PORT).equalsIgnoreCase(host);
    }

    private void sendError(WebSocket connection, String message) {
        sendMessage(connection, jsonError(message));
    }

    private String jsonError(String message) {
        return jsonError(message, "");
    }

    private String jsonError(String message, String requestId) {
        var error = objectMapper.createObjectNode()
                .put("type", "error")
                .put("message", message);
        if (requestId != null && !requestId.isBlank()) error.put("requestId", requestId);
        return error.toString();
    }

    private void sendMessage(WebSocket connection, String message) {
        if (connection != null && connection.isOpen()) {
            connection.send(message);
        }
    }
}
