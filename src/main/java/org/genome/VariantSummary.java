package org.genome;

import java.util.List;

public record VariantSummary(
        String deceaseDefinition,
        List<PublicationSummary> summaries) {
}
