package com.gabryelle.agent;

import com.gabryelle.agent.gui.ConfigWindow;
import com.gabryelle.agent.server.LocalWebSocketServer;
import com.gabryelle.agent.service.PrinterService;
import com.gabryelle.agent.util.ConfigManager;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::startAgent);
    }

    private static void startAgent() {
        try {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException exception) {
                System.err.println("Não foi possível aplicar o tema do sistema: " + exception.getMessage());
            }

            ConfigManager configManager = new ConfigManager();
            PrinterService printerService = new PrinterService(configManager);
            AtomicBoolean serverRunning = new AtomicBoolean(false);
            AtomicReference<TrayIcon> trayIcon = new AtomicReference<>();
            ConfigWindow window = new ConfigWindow(configManager, printerService, serverRunning::get);

            LocalWebSocketServer server = new LocalWebSocketServer(configManager, printerService, running -> {
                serverRunning.set(running);
                SwingUtilities.invokeLater(() -> {
                    window.updateServerStatus();
                    updateTray(trayIcon.get(), running);
                });
            });

            if (!SystemTray.isSupported()) {
                window.setVisible(true);
                System.err.println("System Tray indisponível; a janela será mantida aberta.");
            } else {
                try {
                    trayIcon.set(createTrayIcon(window, server));
                    SystemTray.getSystemTray().add(trayIcon.get());
                } catch (AWTException exception) {
                    window.setVisible(true);
                    JOptionPane.showMessageDialog(window,
                            "Não foi possível adicionar o agente à bandeja do sistema:\n" + exception.getMessage(),
                            "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }

            server.start();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(null,
                    "Não foi possível iniciar o Gabryelle Print Agent:\n" + exception.getMessage(),
                    "Erro de inicialização", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static TrayIcon createTrayIcon(ConfigWindow window, LocalWebSocketServer server) {
        PopupMenu menu = new PopupMenu();
        AtomicReference<TrayIcon> iconReference = new AtomicReference<>();
        MenuItem openItem = new MenuItem("Configurações");
        openItem.addActionListener(event -> SwingUtilities.invokeLater(() -> {
            window.updateServerStatus();
            window.setVisible(true);
            window.setState(java.awt.Frame.NORMAL);
            window.toFront();
        }));
        MenuItem exitItem = new MenuItem("Encerrar");
        exitItem.addActionListener(event -> {
            try {
                server.stop(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                System.err.println("Interrompido ao encerrar o servidor: " + exception.getMessage());
            } finally {
                SystemTray.getSystemTray().remove(iconReference.get());
                System.exit(0);
            }
        });
        menu.add(openItem);
        menu.add(exitItem);

        TrayIcon icon = new TrayIcon(createStatusImage(false), "Gabryelle Print Agent", menu);
        iconReference.set(icon);
        icon.setImageAutoSize(true);
        icon.addActionListener(event -> SwingUtilities.invokeLater(() -> {
            window.updateServerStatus();
            window.setVisible(true);
            window.setState(java.awt.Frame.NORMAL);
            window.toFront();
        }));
        return icon;
    }

    private static void updateTray(TrayIcon icon, boolean running) {
        if (icon != null) {
            icon.setImage(createStatusImage(running));
            icon.setToolTip(running
                    ? "Gabryelle Print Agent — conectado/ativo"
                    : "Gabryelle Print Agent — erro ou inativo");
        }
    }

    private static BufferedImage createStatusImage(boolean running) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillOval(0, 0, 15, 15);
        graphics.setColor(running ? new Color(34, 160, 76) : new Color(210, 55, 55));
        graphics.fillOval(2, 2, 12, 12);
        graphics.setColor(Color.DARK_GRAY);
        graphics.drawOval(1, 1, 13, 13);
        graphics.dispose();
        return image;
    }
}
