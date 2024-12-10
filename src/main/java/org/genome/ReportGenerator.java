package org.genome;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReportGenerator {
    private static final Map<String, Phenotype> geneToPhenotype = loadGenesToPhenotypes();

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter WGS file path:");
        String WGSFilePath = scanner.nextLine();
        File initialFile = new File(WGSFilePath);
        final List<Row> headerRows = new ArrayList<>(2);
        final List<Row> filteredRows = new ArrayList<>();
        try (InputStream is = new FileInputStream(initialFile); ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            try (Stream<Row> rows = sheet.openStream()) {
                Iterator<Row> rowIterator = rows.iterator();
                headerRows.add(rowIterator.next());
                headerRows.add(rowIterator.next());
                rowIterator.forEachRemaining(r -> Optional.of(r)
                        .filter(ReportGenerator::filterBySufficientReadDepth)
                        .filter(ReportGenerator::filterByVariantAlleleFrequency)
                        .filter(ReportGenerator::filterByGnomadAltAlleleFreq)
                        .filter(ReportGenerator::filterByACMGClassification)
                        .filter(ReportGenerator::filterByGenePanel)
                        .filter(ReportGenerator::filterByGnomadZigosityDepeningOnInheritance)
                        .ifPresent(filteredRows::add));
            }
        }

        try (OutputStream os = new FileOutputStream("WGS Filtered.xlsx");
             Workbook wb = new Workbook(os, "WGS Filtered", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet 1");

            // Insert header rows
            saveRowToWS(ws, 0, headerRows.get(0));
            saveRowToWS(ws, 1, headerRows.get(1));

            // Append filtered rows
            for (int i = 2; i < filteredRows.size(); i++) {
                saveRowToWS(ws, i, filteredRows.get(i));
                // Include additional information about phenotypes and OMIM codes
            }
        }
    }

    private static boolean filterBySufficientReadDepth(Row row) {
        return row.getCellAsNumber(6).filter(rd -> rd.longValue() > 20).isPresent();
    }

    // TODO: Figure out how to filter when having more than one value?
    // TODO: Do I need to know the difference between homozygous and heterozygous for the filtering part?
    private static boolean filterByVariantAlleleFrequency(Row row) {
        return row.getCellAsString(4)
                .filter(s -> !s.contains(","))
                .map(Float::parseFloat)
                .filter(vaf -> vaf > 0.25)
                .isPresent();
    }

    private static boolean filterByGnomadAltAlleleFreq(Row row) {
        return row.getCellAsString(77)
                .map(Double::parseDouble)
                .filter(aaf -> aaf < 0.05)
                .isPresent();
    }

    private static boolean filterByACMGClassification(Row row) {
        // 114 := DK column
        return row.getCellAsString(114)
                .map(String::toLowerCase)
                .filter(c -> c.contains("conflicting") || c.contains("pathogenic"))
                .isPresent();
    }

    private static boolean filterByGenePanel(Row row) {
        return row.getCellAsString(29)
                .filter(geneToPhenotype::containsKey)
                .isPresent();
    }

    private static boolean filterByGnomadZigosityDepeningOnInheritance(Row row) {
        Phenotype phenotype = row.getCellAsString(29)
                .map(geneToPhenotype::get)
                .orElse(null);

        // TODO: How should I handle SD (Semi-Dominant) variants?
        if (phenotype != null
                && (phenotype.inheritance.equals("AR") || phenotype.inheritance.equals("SD"))
                && row.getCellAsString(78)
                .map(Integer::parseInt)
                .filter(homCount -> homCount < 5)
                .isEmpty()) {
            return false;
        }

        if (phenotype != null
                && phenotype.inheritance.equals("AD")
                && row.getCellAsString(78)
                .map(Integer::parseInt)
                .filter(homCount -> homCount < 1)
                .isEmpty()) {
            return false;
        }

        // TODO: I'm filtering by hemizygous count the XL (X Linked) variants, it this correct?
        if (phenotype != null
                && phenotype.inheritance.equals("XL")
                && row.getCellAsString(79)
                .map(Integer::parseInt)
                .filter(hemCount -> hemCount < 5)
                .isEmpty()) {
            return false;
        }

        return true;
    }

    private static Map<String, Phenotype> loadGenesToPhenotypes() {
        try {
            return Files.lines(Path.of("./src/main/resources/genes.txt"))
                    .map(l -> l.split(","))
                    .collect(Collectors.toMap(l -> l[0], l -> new Phenotype(l[2], l[1], l[3])));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void saveRowToWS(Worksheet worksheet, int rowNum, Row row) {
        for (int i = 0; i < row.getCellCount(); i++) {
            saveCellToWS(worksheet, rowNum, i, row.getCell(i));
        }
    }

    private static void saveCellToWS(Worksheet worksheet, int rowNum, int colNum, Cell cell) {
        if (cell == null) {
            return;
        }

        switch (cell.getType()) {
            case STRING -> worksheet.value(rowNum, colNum, cell.asString());
            case NUMBER -> worksheet.value(rowNum, colNum, cell.asNumber());
            case BOOLEAN -> worksheet.value(rowNum, colNum, cell.asBoolean());
        }
    }

    private record Phenotype(String name, String inheritance, String mimCodes) {}
}
