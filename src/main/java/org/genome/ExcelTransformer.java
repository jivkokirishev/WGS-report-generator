package org.genome;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.*;
import java.net.http.HttpClient;
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
        this.worksheetFiller = new WorksheetFiller(
                new ClinvarFetcher(HttpClient.newHttpClient(), new XmlMapper()),
                genesToPhenotypes);
        this.scanner = new Scanner(System.in);
    }


    public void run() throws IOException {
        String wgsFilePath = getWGSFilePath();
        String newFileLocation = getTransformedWGSFilePath();

        System.out.println("Data is processed. Please wait ...");
        long startTime = System.currentTimeMillis();

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

        long estimatedTime = System.currentTimeMillis() - startTime;
        System.out.println("The processing was successful. A new file was created.");
        System.out.printf("Total processing time: %f seconds. \r", estimatedTime / 1000.0);
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
        InputStream stream = ExcelTransformer.class.getResourceAsStream("/genes.csv");

        return new BufferedReader(new InputStreamReader(stream)).lines()
                .map(l -> l.split(","))
                .collect(Collectors.toMap(l -> l[0], l -> new Phenotype(l[2], l[1], l[3])));

    }
}
