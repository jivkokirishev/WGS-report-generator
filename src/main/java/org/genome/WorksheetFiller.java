package org.genome;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.Row;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.Map;

public class WorksheetFiller {
    private final ClinvarFetcher clinvarFetcher;
    private final Map<String, Phenotype> genesToPhenotypes;

    public WorksheetFiller(ClinvarFetcher clinvarFetcher, Map<String, Phenotype> genesToPhenotypes) {
        this.clinvarFetcher = clinvarFetcher;
        this.genesToPhenotypes = genesToPhenotypes;
    }

    public void fillWorksheet(Worksheet ws, ValuableRows rows) {
        // Insert header rows
        saveRowToWS(ws, 0, rows.headerRows().get(0));
        saveRowToWS(ws, 1, rows.headerRows().get(1));

        // Insert additional header rows
        ws.value(0, rows.headerRows().get(1).getCellCount(), "Additional Information");
        ws.value(0, rows.headerRows().get(1).getCellCount() + 3, "Publication Summary");

        ws.value(1, rows.headerRows().get(1).getCellCount(), "OMIM Codes");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 1, "Phenotype");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 2, "Decease Definition");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 3, "Submitted Classification");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 4, "Submitted Date Updated");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 5, "Submitter");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 6, "Submitted Assembly");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 7, "Variant Summary");

        // Append filtered rows
        for (int i = 2; i < rows.filteredRows().size(); i++) {
            saveRowToWS(ws, i, rows.filteredRows().get(i));
            // Include additional information about phenotypes and OMIM codes
            Phenotype phenotype = rows.filteredRows().get(i).getCellAsString(29)
                    .map(genesToPhenotypes::get)
                    .orElse(null);
            if (phenotype != null) {
                ws.value(i, rows.headerRows().get(1).getCellCount(), phenotype.mimCodes());
                ws.value(i, rows.headerRows().get(1).getCellCount() + 1, phenotype.name());

                final int rowIndex = i;
                rows.filteredRows().get(i).getCellAsString(57)
                        .map(clinvarVariantId -> {
                            try {
                                return clinvarFetcher.fetchVariantSummary(clinvarVariantId);
                            } catch (IOException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .ifPresent(vs -> {
                            ws.value(rowIndex, rows.headerRows().get(1).getCellCount() + 2, vs.deceaseDefinition());

                            vs.summaries().stream()
                                    .filter(s -> s.classification().equals("pathogenic"))
                                    .filter(s -> !s.summary().isBlank())
                                    .max(Comparator.comparing(PublicationSummary::dateUpdated))
                                    .ifPresent(s -> {
                                        ws.value(rowIndex, rows.headerRows().get(1).getCellCount() + 3, s.classification());
                                        ws.value(rowIndex, rows.headerRows().get(1).getCellCount() + 4, s.dateUpdated());
                                        ws.value(rowIndex, rows.headerRows().get(1).getCellCount() + 5, s.submitter());
                                        ws.value(rowIndex, rows.headerRows().get(1).getCellCount() + 6, s.submittedAssembly());
                                        ws.value(rowIndex, rows.headerRows().get(1).getCellCount() + 7, s.summary());
                                    });
                        });
            }
        }
    }

    private void saveRowToWS(Worksheet worksheet, int rowNum, Row row) {
        for (int i = 0; i < row.getCellCount(); i++) {
            saveCellToWS(worksheet, rowNum, i, row.getCell(i));
        }
    }

    private void saveCellToWS(Worksheet worksheet, int rowNum, int colNum, Cell cell) {
        if (cell == null) {
            return;
        }

        switch (cell.getType()) {
            case STRING -> worksheet.value(rowNum, colNum, cell.asString());
            case NUMBER -> worksheet.value(rowNum, colNum, cell.asNumber());
            case BOOLEAN -> worksheet.value(rowNum, colNum, cell.asBoolean());
        }
    }
}
