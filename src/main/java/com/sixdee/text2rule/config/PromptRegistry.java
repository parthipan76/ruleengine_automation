package com.sixdee.text2rule.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton registry for managing prompt templates loaded from config.xml.
 * Provides thread-safe access to prompts and their associated attributes.
 * 
 * Usage: PromptRegistry.getInstance().get("prompt_key")
 */
public class PromptRegistry {
    private static final Logger logger = LoggerFactory.getLogger(PromptRegistry.class);
    private static final String CONFIG_FILE = "config.xml";

    // Thread-safe singleton instance using Bill Pugh Singleton Design
    private static class SingletonHelper {
        private static final PromptRegistry INSTANCE = new PromptRegistry();
    }

    private final Map<String, String> prompts;
    private final Map<String, Map<String, String>> attributes;

    /**
     * Private constructor to prevent instantiation.
     * Loads all prompts from prompts.xml on initialization.
     */
    private PromptRegistry() {
        logger.info("Initializing PromptRegistry singleton instance");

        Map<String, String> tempPrompts = new HashMap<>();
        Map<String, Map<String, String>> tempAttributes = new HashMap<>();

        try (InputStream input = PromptRegistry.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                logger.error("Unable to find {} in classpath. PromptRegistry will be empty.", CONFIG_FILE);
            } else {
                loadPromptsFromXml(input, tempPrompts, tempAttributes);
                logger.info("Successfully loaded {} prompts from {}", tempPrompts.size(), CONFIG_FILE);
            }
        } catch (Exception e) {
            logger.error("Error loading {} - PromptRegistry will be empty", CONFIG_FILE, e);
        }

        // Make maps immutable for thread safety
        this.prompts = Collections.unmodifiableMap(tempPrompts);
        this.attributes = Collections.unmodifiableMap(tempAttributes);
    }

    /**
     * Returns the singleton instance of PromptRegistry.
     * Thread-safe lazy initialization.
     * 
     * @return the singleton PromptRegistry instance
     */
    public static PromptRegistry getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Loads prompts from XML input stream.
     * 
     * @param input         XML input stream
     * @param promptsMap    map to populate with prompts
     * @param attributesMap map to populate with attributes
     * @throws Exception if XML parsing fails
     */
    private void loadPromptsFromXml(InputStream input,
            Map<String, String> promptsMap,
            Map<String, Map<String, String>> attributesMap) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        org.w3c.dom.Document doc = dBuilder.parse(input);
        doc.getDocumentElement().normalize();

        org.w3c.dom.NodeList nList = doc.getElementsByTagName("prompt");
        logger.debug("Found {} prompt elements in XML", nList.getLength());

        for (int temp = 0; temp < nList.getLength(); temp++) {
            org.w3c.dom.Node node = nList.item(temp);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                String key = element.getAttribute("key");
                String content = element.getTextContent().trim();

                if (key == null || key.isEmpty()) {
                    logger.warn("Skipping prompt element without 'key' attribute");
                    continue;
                }

                promptsMap.put(key, content);
                logger.debug("Loaded prompt with key: {}", key);

                // Parse all attributes
                org.w3c.dom.NamedNodeMap nodeMap = element.getAttributes();
                Map<String, String> attrs = new HashMap<>();
                for (int i = 0; i < nodeMap.getLength(); i++) {
                    org.w3c.dom.Node attr = nodeMap.item(i);
                    attrs.put(attr.getNodeName(), attr.getNodeValue());
                }
                attributesMap.put(key, Collections.unmodifiableMap(attrs));
            }
        }
    }

    /**
     * Retrieves a prompt by its key.
     * 
     * @param key the prompt key
     * @return the prompt content, or an error message if not found
     */
    public String get(String key) {
        if (key == null || key.isEmpty()) {
            logger.warn("Attempted to get prompt with null or empty key");
            return null;
        }

        String prompt = prompts.get(key);
        if (prompt == null) {
            logger.warn("Prompt not found for key: {}", key);
            return null;
        }

        logger.debug("Retrieved prompt for key: {}", key);
        return prompt;
    }

    /**
     * Retrieves a specific attribute value for a prompt.
     * 
     * @param key           the prompt key
     * @param attributeName the attribute name
     * @return the attribute value, or null if not found
     */
    public String getAttribute(String key, String attributeName) {
        if (key == null || key.isEmpty() || attributeName == null || attributeName.isEmpty()) {
            logger.warn("Attempted to get attribute with null or empty key/attributeName");
            return null;
        }

        Map<String, String> promptAttributes = attributes.get(key);
        if (promptAttributes == null) {
            logger.debug("No attributes found for prompt key: {}", key);
            return null;
        }

        String value = promptAttributes.get(attributeName);
        if (value == null) {
            logger.debug("Attribute '{}' not found for prompt key: {}", attributeName, key);
        }

        return value;
    }

    /**
     * Returns all available prompt keys.
     * 
     * @return unmodifiable set of prompt keys
     */
    public java.util.Set<String> getAvailableKeys() {
        return prompts.keySet();
    }

    /**
     * Checks if a prompt exists for the given key.
     * 
     * @param key the prompt key
     * @return true if prompt exists, false otherwise
     */
    public boolean hasPrompt(String key) {
        return key != null && prompts.containsKey(key);
    }
}
