package com.gabryelle.agent.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrinterServiceTest {
    @Test
    void createsEscPosDocumentWithAlignmentBoldTextAndCut() throws Exception {
        byte[] bytes = PrinterService.createEscPosDocument("Olá", "center", true, true);
        byte[] expectedPrefix = {
                0x1B, 0x40,
                0x1B, 0x74, 0x03,
                0x1B, 0x61, 0x01,
                0x1B, 0x45, 0x01
        };
        byte[] expectedText = "Olá\n".getBytes(Charset.forName("IBM860"));
        byte[] expectedSuffix = {
                0x1B, 0x45, 0x00,
                0x1B, 0x61, 0x00,
                0x1D, 0x56, 0x01
        };

        assertArrayEquals(expectedPrefix, Arrays.copyOfRange(bytes, 0, expectedPrefix.length));
        assertArrayEquals(expectedText, Arrays.copyOfRange(bytes,
                expectedPrefix.length, expectedPrefix.length + expectedText.length));
        assertArrayEquals(expectedSuffix, Arrays.copyOfRange(bytes, bytes.length - expectedSuffix.length, bytes.length));
    }

    @Test
    void rejectsUnsupportedAlignment() {
        assertThrows(IllegalArgumentException.class,
                () -> PrinterService.createEscPosDocument("teste", "justify", false, false));
    }

    @Test
    void encodesPortugueseCharactersUsingCp860() throws Exception {
        byte[] bytes = PrinterService.createEscPosDocument("Ação", "left", false, false);
        byte[] expected = "Ação\n".getBytes(Charset.forName("IBM860"));
        assertTrue(indexOf(bytes, expected) >= 0);
    }

    @Test
    void createsCenteredEscPosRasterFromReceiptPng() throws Exception {
        BufferedImage image = new BufferedImage(8, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, Color.WHITE.getRGB());
        }
        image.setRGB(0, 0, Color.BLACK.getRGB());
        image.setRGB(7, 1, Color.BLACK.getRGB());
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        String imageBase64 = Base64.getEncoder().encodeToString(png.toByteArray());

        byte[] bytes = PrinterService.createEscPosImageDocument(imageBase64, true);
        byte[] expected = {
                0x1B, 0x40, 0x1B, 0x61, 0x01,
                0x1D, 0x76, 0x30, 0x00, 0x01, 0x00, 0x02, 0x00,
                (byte) 0x80, 0x01,
                0x0A, 0x1D, 0x56, 0x01
        };

        assertArrayEquals(expected, bytes);
    }

    @Test
    void rejectsUnsupportedOrOversizedReceiptImages() {
        assertThrows(IllegalArgumentException.class,
                () -> PrinterService.createEscPosImageDocument("not-base64", false));
        assertThrows(IllegalArgumentException.class,
                () -> PrinterService.createEscPosImageDocument(
                        Base64.getEncoder().encodeToString("not a png".getBytes(Charset.defaultCharset())), false));
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int i = 0; i <= source.length - target.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(source, i, i + target.length), target)) {
                return i;
            }
        }
        return -1;
    }
}
