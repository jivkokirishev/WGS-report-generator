package org.genome;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.*;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class ExcelTransformer {
    private final DataExtractor dataExtractor;
    private final WorksheetFiller worksheetFiller;
    private final Scanner scanner;


    public ExcelTransformer() {
        Map<String, Phenotype> genesToPhenotypes = loadGenesToPhenotypes();
        this.dataExtractor = new DataExtractor(genesToPhenotypes);
        this.worksheetFiller = new WorksheetFiller(genesToPhenotypes);
        this.scanner = new Scanner(System.in);
    }


    public void run() throws IOException {
        String wgsFilePath = getWGSFilePath();
        String newFileLocation = getTransformedWGSFilePath();

        ValuableRows rows;
        try (InputStream is = new FileInputStream(wgsFilePath); ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            rows = dataExtractor.filterWorkSheet(sheet);
        }

        try (OutputStream os = new FileOutputStream(newFileLocation);
             Workbook wb = new Workbook(os, "WGS Transformed", "1.0")) {
            Worksheet ws = wb.newWorksheet("Sheet 1");

            worksheetFiller.fillWorksheet(ws, rows);
        }
    }

    private String getWGSFilePath() {
        System.out.println("Enter WGS excel file path:");
        return scanner.nextLine();
    }

    private String getTransformedWGSFilePath() {
        System.out.println("Enter the file path where the new excel file should be saved:");
        return scanner.nextLine();
    }

    private Map<String, Phenotype> loadGenesToPhenotypes() {
        try {
            InputStream stream = ExcelTransformer.class.getClassLoader().getResource("genes.csv").openStream();

            return new BufferedReader(new InputStreamReader(stream)).lines()
                    .map(l -> l.split(","))
                    .collect(Collectors.toMap(l -> l[0], l -> new Phenotype(l[2], l[1], l[3])));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
