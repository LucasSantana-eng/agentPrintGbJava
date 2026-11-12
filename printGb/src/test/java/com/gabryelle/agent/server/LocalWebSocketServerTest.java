package com.gabryelle.agent.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalWebSocketServerTest {
    @Test
    void rejectsLegacyTextPrintsThatWouldLoseReceiptFormatting() {
        assertEquals(
                "A impressão precisa ser enviada como imagem PNG para manter a formatação.",
                LocalWebSocketServer.validateRasterPrintRequest("Impressora", null));
        assertEquals(
                "A impressão precisa ser enviada como imagem PNG para manter a formatação.",
                LocalWebSocketServer.validateRasterPrintRequest("Impressora", " "));
    }

    @Test
    void requiresASelectedPrinterForRasterReceipts() {
        assertEquals(
                "Selecione uma impressora para imprimir o recibo formatado.",
                LocalWebSocketServer.validateRasterPrintRequest("", "iVBORw0KGgo="));
    }

    @Test
    void acceptsFormattedReceiptsWithASelectedPrinter() {
        assertNull(LocalWebSocketServer.validateRasterPrintRequest("Impressora", "iVBORw0KGgo="));
    }
}
