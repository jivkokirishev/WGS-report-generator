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
        // G := 6 column
        return row.getCellAsNumber(columnToNumber("G"))
                .filter(rd -> rd.longValue() > 20)
                .isPresent();
    }

    // TODO: Figure out how to filter when having more than one value?
    // TODO: Do I need to know the difference between homozygous and heterozygous for the filtering part?
    private boolean filterByVariantAlleleFrequency(Row row) {
        // E := 4 column
        Optional<String> variantAlleleFrequency = row.getCellAsString(columnToNumber("E"));

        if (variantAlleleFrequency
                .filter(s -> s.contains(","))
                .isPresent()) {
            System.out.printf("There is a problem with position %s and reading %s, having vaf: %s%n",
                    row.getCellText(columnToNumber("A")),
                    row.getCellText(columnToNumber("B")),
                    row.getCellText(columnToNumber("E")));
            return false;
        }

        return variantAlleleFrequency
                .map(s -> s.split(","))
                .filter(s -> Arrays.stream(s).count() == 1)
                .map(s -> s[0])
//                .filter(s -> !s.contains(","))
                .map(Float::parseFloat)
                .filter(vaf -> vaf > 0.25)
                .isPresent();
    }

    private boolean filterByGnomadAltAlleleFreq(Row row) {
        // BZ := 77 column
        return row.getCellAsString(columnToNumber("BZ"))
                .map(Double::parseDouble)
                .filter(aaf -> aaf < 0.05)
                .isPresent();
    }

    private boolean filterBySequenceOntology(Row row) {
        // AE := 30 column
        return row.getCellAsString(columnToNumber("AE"))
                .map(String::toLowerCase)
                .filter(c -> c.contains("frameshift")
                        || c.contains("missense")
                        || c.contains("disruptive_inframe")
                        || c.contains("splice")
                        || c.contains("stop"))
                .isPresent();
    }

    private boolean filterByClinvarAndACMGClassification(Row row) {
        // BG := 58 column
        String clinvarClassification = row.getCellAsString(columnToNumber("BG"))
                .map(String::toLowerCase)
                .orElse("");

        // DK:= 113 column
        String acmgClassification = row.getCellAsString(columnToNumber("DK"))
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

        // Ако в clinvar го няма, а в acmg е pathogenic -> докладва СЕ
        if (clinvarClassification.isEmpty()
                && (acmgClassification.contains("pathogenic")
                || acmgClassification.contains("conflicting"))) {
            return true;
        }

        // Ако в clinvar е conflicting, гледаме следващата колона и ако там ИМА pathogenic,
        // гледаме acmg и ако там има Pathogenic (или conflicting),
        // тогава можем да докладваме
        // BH := 59 column
        if (clinvarClassification.contains("conflicting")) {
            String aggregatedSubmissions = row.getCellAsString(columnToNumber("BH"))
                    .map(String::toLowerCase)
                    .orElse("");

            return aggregatedSubmissions.contains("pathogenic")
                    && (acmgClassification.contains("pathogenic")
                    || acmgClassification.contains("conflicting"));
        }

        // Ако в clinvar e патогенен, то най-вероятно и в acmg е pathogenic -> докладва СЕ без значение оценката в acmg
        if (clinvarClassification.contains("pathogenic")) {
            return true;
        }

        return false;
    }

    private boolean filterByGenePanel(Row row) {
        // AD := 29 column
        return row.getCellAsString(columnToNumber("AD"))
                .map(g -> g.split(","))
                .filter(gs -> Arrays.stream(gs).anyMatch(genesToPhenotypes::containsKey))
                .isPresent();
    }

    private boolean filterByGnomadZigosityDepeningOnInheritance(Row row) {
        // AD := 29 column
        Phenotype phenotype = row.getCellAsString(columnToNumber("AD"))
                .flatMap(g -> Arrays.stream(g.split(",")).findFirst())
                .map(genesToPhenotypes::get)
                .orElse(null);

        // CA := 78 column
        if (phenotype != null
                && (phenotype.inheritance().equals("AR") || phenotype.inheritance().equals("SD"))
                && row.getCellAsString(columnToNumber("CA"))
                .map(Integer::parseInt)
                .filter(homCount -> homCount < 5)
                .isEmpty()) {
            return false;
        }

        // CA := 78 column
        if (phenotype != null
                && phenotype.inheritance().equals("AD")
                && row.getCellAsString(columnToNumber("CA"))
                .map(Integer::parseInt)
                .filter(homCount -> homCount < 1)
                .isEmpty()) {
            return false;
        }

        // CB := 79 column
        if (phenotype != null
                && phenotype.inheritance().equals("XL")
                && row.getCellAsString(columnToNumber("CB"))
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
