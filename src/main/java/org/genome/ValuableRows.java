package org.genome;

import org.dhatim.fastexcel.reader.Row;

import java.util.List;

public record ValuableRows (List<Row> headerRows, List<Row> filteredRows) {
}
