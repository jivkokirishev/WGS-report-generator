package org.genome;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hello world!
 */
public class ReportGenerator {
    private static Set<String> genes = loadGenes();

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter WGS file path:");
        String WGSFilePath = scanner.nextLine();
        File initialFile = new File(WGSFilePath);
        try (InputStream is = new FileInputStream(initialFile); ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            try (Stream<Row> rows = sheet.openStream()) {
                System.out.println(rows.skip(2).filter(ReportGenerator::filterRow)
                        .count());
            }
        }
    }

    private static boolean filterRow(Row row) {
        boolean sufficientReadDepth = row.getCellAsNumber(6).filter(rd -> rd.longValue() > 20).isPresent();
        if (!sufficientReadDepth) {
            return false;
        }

        // Figure out how to filter when having more than one value
        float variantAlleleFrequency = row.getCellAsString(4).filter(s -> !s.contains(",")).map(Float::parseFloat).orElse(0.0f);
        if (variantAlleleFrequency < 0.75) {
            return false;
        }

        if (row.getCellAsString(77).map(Double::parseDouble).filter(aaf -> aaf >= 0.05).isPresent()) {
            return false;
        }

        if (row.getCellAsString(114).filter(c -> {
            String classification = c.toLowerCase();
            return classification.contains("conflicting") || classification.contains("pathogenic");
        }).isEmpty()) {
            return false;
        }

        if (row.getCellAsString(29).filter(genes::contains).isEmpty()) {
            return false;
        }

        return true;
    }

    private static Set<String> loadGenes() {
        try {
            return new HashSet<>(Files.readAllLines(Path.of("./src/main/resources/genes.txt")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
