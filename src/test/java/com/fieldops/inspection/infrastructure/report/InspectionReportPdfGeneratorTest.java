package com.fieldops.inspection.infrastructure.report;

import com.fieldops.inspection.application.InspectionReportData;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionReportPdfGeneratorTest {

    @Test
    void generatesReadablePdfWithResizedEvidenceImage() throws Exception {
        InspectionReportData.Evidence evidence = new InspectionReportData.Evidence(
                "PHOTO", "Painel do equipamento", Instant.parse("2026-11-20T10:00:00Z"),
                Instant.parse("2026-11-20T10:01:00Z"), image(3000, 2000)
        );
        InspectionReportData report = new InspectionReportData(
                UUID.randomUUID(), "Inspecao do gerador", "Executar com o equipamento isolado", "APPROVED", "HIGH",
                Instant.parse("2026-11-20T09:00:00Z"), "Cliente A", "Planta Sul", "Rua A, 10",
                "Gerador GD-001", "Tecnico A", "Supervisora A", Instant.parse("2026-11-20T09:10:00Z"),
                Instant.parse("2026-11-20T09:11:00Z"), Instant.parse("2026-11-20T09:45:00Z"),
                Instant.parse("2026-11-20T09:46:00Z"), "-23.5, -47.4 (precisao 5 m)", "Nao informado",
                List.of(new InspectionReportData.Item(
                        "Seguranca", "Itens de seguranca", 1, "SEG-001", "Lacre intacto?",
                        "Verifique o lacre.", null, true, "CONFORMING", "Sem avarias", List.of(evidence)
                )), List.of(), null, List.of()
        );

        byte[] pdf = new InspectionReportPdfGenerator().generate(report);

        assertThat(pdf).startsWith("%PDF".getBytes());
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Inspecao do gerador", "Lacre intacto?", "CONFORMING");
        }
    }

    private byte[] image(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
