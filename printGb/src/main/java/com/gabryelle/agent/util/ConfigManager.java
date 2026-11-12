package com.gabryelle.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ConfigManager {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path configPath = Path.of(System.getProperty("user.home"),
            ".gabryelle-print-agent", "config.json");
    private AgentSettings settings;

    public ConfigManager() throws IOException {
        if (Files.exists(configPath)) {
            settings = objectMapper.readValue(configPath.toFile(), AgentSettings.class);
            validateSettings(settings);
        } else {
            settings = AgentSettings.createDefault();
            save(settings);
        }
    }

    public synchronized AgentSettings getSettings() {
        return settings.copy();
    }

    public synchronized void updateSettings(AgentSettings updated) throws IOException {
        validateSettings(updated);
        AgentSettings replacement = updated.copy();
        save(replacement);
        settings = replacement;
    }

    public Path getConfigPath() {
        return configPath;
    }

    private void save(AgentSettings settingsToSave) throws IOException {
        Files.createDirectories(configPath.getParent());
        Path temporaryPath = Files.createTempFile(configPath.getParent(), "config-", ".json.tmp");
        try {
            objectMapper.writeValue(temporaryPath.toFile(), settingsToSave);
            try {
                Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveNotSupported) {
                Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void validateSettings(AgentSettings candidate) throws IOException {
        if (candidate == null) {
            throw new IOException("O arquivo de configuração não contém configurações válidas.");
        }
        if (candidate.getPairingToken() == null || candidate.getPairingToken().length() < 32) {
            throw new IOException("O token de pareamento está ausente ou é muito curto.");
        }
        if (candidate.getTrustedOrigins() == null) {
            candidate.setTrustedOrigins(new ArrayList<>());
        }
        candidate.setTrustedOrigins(candidate.getTrustedOrigins().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(ConfigManager::normalizeOrigin)
                .distinct()
                .toList());
    }

    public static String normalizeOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Informe uma origem HTTP(S), sem caminho, consulta ou credenciais.");
            }
            int port = uri.getPort();
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String authority = port == -1 ? host : host + ":" + port;
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + authority;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Origem inválida: " + origin, exception);
        }
    }

    public static final class AgentSettings {
        private String kitchenPrinter = "";
        private String cashierPrinter = "";
        private List<String> trustedOrigins = new ArrayList<>();
        private String pairingToken;

        public AgentSettings() {
        }

        private static AgentSettings createDefault() {
            AgentSettings defaults = new AgentSettings();
            defaults.setTrustedOrigins(List.of(
                    "https://lucassantana-eng.github.io",
                    "http://localhost:3000",
                    "http://localhost:5173",
                    "http://127.0.0.1:5500",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:5173"));
            defaults.setPairingToken(generatePairingToken());
            return defaults;
        }

        private static String generatePairingToken() {
            byte[] tokenBytes = new byte[32];
            new SecureRandom().nextBytes(tokenBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        }

        private AgentSettings copy() {
            AgentSettings copy = new AgentSettings();
            copy.kitchenPrinter = kitchenPrinter;
            copy.cashierPrinter = cashierPrinter;
            copy.trustedOrigins = trustedOrigins == null
                    ? new ArrayList<>() : new ArrayList<>(trustedOrigins);
            copy.pairingToken = pairingToken;
            return copy;
        }

        public String getKitchenPrinter() {
            return kitchenPrinter;
        }

        public void setKitchenPrinter(String kitchenPrinter) {
            this.kitchenPrinter = kitchenPrinter == null ? "" : kitchenPrinter;
        }

        public String getCashierPrinter() {
            return cashierPrinter;
        }

        public void setCashierPrinter(String cashierPrinter) {
            this.cashierPrinter = cashierPrinter == null ? "" : cashierPrinter;
        }

        public List<String> getTrustedOrigins() {
            return trustedOrigins;
        }

        public void setTrustedOrigins(List<String> trustedOrigins) {
            this.trustedOrigins = trustedOrigins;
        }

        public String getPairingToken() {
            return pairingToken;
        }

        public void setPairingToken(String pairingToken) {
            this.pairingToken = pairingToken;
        }
    }
}
