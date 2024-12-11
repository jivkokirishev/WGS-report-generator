package org.genome;

import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.Row;

import java.util.Map;

public class WorksheetFiller {
    private final Map<String, Phenotype> genesToPhenotypes;

    public WorksheetFiller(Map<String, Phenotype> genesToPhenotypes) {
        this.genesToPhenotypes = genesToPhenotypes;
    }

    public void fillWorksheet(Worksheet ws, ValuableRows rows) {
        // Insert header rows
        saveRowToWS(ws, 0, rows.headerRows().get(0));
        saveRowToWS(ws, 1, rows.headerRows().get(1));

        // Insert additional header rows
        ws.value(0, rows.headerRows().get(1).getCellCount(), "Additional information");

        ws.value(1, rows.headerRows().get(1).getCellCount(), "OMIM Codes");
        ws.value(1, rows.headerRows().get(1).getCellCount() + 1, "Phenotype");

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
