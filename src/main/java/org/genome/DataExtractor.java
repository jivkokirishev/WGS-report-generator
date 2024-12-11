package org.genome;

import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class DataExtractor {
    private final Map<String, Phenotype> genesToPhenotypes;

    public DataExtractor(Map<String, Phenotype> genesToPhenotypes) {
        this.genesToPhenotypes = genesToPhenotypes;
    }

    public ValuableRows filterWorkSheet(Sheet sheet) throws IOException {
        final List<Row> headerRows = new ArrayList<>(2);
        final List<Row> filteredRows = new ArrayList<>();

        try (Stream<Row> rows = sheet.openStream()) {
            Iterator<Row> rowIterator = rows.iterator();
            headerRows.add(rowIterator.next());
            headerRows.add(rowIterator.next());
            rowIterator.forEachRemaining(r -> Optional.of(r)
                    .filter(this::filterBySufficientReadDepth)
                    .filter(this::filterByVariantAlleleFrequency)
                    .filter(this::filterByGnomadAltAlleleFreq)
                    .filter(this::filterByACMGClassification)
                    .filter(this::filterByGenePanel)
                    .filter(this::filterByGnomadZigosityDepeningOnInheritance)
                    .ifPresent(filteredRows::add));
        }
        return new ValuableRows(headerRows, filteredRows);
    }


    private boolean filterBySufficientReadDepth(Row row) {
        return row.getCellAsNumber(6).filter(rd -> rd.longValue() > 20).isPresent();
    }

    // TODO: Figure out how to filter when having more than one value?
    // TODO: Do I need to know the difference between homozygous and heterozygous for the filtering part?
    private boolean filterByVariantAlleleFrequency(Row row) {
        return row.getCellAsString(4)
                .filter(s -> !s.contains(","))
                .map(Float::parseFloat)
                .filter(vaf -> vaf > 0.25)
                .isPresent();
    }

    private boolean filterByGnomadAltAlleleFreq(Row row) {
        return row.getCellAsString(77)
                .map(Double::parseDouble)
                .filter(aaf -> aaf < 0.05)
                .isPresent();
    }

    private boolean filterByACMGClassification(Row row) {
        // 114 := DK column
        return row.getCellAsString(114)
                .map(String::toLowerCase)
                .filter(c -> c.contains("conflicting") || c.contains("pathogenic"))
                .isPresent();
    }

    private boolean filterByGenePanel(Row row) {
        return row.getCellAsString(29)
                .filter(genesToPhenotypes::containsKey)
                .isPresent();
    }

    private boolean filterByGnomadZigosityDepeningOnInheritance(Row row) {
        Phenotype phenotype = row.getCellAsString(29)
                .map(genesToPhenotypes::get)
                .orElse(null);

        // TODO: How should I handle SD (Semi-Dominant) variants?
        if (phenotype != null
                && (phenotype.inheritance().equals("AR") || phenotype.inheritance().equals("SD"))
                && row.getCellAsString(78)
                .map(Integer::parseInt)
                .filter(homCount -> homCount < 5)
                .isEmpty()) {
            return false;
        }

        if (phenotype != null
                && phenotype.inheritance().equals("AD")
                && row.getCellAsString(78)
                .map(Integer::parseInt)
                .filter(homCount -> homCount < 1)
                .isEmpty()) {
            return false;
        }

        // TODO: I'm filtering by hemizygous count the XL (X Linked) variants, it this correct?
        if (phenotype != null
                && phenotype.inheritance().equals("XL")
                && row.getCellAsString(79)
                .map(Integer::parseInt)
                .filter(hemCount -> hemCount < 5)
                .isEmpty()) {
            return false;
        }

        return true;
    }
}
