package com.gabryelle.agent.gui;

import com.gabryelle.agent.service.PrinterService;
import com.gabryelle.agent.util.ConfigManager;
import com.gabryelle.agent.util.ConfigManager.AgentSettings;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ConfigWindow extends JFrame {
    private static final String NO_PRINTER = "(Não selecionada)";
    private final ConfigManager configManager;
    private final PrinterService printerService;
    private final BooleanSupplier serverIsRunning;
    private final JComboBox<String> kitchenPrinter = new JComboBox<>();
    private final JComboBox<String> cashierPrinter = new JComboBox<>();
    private final JTextArea originsArea = new JTextArea(8, 48);
    private final JTextField tokenField = new JTextField();
    private final JLabel serverStatus = new JLabel();

    public ConfigWindow(ConfigManager configManager, PrinterService printerService,
                        BooleanSupplier serverIsRunning) {
        super("Gabryelle Print Agent");
        this.configManager = configManager;
        this.printerService = printerService;
        this.serverIsRunning = serverIsRunning;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(620, 420));
        setSize(720, 500);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Impressoras detectadas", createPrintersTab());
        tabs.addTab("Mapeamento de setores", createSectorsTab());
        tabs.addTab("Segurança / domínios", createSecurityTab());

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        JButton saveButton = new JButton("Salvar configurações");
        saveButton.addActionListener(event -> saveSettings());
        serverStatus.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        footer.add(serverStatus, BorderLayout.CENTER);
        footer.add(saveButton, BorderLayout.EAST);

        add(tabs, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        loadSettings();
        updateServerStatus();
    }

    public void updateServerStatus() {
        boolean running = serverIsRunning.getAsBoolean();
        serverStatus.setText(running
                ? "Servidor local ativo em 127.0.0.1:38182"
                : "Servidor local indisponível");
    }

    private JPanel createPrintersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> printerList = new JList<>(model);
        printerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        printerService.getInstalledPrinters().forEach(model::addElement);
        JButton refreshButton = new JButton("Atualizar lista");
        refreshButton.addActionListener(event -> {
            model.clear();
            printerService.getInstalledPrinters().forEach(model::addElement);
            loadPrinterChoices();
        });
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Impressoras instaladas no Windows"), BorderLayout.WEST);
        header.add(refreshButton, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(printerList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSectorsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Impressora da cozinha:"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(kitchenPrinter, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        panel.add(new JLabel("Impressora do caixa:"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(cashierPrinter, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        panel.add(new JLabel("As impressoras precisam estar instaladas no Windows."), constraints);
        return panel;
    }

    private JPanel createSecurityTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        panel.add(new JLabel("Origens web autorizadas (uma origem HTTP(S) por linha, sem caminho):"),
                constraints);
        constraints.gridy = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(originsArea), constraints);
        constraints.gridy = 2;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("Token de pareamento — configure-o no front-end; não compartilhe publicamente:"),
                constraints);
        constraints.gridy = 3;
        JPanel tokenPanel = new JPanel(new BorderLayout(8, 0));
        tokenField.setEditable(false);
        JButton copyButton = new JButton("Copiar");
        copyButton.addActionListener(event -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(tokenField.getText()), null);
            JOptionPane.showMessageDialog(this, "Token copiado para a área de transferência.");
        });
        tokenPanel.add(tokenField, BorderLayout.CENTER);
        tokenPanel.add(copyButton, BorderLayout.EAST);
        panel.add(tokenPanel, constraints);
        return panel;
    }

    private void loadSettings() {
        AgentSettings settings = configManager.getSettings();
        originsArea.setText(String.join(System.lineSeparator(), settings.getTrustedOrigins()));
        tokenField.setText(settings.getPairingToken());
        loadPrinterChoices();
        selectPrinter(kitchenPrinter, settings.getKitchenPrinter());
        selectPrinter(cashierPrinter, settings.getCashierPrinter());
    }

    private void loadPrinterChoices() {
        List<String> printers = printerService.getInstalledPrinters();
        replaceChoices(kitchenPrinter, printers);
        replaceChoices(cashierPrinter, printers);
    }

    private void replaceChoices(JComboBox<String> combo, List<String> printers) {
        String selection = (String) combo.getSelectedItem();
        combo.removeAllItems();
        combo.addItem(NO_PRINTER);
        printers.forEach(combo::addItem);
        if (selection != null) {
            combo.setSelectedItem(selection);
        }
    }

    private void selectPrinter(JComboBox<String> combo, String printerName) {
        combo.setSelectedItem(printerName == null || printerName.isBlank() ? NO_PRINTER : printerName);
    }

    private void saveSettings() {
        try {
            AgentSettings updated = configManager.getSettings();
            updated.setKitchenPrinter(selectedPrinter(kitchenPrinter));
            updated.setCashierPrinter(selectedPrinter(cashierPrinter));
            List<String> origins = new ArrayList<>();
            for (String line : originsArea.getText().split("\\R")) {
                if (!line.isBlank()) {
                    origins.add(ConfigManager.normalizeOrigin(line.trim()));
                }
            }
            updated.setTrustedOrigins(origins);
            configManager.updateSettings(updated);
            JOptionPane.showMessageDialog(this,
                    "Configurações salvas em:\n" + configManager.getConfigPath(),
                    "Configurações", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException | IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Não foi possível salvar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String selectedPrinter(JComboBox<String> combo) {
        String value = (String) combo.getSelectedItem();
        return value == null || NO_PRINTER.equals(value) ? "" : value;
    }
}
