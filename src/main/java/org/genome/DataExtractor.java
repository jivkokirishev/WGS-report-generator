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
                    .filter(this::filterBySequenceOntology)
                    .filter(this::filterByGnomadAltAlleleFreq)
                    .filter(this::filterByClinvarAndACMGClassification)
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
        Optional<String> variantAlleleFrequency = row.getCellAsString(4);

        if (variantAlleleFrequency
                .filter(s -> s.contains(","))
                .isPresent()) {
            System.out.printf("There is a problem with position %s and reading %s, having vaf: %s",
                    row.getCellText(0),
                    row.getCellText(1),
                    row.getCellText(4));
            return false;
        }

        return variantAlleleFrequency
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

    private boolean filterBySequenceOntology(Row row) {
        // 31 := AE column
        return row.getCellAsString(31)
                .map(String::toLowerCase)
                .filter(c -> c.contains("frameshift")
                        || c.contains("missense")
                        || c.contains("disruptive_inframe")
                        || c.contains("splice")
                        || c.contains("stop"))
                .isPresent();
    }

    private boolean filterByClinvarAndACMGClassification(Row row) {
        // 59 := BG column

        String clinvarClassification = row.getCellAsString(59)
                .map(String::toLowerCase)
                .orElse("");

        // 114 := DK column
        String acmgClassification = row.getCellAsString(114)
                .map(String::toLowerCase)
                .orElse("");

        // Ако в clinvar е vus -> не се докладва
        if (clinvarClassification.contains("vus")
                || clinvarClassification.contains("uncertain")) {
            return false;
        }

        // Ако в clinvar го няма, а в acmg е VUS -> не се докладва
        if (clinvarClassification.isEmpty()
                && acmgClassification.contains("vus")) {
            return false;
        }

        // Ако в clinvar го няма, а в acmg е pathogenic  -> докладва СЕ
        if (clinvarClassification.isEmpty()
                && (acmgClassification.contains("pathogenic")
                || acmgClassification.contains("conflicting"))) {
            return true;
        }

        // Ако в clinvar e патогенен, то най-вероятно и в acmg е pathogenic -> докладва СЕ без значение оценката в acmg
        if (clinvarClassification.contains("pathogenic")) {
            return true;
        }

        // Ако в clinvar е conflicting, гледаме следващата колона и ако там ИМА pathogenic,
        // гледаме acmg и ако там има Pathogenic (или conflicting),
        // тогава можем да докладваме
        if (clinvarClassification.contains("conflicting")) {
            String aggregatedSubmissions = row.getCellAsString(60)
                    .map(String::toLowerCase)
                    .orElse("");

            return aggregatedSubmissions.contains("pathogenic")
                    && (acmgClassification.contains("pathogenic")
                    || acmgClassification.contains("conflicting"));
        }

        return false;
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

        // TODO: I'm filtering by hemizygous count the XL (X Linked) variants, is this correct?
        if (phenotype != null
                && phenotype.inheritance().equals("XL")
                && row.getCellAsString(79)
                .map(Integer::parseInt)
                .filter(hemCount -> hemCount < 1)
                .isEmpty()) {
            return false;
        }

        return true;
    }

    private int columnToNumber(String column) {
        return column.toLowerCase().chars()
                .map(charValue -> charValue + 1 - 'a')
                .reduce(0, (result, charValue) -> result * 26 + charValue) - 1;
    }
}
