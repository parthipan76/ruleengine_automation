package com.sixdee.text2rule.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupabaseService {
    private static final Logger logger = LoggerFactory.getLogger(SupabaseService.class);

    private final String projectUrl;
    private final String anonKey;
    private final String projectName;
    private final String defaultDocumentId;
    private final HttpClient httpClient;
    private final int timeoutSeconds;

    public SupabaseService() {
        ConfigurationManager config = ConfigurationManager.getInstance();
        this.projectUrl = config.getSupabaseProjectUrl();
        this.anonKey = config.getSupabaseAnonKey();
        this.projectName = config.getSupabaseProjectName();
        this.defaultDocumentId = config.getSupabaseDocument();
        this.timeoutSeconds = config.getSupabaseTimeoutSeconds();

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        if (this.projectUrl == null || this.projectUrl.isEmpty() || this.projectUrl.contains("YOUR_PROJECT_ID")) {
            logger.warn(
                    "Supabase Configuration is missing or default. Please configure supabase.project.url and supabase.anon.key in config.xml");
        } else {
            logger.info("SupabaseService initialized for project: {} (Document: {})", projectName, defaultDocumentId);
        }
    }

    /**
     * Fetch document content using the default configured document ID.
     */
    public String fetchDocument() {
        if (defaultDocumentId == null || defaultDocumentId.isEmpty()) {
            logger.warn("No default document ID configured in config.xml");
            return "Error: No default document configured";
        }
        return fetchDocument(defaultDocumentId);
    }

    /**
     * Fetch document content from Supabase 'documents' table.
     * Assumes table 'documents' exists with columns 'id' and 'content'.
     */
    public String fetchDocument(String documentId) {
        if (projectUrl.isEmpty() || anonKey.isEmpty()) {
            logger.warn("Supabase credentials not configured. Returning error.");
            return "Error: Supabase not configured";
        }

        // Handle case where documentId is "document5" but ID is 5 (BigInt)
        String queryId = documentId;
        boolean isNumeric = documentId.matches("\\d+");

        if (!isNumeric) {
            // Try explicit "documentX" pattern
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)document(\\d+)").matcher(documentId);
            if (m.find()) {
                queryId = m.group(1);
                logger.info("Parsed numeric ID {} from document identifier '{}'", queryId, documentId);
            } else {
                // If it's not a number and not "documentN", assume specific integer match
                // failed.
                // We could try metadata search here, but let's default to trying ID first or
                // warn.
                // For now, if we can't parse a number, we'll try to use it as-is, which might
                // fail
                // if default column is ID (bigint).
                logger.warn("Document ID '{}' is not numeric. Supabase ID column requires BigInt.", documentId);
            }
        }

        String endpoint = String.format("%s/rest/v1/documents?id=eq.%s&select=content", projectUrl, queryId);
        logger.info("Fetching document {} (queryVal={}) from Supabase: {}", documentId, queryId, endpoint);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // Basic JSON parsing to extract content (assuming simple structure)
                // In a real app, use Jackson/Gson
                logger.debug("Supabase response: {}", body);

                if (body.equals("[]")) {
                    logger.warn("Document {} not found in Supabase", documentId);
                    return "Error: Document not found";
                }

                // Return raw JSON body or extracted content if needed
                return body;
            } else {
                logger.error("Failed to fetch document. Status: {}, Body: {}", response.statusCode(), response.body());
                return "Error: HTTP " + response.statusCode();
            }
        } catch (Exception e) {
            logger.error("Exception fetching document from Supabase", e);
            return "Error: " + e.getMessage();
        }
    }

    public String matchKpis(String segments, String prompt) {
        // Placeholder for KPI matching implementation
        logger.info("Matching KPIs logic to be implemented. Segments: {}", segments);
        return "Matched KPIs functionality pending implementation";
    }
}
