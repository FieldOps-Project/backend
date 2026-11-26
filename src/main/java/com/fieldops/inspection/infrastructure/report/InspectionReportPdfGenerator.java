package com.fieldops.inspection.infrastructure.report;

import com.fieldops.inspection.application.InspectionReportData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Renders a bounded, readable PDF while keeping evidence images small. */
@Component
public class InspectionReportPdfGenerator {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 42;
    private static final float CONTENT_WIDTH = PAGE.getWidth() - (MARGIN * 2);
    private static final float MAX_IMAGE_PIXELS = 1400;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] generate(InspectionReportData report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Writer writer = new Writer(document);
            writer.heading("Relatorio de Inspecao");
            writer.paragraph("ID: " + report.inspectionId());
            writer.paragraph("Gerado em: " + DATE_TIME.format(Instant.now()));

            writer.section("Identificacao");
            writer.field("Titulo", report.title());
            writer.field("Instrucoes", report.instructions());
            writer.field("Status", report.status());
            writer.field("Prioridade", report.priority());
            writer.field("Agendada para", format(report.scheduledFor()));
            writer.field("Cliente", report.clientName());
            writer.field("Local", report.siteName());
            writer.field("Endereco", report.siteAddress());
            writer.field("Equipamento", report.equipmentName());
            writer.field("Tecnico", report.technicianName());
            writer.field("Supervisor", report.supervisorName());

            writer.section("Horarios e localizacao");
            writer.field("Inicio no dispositivo", format(report.startedAtDevice()));
            writer.field("Inicio no servidor", format(report.startedAtServer()));
            writer.field("Conclusao no dispositivo", format(report.completedAtDevice()));
            writer.field("Envio ao servidor", format(report.submittedAtServer()));
            writer.field("Localizacao inicial", report.startLocation());
            writer.field("Localizacao final", report.completionLocation());

            writer.section("Checklist (snapshot da inspecao)");
            if (report.items().isEmpty()) {
                writer.paragraph("Nenhum item foi registrado no snapshot.");
            }
            String lastSection = null;
            for (InspectionReportData.Item item : report.items()) {
                if (!item.sectionTitle().equals(lastSection)) {
                    writer.subheading("Secao " + item.sectionOrder() + ": " + item.sectionTitle());
                    writer.paragraph(item.sectionDescription());
                    lastSection = item.sectionTitle();
                }
                writer.item(item);
                for (InspectionReportData.Evidence evidence : item.evidence()) {
                    writer.evidence(evidence);
                }
            }

            writer.section("Nao conformidades");
            if (report.nonConformities().isEmpty()) {
                writer.paragraph("Nenhuma nao conformidade registrada.");
            }
            for (InspectionReportData.NonConformity nonConformity : report.nonConformities()) {
                writer.subheading(nonConformity.title());
                writer.field("Criticidade", nonConformity.severity());
                writer.field("Status", nonConformity.status());
                writer.paragraph(nonConformity.description());
                for (InspectionReportData.Evidence evidence : nonConformity.evidence()) {
                    writer.evidence(evidence);
                }
            }

            writer.section("Decisao da revisao");
            if (report.review() == null) {
                writer.paragraph("A inspecao ainda nao possui decisao de revisao.");
            } else {
                writer.field("Decisao", report.review().decision());
                writer.field("Revisor", report.review().reviewerName());
                writer.field("Ciclo", Integer.toString(report.review().reviewCycle()));
                writer.field("Data", format(report.review().reviewedAt()));
                writer.field("Motivo", report.review().reason());
                writer.field("Comentarios", report.review().comments());
                if (!report.review().itemsToCorrect().isEmpty()) {
                    writer.field("Itens para correcao", report.review().itemsToCorrect().toString());
                }
            }

            if (!report.unlinkedEvidence().isEmpty()) {
                writer.section("Evidencias adicionais");
                for (InspectionReportData.Evidence evidence : report.unlinkedEvidence()) {
                    writer.evidence(evidence);
                }
            }
            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate inspection report", exception);
        }
    }

    private String format(Instant value) {
        return value == null ? "Nao informado" : DATE_TIME.format(value);
    }

    private final class Writer {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        private Writer(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void heading(String text) throws IOException {
            text(text, bold, 18, 24, new Color(24, 55, 85));
        }

        private void section(String text) throws IOException {
            ensure(28);
            y -= 8;
            text(text, bold, 13, 18, new Color(24, 55, 85));
        }

        private void subheading(String text) throws IOException {
            ensure(20);
            text(text, bold, 10, 14, Color.DARK_GRAY);
        }

        private void field(String label, String value) throws IOException {
            paragraph(label + ": " + (value == null || value.isBlank() ? "Nao informado" : value));
        }

        private void paragraph(String value) throws IOException {
            for (String line : wrap(value == null || value.isBlank() ? "Nao informado" : value, regular, 9, CONTENT_WIDTH)) {
                ensure(14);
                text(line, regular, 9, 12, Color.BLACK);
            }
            y -= 2;
        }

        private void item(InspectionReportData.Item item) throws IOException {
            ensure(50);
            String code = item.code() == null || item.code().isBlank() ? "" : item.code() + " - ";
            subheading(code + item.title() + (item.required() ? " (obrigatorio)" : ""));
            field("Tipo", item.responseType() == null ? null : item.responseType().name());
            field("Resposta", item.response());
            field("Observacao", item.observation());
            if (item.description() != null && !item.description().isBlank()) {
                field("Descricao do item", item.description());
            }
        }

        private void evidence(InspectionReportData.Evidence evidence) throws IOException {
            field("Evidencia", evidence.type() + (evidence.description() == null ? "" : " - " + evidence.description()));
            field("Capturada no dispositivo", format(evidence.capturedAtDevice()));
            field("Recebida no servidor", format(evidence.serverReceivedAt()));
            if (evidence.image() == null) {
                paragraph("Imagem indisponivel no armazenamento.");
                return;
            }
            try {
                addImage(resize(evidence.image()));
            } catch (IOException exception) {
                paragraph("Imagem nao pode ser incorporada ao relatorio.");
            }
        }

        private void addImage(byte[] imageBytes) throws IOException {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "inspection-evidence");
            float width = Math.min(CONTENT_WIDTH, image.getWidth());
            float height = width * image.getHeight() / image.getWidth();
            if (height > 230) {
                height = 230;
                width = height * image.getWidth() / image.getHeight();
            }
            ensure(height + 12);
            content.drawImage(image, MARGIN, y - height, width, height);
            y -= height + 10;
        }

        private byte[] resize(byte[] sourceBytes) throws IOException {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (source == null) {
                throw new IOException("Unsupported image");
            }
            double scale = Math.min(1d, Math.min(MAX_IMAGE_PIXELS / source.getWidth(), MAX_IMAGE_PIXELS / source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(source, 0, 0, width, height, Color.WHITE, null);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", output);
            return output.toByteArray();
        }

        private void text(String value, PDFont font, float size, float lineHeight, Color color) throws IOException {
            ensure(lineHeight);
            content.beginText();
            content.setFont(font, size);
            content.setNonStrokingColor(color);
            content.newLineAtOffset(MARGIN, y);
            content.showText(safe(value));
            content.endText();
            y -= lineHeight;
        }

        private List<String> wrap(String value, PDFont font, float size, float width) throws IOException {
            String normalized = safe(value).replace('\n', ' ');
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : normalized.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size <= width || current.isEmpty()) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("Nao informado") : lines;
        }

        private String safe(String value) throws IOException {
            if (value == null) {
                return "Nao informado";
            }
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                String character = String.valueOf(value.charAt(index));
                try {
                    regular.encode(character);
                    result.append(character);
                } catch (IllegalArgumentException exception) {
                    result.append('?');
                }
            }
            return result.toString();
        }

        private void ensure(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            page = new PDPage(PAGE);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE.getHeight() - MARGIN;
        }

        private void close() throws IOException {
            content.close();
        }
    }
}
