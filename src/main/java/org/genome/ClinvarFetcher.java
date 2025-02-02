package org.genome;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClinvarFetcher {
    private final HttpClient client;
    private final XmlMapper mapper;

    public ClinvarFetcher(HttpClient client, XmlMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public VariantSummary fetchVariantSummary(String clinvarVariantId) throws IOException, InterruptedException {
        HttpRequest request = getVariantRequest(clinvarVariantId);
        HttpResponse<String> clinvarResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

        return getRCVNumbers(mapper.readTree(clinvarResponse.body())).stream()
                .map(rcvNumber -> {
                    try {
                        return getVariantSummaryForRcvNumber(rcvNumber);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .reduce((vs1, vs2) -> {
                    vs1.summaries().addAll(vs2.summaries());
                    return new VariantSummary(
                        vs1.deceaseDefinition() == null || vs1.deceaseDefinition().isBlank()
                                ? vs1.deceaseDefinition()
                                : vs2.deceaseDefinition(),
                            vs1.summaries());})
                .orElse(new VariantSummary(null, Collections.emptyList()));
    }

    private VariantSummary getVariantSummaryForRcvNumber(String rcvNumber) throws IOException, InterruptedException {
        HttpRequest submissionsRequest = getSubmissionsRequest(rcvNumber);
        HttpResponse<String> submissionsResponse =
                client.send(submissionsRequest, HttpResponse.BodyHandlers.ofString());

        return constructVariantSummary(mapper.readTree(submissionsResponse.body()));
    }

    private List<String> getRCVNumbers(JsonNode responseTree) throws IOException {
        ArrayNode rcvNumbers = responseTree
                .path("DocumentSummarySet")
                .path("DocumentSummary")
                .path("supporting_submissions")
                .path("rcv")
                .withArrayProperty("string");

        ObjectReader reader = mapper.readerFor(new TypeReference<List<String>>() {});
        return reader.readValue(rcvNumbers);
    }

    private HttpRequest getVariantRequest(String clinvarVariantId) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=clinvar&id=" + clinvarVariantId))
                .GET()
                .build();
    }

    private HttpRequest getSubmissionsRequest(String rcvNumber) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=clinvar&rettype=clinvarset&id=" + rcvNumber))
                .GET()
                .build();
    }

    private VariantSummary constructVariantSummary(JsonNode responseTree) {
        String deceaseDefinition = getDeceaseDefinition(responseTree);

        List<PublicationSummary> publicationSummaries = new ArrayList<>();
        responseTree.path("ClinVarSet")
                .path("ClinVarAssertion")
                .forEach(publication -> {
                    PublicationSummary publicationSummary = new PublicationSummary(
                            getDateUpdated(publication),
                            getSubmitter(publication),
                            getSubmittedAssembly(publication),
                            getClassification(publication),
                            getSummary(publication));

                    publicationSummaries.add(publicationSummary);
                });

        return new VariantSummary(deceaseDefinition, publicationSummaries);
    }

    private String getDeceaseDefinition(JsonNode responseTree) {
        return responseTree
                .path("ClinVarSet")
                .path("ReferenceClinVarAssertion")
                .path("TraitSet")
                .path("Trait")
                .path(0)
                .path("AttributeSet")
                .path("Attribute")
                .path(0)
                .asText();
    }

    private String getSubmitter(JsonNode publication) {
        return publication
                .path("ClinVarSubmissionID")
                .path("submitter")
                .asText();
    }

    private String getSubmittedAssembly(JsonNode publication) {
        return publication
                .path("ClinVarSubmissionID")
                .path("submittedAssembly")
                .asText();
    }

    private LocalDate getDateUpdated(JsonNode publication) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateUpdated = publication
                .path("ClinVarAccession")
                .path("DateUpdated")
                .asText();

        try {
            return LocalDate.parse(dateUpdated, dateFormatter);
        } catch (Exception e) {
            return LocalDate.MIN;
        }
    }

    private String getClassification(JsonNode publication) {
        return publication
                .path("Classification")
                .path("GermlineClassification")
                .asText();
    }

    private String getSummary(JsonNode publication) {
        return publication
                .path("Classification")
                .path("Comment")
                .asText();
    }
}
