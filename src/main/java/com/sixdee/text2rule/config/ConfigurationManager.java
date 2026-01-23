package com.sixdee.text2rule.config;

import com.sixdee.text2rule.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton Configuration Manager following best practices.
 * Thread-safe, centralized configuration management.
 * Supports environment variables and XML-based prompts.
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);

    private static volatile ConfigurationManager instance;
    private static final Object lock = new Object();

    private final PromptRegistry promptRegistry;
    private final Map<String, Object> configCache;

    // Configuration keys
    private static final String GROQ_API_KEY_ENV = "GROQ_API_KEY";
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String DEFAULT_MODEL_NAME = "llama-3.3-70b-versatile";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final double DEFAULT_CONSISTENCY_THRESHOLD = 0.80;
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * Private constructor to prevent instantiation.
     */
    private ConfigurationManager() {
        try {
            this.promptRegistry = PromptRegistry.getInstance();
            this.configCache = new ConcurrentHashMap<>();
            loadConfiguration();
            logger.info("ConfigurationManager initialized successfully [cached_keys={}]", configCache.size());
        } catch (Exception e) {
            logger.error("Failed to initialize ConfigurationManager [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Configuration initialization failed", e);
        }
    }
    
    
    /**
     * Check if DSL renderer is enabled.
     */
    public boolean isDslRendererEnabled() {
        return (Boolean) configCache.getOrDefault("renderer.dsl.enabled", false);
    }

    

    /**
     * Double-checked locking Singleton pattern.
     * Thread-safe lazy initialization.
     */
    public static ConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ConfigurationManager();
                }
            }
        }
        return instance;
    }

    /**
     * Load configuration from config.xml and environment variables.
     */
    private void loadConfiguration() {
        try {
            // Load from config.xml first
            loadFromConfigXml();

            logger.info("Configuration loaded [provider={}, model={}, timeout={}s]",
                    getActiveProvider(),
                    configCache.get("model.name"),
                    configCache.get("timeout.seconds"));
        } catch (Exception e) {
            logger.error("Error loading configuration [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to load configuration", e);
        }
    }

    /**
     * Load configuration from config.xml file.
     */
    private void loadFromConfigXml() {
        try (java.io.InputStream input = getClass().getClassLoader().getResourceAsStream("config.xml")) {
            if (input == null) {
                logger.warn("config.xml not found, using defaults");
                setDefaults();
                return;
            }

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(input);
            doc.getDocumentElement().normalize();

            // Load API configuration from <api> section
            loadApiConfig(doc);

            // Load rendering configuration from <rendering> section
            // Load rendering configuration from <rendering> section
            loadRenderingConfig(doc);

            // Load Supabase configuration from <supabase> section
            loadSupabaseConfig(doc);

            // Load Observability configuration from <observability> section
            loadObservabilityConfig(doc);

            // Load Rate Limiting configuration from <ratelimiting> section
            loadRateLimitConfig(doc);

        } catch (Exception e) {
            logger.error("Failed to parse config.xml [error={}]", e.getMessage(), e);
            setDefaults();
        }
    }

    /**
     * Load Supabase configuration from XML document.
     */
    private void loadSupabaseConfig(org.w3c.dom.Document doc) {
        try {
            org.w3c.dom.NodeList supabaseNodes = doc.getElementsByTagName("supabase");
            if (supabaseNodes.getLength() > 0) {
                org.w3c.dom.Element supabaseElement = (org.w3c.dom.Element) supabaseNodes.item(0);

                // Project Details
                org.w3c.dom.NodeList projectNodes = supabaseElement.getElementsByTagName("project");
                if (projectNodes.getLength() > 0) {
                    org.w3c.dom.Element projectElement = (org.w3c.dom.Element) projectNodes.item(0);
                    configCache.put("supabase.project.name", getElementText(projectElement, "name", ""));
                    configCache.put("supabase.project.url", getElementText(projectElement, "url", ""));
                    configCache.put("supabase.anon.key", getElementText(projectElement, "anonKey", ""));
                    configCache.put("supabase.document", getElementText(projectElement, "document", ""));
                }

                // Auth Details
                org.w3c.dom.NodeList authNodes = supabaseElement.getElementsByTagName("auth");
                if (authNodes.getLength() > 0) {
                    org.w3c.dom.Element authElement = (org.w3c.dom.Element) authNodes.item(0);
                    configCache.put("supabase.email", getElementText(authElement, "email", ""));
                    configCache.put("supabase.password", getElementText(authElement, "password", ""));
                }

                // Connection settings
                configCache.put("supabase.timeout.seconds", Integer.parseInt(
                        getElementText(supabaseElement, "timeout", "10")));
                configCache.put("supabase.max.retries", Integer.parseInt(
                        getElementText(supabaseElement, "maxRetries", "3")));

                logger.debug("Loaded Supabase configuration [url={}]", configCache.get("supabase.project.url"));
            }
        } catch (Exception e) {
            logger.warn("Error loading Supabase config, using defaults [error={}]", e.getMessage());
        }
    }

    /**
     * Load API configuration from XML document.
     */
    private void loadApiConfig(org.w3c.dom.Document doc) {
        try {
            org.w3c.dom.NodeList apiNodes = doc.getElementsByTagName("api");
            if (apiNodes.getLength() > 0) {
                org.w3c.dom.Element apiElement = (org.w3c.dom.Element) apiNodes.item(0);

                // Load common settings
                configCache.put("timeout.seconds", Integer.parseInt(
                        getElementText(apiElement, "timeout_seconds", String.valueOf(DEFAULT_TIMEOUT_SECONDS))));
                configCache.put("max.retries", Integer.parseInt(getElementText(apiElement, "max_retries", "3")));
                configCache.put("temperature", Double.parseDouble(getElementText(apiElement, "temperature", "0.7")));
                configCache.put("max_tokens", Integer.parseInt(getElementText(apiElement, "max_tokens", "4096")));

                // Load provider-specific settings
                String provider = getElementText(apiElement, "provider", "groq");
                configCache.put("provider", provider);
                configCache.put("base.url", getElementText(apiElement, "base_url", ""));
                configCache.put("model.name", getElementText(apiElement, "model_name", ""));

                // Load API key from config, but allow environment variable to override
                String configApiKey = getElementText(apiElement, "api_key_fallback", "demo-key");
                String envKey = getProviderEnvKey(provider);
                String envValue = System.getenv(envKey);

                if (envValue != null && !envValue.isEmpty()) {
                    configCache.put("api.key", envValue);
                    logger.info("Using API key from environment variable [{}]", envKey);
                } else {
                    configCache.put("api.key", configApiKey);
                    logger.info("Using API key from config.xml");
                }

                logger.debug("Loaded API configuration from config.xml [provider={}]", provider);
            }
        } catch (Exception e) {
            logger.warn("Error loading API config, using defaults [error={}]", e.getMessage());
            setDefaults();
        }
    }

    /**
     * Load rendering configuration from XML document.
     */
    private void loadRenderingConfig(org.w3c.dom.Document doc) {
        try {
            org.w3c.dom.NodeList renderingNodes = doc.getElementsByTagName("rendering");
            if (renderingNodes.getLength() > 0) {
                org.w3c.dom.Element renderingElement = (org.w3c.dom.Element) renderingNodes.item(0);
                org.w3c.dom.NodeList rendererNodes = renderingElement.getElementsByTagName("renderers");

                if (rendererNodes.getLength() > 0) {
                    org.w3c.dom.Element renderersElement = (org.w3c.dom.Element) rendererNodes.item(0);

                    // Load ASCII renderer setting
                    org.w3c.dom.NodeList asciiNodes = renderersElement.getElementsByTagName("ascii");
                    if (asciiNodes.getLength() > 0) {
                        org.w3c.dom.Element asciiElement = (org.w3c.dom.Element) asciiNodes.item(0);
                        boolean enabled = Boolean.parseBoolean(asciiElement.getAttribute("enabled"));
                        configCache.put("renderer.ascii.enabled", enabled);
                    }

                    // Load Mermaid renderer setting
                    org.w3c.dom.NodeList mermaidNodes = renderersElement.getElementsByTagName("mermaid");
                    if (mermaidNodes.getLength() > 0) {
                        org.w3c.dom.Element mermaidElement = (org.w3c.dom.Element) mermaidNodes.item(0);
                        boolean enabled = Boolean.parseBoolean(mermaidElement.getAttribute("enabled"));
                        configCache.put("renderer.mermaid.enabled", enabled);
                    }

                    // Load JSON renderer setting
                    org.w3c.dom.NodeList jsonNodes = renderersElement.getElementsByTagName("json");
                    if (jsonNodes.getLength() > 0) {
                        org.w3c.dom.Element jsonElement = (org.w3c.dom.Element) jsonNodes.item(0);
                        boolean enabled = Boolean.parseBoolean(jsonElement.getAttribute("enabled"));
                        configCache.put("renderer.json.enabled", enabled);
                    }

                    // Load Consistency renderer setting
                    org.w3c.dom.NodeList consistencyNodes = renderersElement.getElementsByTagName("consistency");
                    if (consistencyNodes.getLength() > 0) {
                        org.w3c.dom.Element consistencyElement = (org.w3c.dom.Element) consistencyNodes.item(0);
                        boolean enabled = Boolean.parseBoolean(consistencyElement.getAttribute("enabled"));
                        configCache.put("renderer.consistency.enabled", enabled);
                    }

                    // Load Graph renderer setting
                    org.w3c.dom.NodeList graphNodes = renderersElement.getElementsByTagName("graph");
                    if (graphNodes.getLength() > 0) {
                        org.w3c.dom.Element graphElement = (org.w3c.dom.Element) graphNodes.item(0);
                        boolean enabled = Boolean.parseBoolean(graphElement.getAttribute("enabled"));
                        configCache.put("renderer.graph.enabled", enabled);
                    }
                    
                 // Load DSL renderer setting
                    org.w3c.dom.NodeList dslNodes = renderersElement.getElementsByTagName("dsl");
                    if (dslNodes.getLength() > 0) {
                        org.w3c.dom.Element dslElement = (org.w3c.dom.Element) dslNodes.item(0);
                        boolean enabled = Boolean.parseBoolean(dslElement.getAttribute("enabled"));
                        configCache.put("renderer.dsl.enabled", enabled);
                    }


                    logger.debug(
                            "Loaded rendering configuration [ascii={}, mermaid={}, json={}, consistency={}, graph={}]",
                            configCache.get("renderer.ascii.enabled"),
                            configCache.get("renderer.mermaid.enabled"),
                            configCache.get("renderer.json.enabled"),
                            configCache.get("renderer.consistency.enabled"),
                            configCache.get("renderer.graph.enabled"));
                }
            } else {
                // Set defaults if rendering section not found
                configCache.put("renderer.ascii.enabled", true);
                configCache.put("renderer.mermaid.enabled", true);
                configCache.put("renderer.json.enabled", true);
                logger.debug("Rendering configuration not found, using defaults (all enabled)");
            }
        } catch (Exception e) {
            logger.warn("Error loading rendering config, using defaults [error={}]", e.getMessage());
            configCache.put("renderer.ascii.enabled", true);
            configCache.put("renderer.mermaid.enabled", true);
            configCache.put("renderer.json.enabled", true);
        }
    }

    /**
     * Get text content of a child element.
     */
    private String getElementText(org.w3c.dom.Element parent, String tagName, String defaultValue) {
        try {
            org.w3c.dom.NodeList nodes = parent.getElementsByTagName(tagName);
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent().trim();
            }
        } catch (Exception e) {
            logger.debug("Error getting element text [tag={}, error={}]", tagName, e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Set default configuration values.
     */
    private void setDefaults() {
        configCache.put("provider", "groq");
        configCache.put("base.url", GROQ_BASE_URL);
        configCache.put("model.name", DEFAULT_MODEL_NAME);
        configCache.put("timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
        configCache.put("api.key", "demo-key");
        configCache.put("temperature", 0.7);
        configCache.put("max_tokens", 4096);
        logger.info("Using default configuration values");
    }

    // ===== API Configuration =====

    public String getGroqApiKey() {
        return (String) configCache.get("groq.api.key");
    }

    public String getGroqBaseUrl() {
        return (String) configCache.get("groq.base.url");
    }

    public String getModelName() {
        return (String) configCache.get("model.name");
    }

    public Duration getTimeout() {
        return Duration.ofSeconds((Integer) configCache.getOrDefault("timeout.seconds", DEFAULT_TIMEOUT_SECONDS));
    }

    public double getTemperature() {
        return (Double) configCache.getOrDefault("temperature", 0.7);
    }

    public int getMaxTokens() {
        return (Integer) configCache.getOrDefault("max_tokens", 4096);
    }

    // ===== Multi-Provider Configuration =====

    public String getActiveProvider() {
        return (String) configCache.getOrDefault("active.provider", "groq");
    }

    public String getApiKey(String provider) {
        // Environment variable takes precedence, but config.xml value is already loaded
        return (String) configCache.getOrDefault("api.key", "demo-key");
    }

    public String getProviderBaseUrl(String provider) {
        return (String) configCache.getOrDefault("base.url", "");
    }

    public String getProviderModelName(String provider) {
        return (String) configCache.getOrDefault("model.name", "");
    }

    private String getProviderEnvKey(String provider) {
        switch (provider.toLowerCase()) {
            case "openai":
                return "OPENAI_API_KEY";
            case "anthropic":
                return "ANTHROPIC_API_KEY";
            case "groq":
                return "GROQ_API_KEY";
            case "azure":
                return "AZURE_OPENAI_API_KEY";
            case "google":
                return "GOOGLE_API_KEY";
            case "huggingface":
                return "HUGGINGFACE_API_KEY";
            default:
                return provider.toUpperCase() + "_API_KEY";
        }
    }

    // ===== Prompt Configuration =====

    public String getPrompt(String key) {
        try {
            String prompt = promptRegistry.get(key);
            if (prompt == null || prompt.trim().isEmpty()) {
                logger.warn("Prompt not found or empty [key={}]", key);
                throw new ConfigurationException("Prompt not found: " + key);
            }
            return prompt;
        } catch (Exception e) {
            logger.error("Failed to retrieve prompt [key={}, error={}]", key, e.getMessage(), e);
            throw new ConfigurationException("Failed to get prompt: " + key, e);
        }
    }

    public double getConsistencyThreshold(String promptKey) {
        try {
            String value = promptRegistry.getAttribute(promptKey, "consistency_threshold");
            return value != null ? Double.parseDouble(value) : DEFAULT_CONSISTENCY_THRESHOLD;
        } catch (NumberFormatException e) {
            logger.warn("Invalid consistency threshold for [key={}], using default [threshold={}]",
                    promptKey, DEFAULT_CONSISTENCY_THRESHOLD);
            return DEFAULT_CONSISTENCY_THRESHOLD;
        } catch (Exception e) {
            logger.error("Error getting consistency threshold [key={}, error={}]", promptKey, e.getMessage(), e);
            return DEFAULT_CONSISTENCY_THRESHOLD;
        }
    }

    public int getMaxRetries(String promptKey) {
        try {
            String value = promptRegistry.getAttribute(promptKey, "max_retries");
            return value != null ? Integer.parseInt(value) : DEFAULT_MAX_RETRIES;
        } catch (NumberFormatException e) {
            logger.warn("Invalid max retries for [key={}], using default [retries={}]",
                    promptKey, DEFAULT_MAX_RETRIES);
            return DEFAULT_MAX_RETRIES;
        } catch (Exception e) {
            logger.error("Error getting max retries [key={}, error={}]", promptKey, e.getMessage(), e);
            return DEFAULT_MAX_RETRIES;
        }
    }

    // ===== Prompt Keys (Constants) =====

    public static final String VALIDATION_PROMPT_KEY = "basic_validator_agent_prompt";
    public static final String DECOMPOSITION_PROMPT_KEY = "statement_decompostion_agent_prompt";
    public static final String CONDITION_EXTRACTION_PROMPT_KEY = "condition_extraction_prompt";
    public static final String ACTION_EXTRACTION_PROMPT_KEY = "action_extraction_prompt";
    public static final String CONSISTENCY_CHECK_PROMPT_KEY = "consistency_check_prompt";
    public static final String PROMPT_REFINEMENT_PROMPT_KEY = "prompt_refinement_prompt";
    public static final String RULE_CONVERTER_PROMPT_KEY = "rule_converter_prompt";
    public static final String UNIFIED_RULE_PROMPT_KEY = "unified_rule_prompt";
    public static final String SCHEDULE_PARSER_PROMPT_KEY = "schedule_parser_prompt";

    // ===== Supabase Configuration =====

    public String getSupabaseProjectName() {
        return (String) configCache.getOrDefault("supabase.project.name", "");
    }

    public String getSupabaseDocument() {
        return (String) configCache.getOrDefault("supabase.document", "");
    }

    public String getSupabaseProjectUrl() {
        return (String) configCache.getOrDefault("supabase.project.url", "");
    }

    public String getSupabaseAnonKey() {
        return (String) configCache.getOrDefault("supabase.anon.key", "");
    }

    public String getSupabaseEmail() {
        return (String) configCache.getOrDefault("supabase.email", "");
    }

    public String getSupabasePassword() {
        String envPassword = System.getenv("SUPABASE_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            return envPassword;
        }
        return (String) configCache.getOrDefault("supabase.password", "");
    }

    public int getSupabaseTimeoutSeconds() {
        return (Integer) configCache.getOrDefault("supabase.timeout.seconds", 10);
    }

    public int getSupabaseMaxRetries() {
        return (Integer) configCache.getOrDefault("supabase.max.retries", 3);
    }

    // ===== Observability Configuration =====

    private void loadObservabilityConfig(org.w3c.dom.Document doc) {
        try {
            org.w3c.dom.NodeList observabilityNodes = doc.getElementsByTagName("observability");
            if (observabilityNodes.getLength() > 0) {
                org.w3c.dom.Element obsElement = (org.w3c.dom.Element) observabilityNodes.item(0);
                configCache.put("observability.url", getElementText(obsElement, "url", ""));
                configCache.put("observability.api.key", getElementText(obsElement, "api_key", ""));
                configCache.put("observability.secret.key", getElementText(obsElement, "secret_key", ""));
                logger.debug("Loaded Observability configuration [url={}]", configCache.get("observability.url"));
            }
        } catch (Exception e) {
            logger.warn("Error loading Observability config, using defaults [error={}]", e.getMessage());
        }
    }

    public String getObservabilityUrl() {
        return (String) configCache.getOrDefault("observability.url", "");
    }

    public String getObservabilityApiKey() {
        return (String) configCache.getOrDefault("observability.api.key", "");
    }

    public String getObservabilitySecretKey() {
        return (String) configCache.getOrDefault("observability.secret.key", "");
    }

    // ===== Rate Limiting Configuration =====

    private void loadRateLimitConfig(org.w3c.dom.Document doc) {
        try {
            org.w3c.dom.NodeList nodes = doc.getElementsByTagName("ratelimiting");
            if (nodes.getLength() > 0) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) nodes.item(0);
                String delayStr = getElementText(element, "delay_ms", "0");
                configCache.put("ratelimiting.delay.ms", Long.parseLong(delayStr));
                logger.debug("Loaded Rate Limiting configuration [delay={}ms]",
                        configCache.get("ratelimiting.delay.ms"));
            } else {
                configCache.put("ratelimiting.delay.ms", 0L);
                logger.debug("Rate Limiting configuration not found, disabled (delay=0ms)");
            }
        } catch (Exception e) {
            logger.warn("Error loading Rate Limiting config, using defaults (disabled) [error={}]", e.getMessage());
            configCache.put("ratelimiting.delay.ms", 0L);
        }
    }

    public long getRateLimitDelay() {
        return (Long) configCache.getOrDefault("ratelimiting.delay.ms", 0L);
    }

    // ===== Rendering Configuration =====

    /**
     * Check if ASCII renderer is enabled.
     */
    public boolean isAsciiRendererEnabled() {
        return (Boolean) configCache.getOrDefault("renderer.ascii.enabled", true);
    }

    /**
     * Check if Mermaid renderer is enabled.
     */
    public boolean isMermaidRendererEnabled() {
        return (Boolean) configCache.getOrDefault("renderer.mermaid.enabled", true);
    }

    /**
     * Check if JSON renderer is enabled.
     */
    public boolean isJsonRendererEnabled() {
        return (Boolean) configCache.getOrDefault("renderer.json.enabled", true);
    }

    /**
     * Check if Consistency renderer is enabled.
     */
    public boolean isConsistencyRendererEnabled() {
        return (Boolean) configCache.getOrDefault("renderer.consistency.enabled", true);
    }

    /**
     * Check if Workflow Graph renderer is enabled.
     */
    public boolean isWorkflowGraphRendererEnabled() {
        return (Boolean) configCache.getOrDefault("renderer.graph.enabled", true);
    }

    // ===== Utility Methods =====

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            logger.warn("Environment variable not set, using default [key={}, default={}]", key, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Clear cache (useful for testing).
     */
    public void clearCache() {
        configCache.clear();
        loadConfiguration();
        logger.info("Configuration cache cleared and reloaded");
    }
}
