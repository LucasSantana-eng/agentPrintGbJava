package com.gabryelle.agent.service;

import com.gabryelle.agent.util.ConfigManager;
import com.gabryelle.agent.util.ConfigManager.AgentSettings;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

public final class PrinterService {
    private static final Charset PRINTER_CHARSET = Charset.forName("IBM860");
    private static final int MAX_IMAGE_BASE64_LENGTH = 440 * 1024;
    private static final int MAX_IMAGE_BYTES = 320 * 1024;
    private static final int MAX_IMAGE_WIDTH = 576;
    private static final int MAX_IMAGE_HEIGHT = 10_000;
    private final ConfigManager configManager;

    public PrinterService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<String> getInstalledPrinters() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public void print(String sector, String text, String alignment, boolean bold, boolean cut)
            throws PrintException, IOException {
        AgentSettings settings = configManager.getSettings();
        String printerName = switch (sector) {
            case "kitchen" -> settings.getKitchenPrinter();
            case "cashier" -> settings.getCashierPrinter();
            default -> throw new IllegalArgumentException("Setor inválido. Use 'kitchen' ou 'cashier'.");
        };
        printToPrinter(printerName, text, alignment, bold, cut);
    }

    public void printToPrinter(String printerName, String text, String alignment, boolean bold, boolean cut)
            throws PrintException, IOException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("O conteúdo da impressão não pode estar vazio.");
        }
        if (text.length() > 16_000) {
            throw new IllegalArgumentException("O conteúdo da impressão excede o limite de 16000 caracteres.");
        }
        validateText(text);

        if (printerName == null || printerName.isBlank()) {
            throw new IllegalStateException("Nenhuma impressora foi selecionada.");
        }

        PrintService selectedPrinter = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(printer -> printer.getName().equals(printerName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "A impressora configurada não está instalada ou disponível: " + printerName));

        byte[] bytes = createEscPosDocument(text, alignment, bold, cut);
        DocPrintJob job = selectedPrinter.createPrintJob();
        Doc document = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        job.print(document, null);
    }

    public void printImageToPrinter(String printerName, String imageBase64, boolean cut)
            throws PrintException, IOException {
        if (printerName == null || printerName.isBlank()) {
            throw new IllegalStateException("Nenhuma impressora foi selecionada.");
        }

        PrintService selectedPrinter = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(printer -> printer.getName().equals(printerName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "A impressora configurada não está instalada ou disponível: " + printerName));

        byte[] bytes = createEscPosImageDocument(imageBase64, cut);
        DocPrintJob job = selectedPrinter.createPrintJob();
        Doc document = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        job.print(document, null);
    }

    static byte[] createEscPosDocument(String text, String alignment, boolean bold, boolean cut)
            throws IOException {
        int alignmentCode = switch (alignment == null ? "left" : alignment.toLowerCase()) {
            case "center" -> 1;
            case "right" -> 2;
            case "left" -> 0;
            default -> throw new IllegalArgumentException("Alinhamento inválido. Use left, center ou right.");
        };

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[]{0x1B, 0x40});
        output.write(new byte[]{0x1B, 0x74, 0x03});
        output.write(new byte[]{0x1B, 0x61, (byte) alignmentCode});
        output.write(new byte[]{0x1B, 0x45, (byte) (bold ? 1 : 0)});
        output.write(text.getBytes(PRINTER_CHARSET));
        if (!text.endsWith("\n")) {
            output.write('\n');
        }
        output.write(new byte[]{0x1B, 0x45, 0});
        output.write(new byte[]{0x1B, 0x61, 0});
        if (cut) {
            output.write(new byte[]{0x1D, 0x56, 0x01});
        }
        return output.toByteArray();
    }

    static byte[] createEscPosImageDocument(String imageBase64, boolean cut) throws IOException {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("A imagem do recibo não pode estar vazia.");
        }
        if (imageBase64.length() > MAX_IMAGE_BASE64_LENGTH) {
            throw new IllegalArgumentException("A imagem do recibo excede o limite permitido.");
        }

        byte[] encodedImage;
        try {
            encodedImage = Base64.getDecoder().decode(imageBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("A imagem do recibo não está em Base64 válido.", exception);
        }
        if (encodedImage.length == 0 || encodedImage.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("A imagem do recibo está vazia ou excede o limite permitido.");
        }

        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encodedImage))) {
            if (input == null) {
                throw new IllegalArgumentException("Não foi possível ler a imagem do recibo.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("O recibo precisa estar no formato PNG.");
            }
            ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IllegalArgumentException("O recibo precisa estar no formato PNG.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || width > MAX_IMAGE_WIDTH || height < 1 || height > MAX_IMAGE_HEIGHT) {
                    throw new IllegalArgumentException("As dimensões do recibo excedem o limite permitido.");
                }
                image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("Não foi possível decodificar a imagem do recibo.");
                }
            } finally {
                reader.dispose();
            }
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int widthBytes = (width + 7) / 8;
        ByteArrayOutputStream output = new ByteArrayOutputStream(11 + widthBytes * height);
        output.write(new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x01});
        output.write(new byte[]{
                0x1D, 0x76, 0x30, 0x00,
                (byte) (widthBytes & 0xFF), (byte) ((widthBytes >> 8) & 0xFF),
                (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF)
        });
        for (int y = 0; y < height; y++) {
            for (int xByte = 0; xByte < widthBytes; xByte++) {
                int packedPixels = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xByte * 8 + bit;
                    if (x < width) {
                        int pixel = image.getRGB(x, y);
                        int alpha = (pixel >>> 24) & 0xFF;
                        int red = (pixel >>> 16) & 0xFF;
                        int green = (pixel >>> 8) & 0xFF;
                        int blue = pixel & 0xFF;
                        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
                        luminance = (luminance * alpha + 255 * (255 - alpha)) / 255;
                        if (luminance < 160) packedPixels |= 0x80 >> bit;
                    }
                }
                output.write(packedPixels);
            }
        }
        image.flush();
        output.write('\n');
        if (cut) {
            output.write(new byte[]{0x1D, 0x56, 0x01});
        }
        return output.toByteArray();
    }

    private static void validateText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if ((character < 0x20 && character != '\n' && character != '\r' && character != '\t')
                    || character == 0x7F) {
                throw new IllegalArgumentException("O texto contém caracteres de controle não permitidos.");
            }
        }
    }
}
