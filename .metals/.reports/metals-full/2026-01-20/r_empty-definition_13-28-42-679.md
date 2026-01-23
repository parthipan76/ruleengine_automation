error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/config/SupabaseService.java:_empty_/ConfigurationManager#
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/config/SupabaseService.java
empty definition using pc, found symbol in pc: _empty_/ConfigurationManager#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 573
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/config/SupabaseService.java
text:
```scala
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
    private final HttpClient httpClient;
    private final int timeoutSeconds;

    public SupabaseService() {
        ConfigurationMan@@ager config = ConfigurationManager.getInstance();
        this.projectUrl = config.getSupabaseProjectUrl();
        this.anonKey = config.getSupabaseAnonKey();
        this.timeoutSeconds = config.getSupabaseTimeoutSeconds();

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        if (this.projectUrl == null || this.projectUrl.isEmpty() || this.projectUrl.contains("YOUR_PROJECT_ID")) {
            logger.warn(
                    "Supabase Configuration is missing or default. Please configure supabase.project.url and supabase.anon.key in config.xml");
        }
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

        String endpoint = String.format("%s/rest/v1/documents?id=eq.%s&select=content", projectUrl, documentId);
        logger.info("Fetching document {} from Supabase: {}", documentId, endpoint);

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

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/ConfigurationManager#