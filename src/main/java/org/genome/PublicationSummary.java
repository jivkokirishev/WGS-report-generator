package org.genome;


import java.time.LocalDate;

public record PublicationSummary(
        LocalDate dateUpdated,
        String submitter,
        String submittedAssembly,
        String classification,
        String summary
) {
}
