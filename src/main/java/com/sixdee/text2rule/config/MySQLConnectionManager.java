package com.sixdee.text2rule.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MySQL Connection Manager for Product and Policy database queries.
 * 
 * Databases:
 * 1. Product Master DB (10.0.10.24): Magik_3.0_Demo_Upgrade
 * 2. Policy DB (10.0.15.17): MAGIK
 */
public class MySQLConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(MySQLConnectionManager.class);

    // Product Master Database (10.0.10.24)
    private static final String PRODUCT_DB_HOST = "10.0.10.24";
    private static final String PRODUCT_DB_PORT = "3306";
    private static final String PRODUCT_DB_NAME = "Magik_3.0_Demo_Upgrade";
    private static final String PRODUCT_DB_USER = "magikuser";
    private static final String PRODUCT_DB_PASSWORD = "magikuser@6Dtech";

    // Policy Database (10.0.15.17)
    private static final String POLICY_DB_HOST = "10.0.15.17";
    private static final String POLICY_DB_PORT = "3306";
    private static final String POLICY_DB_NAME = "MAGIK";
    private static final String POLICY_DB_USER = "dbuser";
    private static final String POLICY_DB_PASSWORD = "dbuser@6Dtech";

    private static MySQLConnectionManager instance;

    private MySQLConnectionManager() {
        try {
            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("MySQL JDBC Driver loaded successfully");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL JDBC Driver not found", e);
        }
    }

    public static synchronized MySQLConnectionManager getInstance() {
        if (instance == null) {
            instance = new MySQLConnectionManager();
        }
        return instance;
    }

    /**
     * Get connection to Product Master database.
     */
    public Connection getProductConnection() throws SQLException {
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                PRODUCT_DB_HOST, PRODUCT_DB_PORT, PRODUCT_DB_NAME);
        
        Properties props = new Properties();
        props.setProperty("user", PRODUCT_DB_USER);
        props.setProperty("password", PRODUCT_DB_PASSWORD);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "30000");

        logger.debug("Connecting to Product DB: {}", url);
        return DriverManager.getConnection(url, props);
    }

    /**
     * Get connection to Policy database.
     */
    public Connection getPolicyConnection() throws SQLException {
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                POLICY_DB_HOST, POLICY_DB_PORT, POLICY_DB_NAME);
        
        Properties props = new Properties();
        props.setProperty("user", POLICY_DB_USER);
        props.setProperty("password", POLICY_DB_PASSWORD);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "30000");

        logger.debug("Connecting to Policy DB: {}", url);
        return DriverManager.getConnection(url, props);
    }

    /**
     * Query product by name (Levenshtein fuzzy matching).
     * 
     * @param productName Product name to search (e.g., "10 GB data pack")
     * @return Map containing product details or null if not found
     */
    public Map<String, Object> findProductByName(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return null;
        }

        String sql = """
            SELECT 
                ID,
                PRODUCT_NAME,
                UPC_PRODUCT,
                PRODUCT_TYPE,
                PRODUCT_GROUP,
                VALIDITY_TYPE,
                VALIDITY,
                PRICE
            FROM PROD_PRODUCT_MASTER
            WHERE LOWER(PRODUCT_NAME) LIKE LOWER(?)
            OR LOWER(UPC_PRODUCT) LIKE LOWER(?)
            ORDER BY 
                CASE 
                    WHEN LOWER(PRODUCT_NAME) = LOWER(?) THEN 0
                    WHEN LOWER(PRODUCT_NAME) LIKE LOWER(?) THEN 1
                    ELSE 2
                END
            LIMIT 1
            """;

        try (Connection conn = getProductConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String searchPattern = "%" + productName.trim() + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, productName.trim());
            stmt.setString(4, productName.trim() + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("product_id", rs.getLong("ID"));
                    product.put("product_name", rs.getString("PRODUCT_NAME"));
                    product.put("upc_product", rs.getString("UPC_PRODUCT"));
                    product.put("product_type", rs.getString("PRODUCT_TYPE"));
                    product.put("product_group", rs.getString("PRODUCT_GROUP"));
                    product.put("validity_type", rs.getString("VALIDITY_TYPE"));
                    product.put("validity", rs.getInt("VALIDITY"));
                    product.put("price", rs.getDouble("PRICE"));
                    
                    logger.info("Found product: {} (ID: {})", 
                            product.get("product_name"), product.get("product_id"));
                    return product;
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying product by name: {}", productName, e);
        }

        logger.warn("No product found for: {}", productName);
        return null;
    }

    /**
     * Query product by approximate matching using extracted values (data size, validity).
     * 
     * @param dataSize Data size in GB (e.g., "10")
     * @param validity Validity in days (e.g., "15")
     * @return Map containing product details or null if not found
     */
    public Map<String, Object> findProductBySpecs(String dataSize, String validity) {
        String sql = """
            SELECT 
                ID,
                PRODUCT_NAME,
                UPC_PRODUCT,
                PRODUCT_TYPE,
                PRODUCT_GROUP,
                VALIDITY_TYPE,
                VALIDITY,
                PRICE
            FROM PROD_PRODUCT_MASTER
            WHERE (LOWER(PRODUCT_NAME) LIKE LOWER(?) OR LOWER(UPC_PRODUCT) LIKE LOWER(?))
            AND VALIDITY = ?
            LIMIT 1
            """;

        try (Connection conn = getProductConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String dataPattern = "%" + dataSize + "%GB%";
            stmt.setString(1, dataPattern);
            stmt.setString(2, dataPattern);
            stmt.setInt(3, Integer.parseInt(validity));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("product_id", rs.getLong("ID"));
                    product.put("product_name", rs.getString("PRODUCT_NAME"));
                    product.put("upc_product", rs.getString("UPC_PRODUCT"));
                    product.put("product_type", rs.getString("PRODUCT_TYPE"));
                    product.put("product_group", rs.getString("PRODUCT_GROUP"));
                    product.put("validity_type", rs.getString("VALIDITY_TYPE"));
                    product.put("validity", rs.getInt("VALIDITY"));
                    product.put("price", rs.getDouble("PRICE"));
                    
                    logger.info("Found product by specs: {} (ID: {})", 
                            product.get("product_name"), product.get("product_id"));
                    return product;
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying product by specs: {}GB/{}days", dataSize, validity, e);
        } catch (NumberFormatException e) {
            logger.error("Invalid validity value: {}", validity);
        }

        return null;
    }

    /**
     * Query LeadPolicy by campaign name.
     * 
     * @param campaignName Campaign name (e.g., "Baby Care Program")
     * @return LeadPolicyId or null if not found
     */
    public String findLeadPolicyId(String campaignName) {
        if (campaignName == null || campaignName.trim().isEmpty()) {
            return null;
        }

        // First try exact match on POLICY_NAME
        String sql = """
            SELECT ID FROM RE_LEAD_POLICY 
            WHERE LOWER(POLICY_NAME) LIKE LOWER(?)
            OR LOWER(CAMPAIGNS) LIKE LOWER(?)
            LIMIT 1
            """;

        try (Connection conn = getPolicyConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String searchPattern = "%" + campaignName.trim() + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String policyId = String.valueOf(rs.getLong("ID"));
                    logger.info("Found LeadPolicyId: {} for campaign: {}", policyId, campaignName);
                    return policyId;
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying LeadPolicy for: {}", campaignName, e);
        }

        // Fallback: try CAMPAIGN_DEFINITION_MASTER
        return findPolicyByCampaignDefinition(campaignName);
    }

    /**
     * Query policy via CAMPAIGN_DEFINITION_MASTER.
     */
    private String findPolicyByCampaignDefinition(String campaignName) {
        String sql = """
            SELECT lp.ID 
            FROM RE_LEAD_POLICY lp
            INNER JOIN CAMPAIGN_DEFINITION_MASTER cdm 
                ON lp.CAMPAIGNS LIKE CONCAT('%', cdm.CAMPAIGN_NAME, '%')
            WHERE LOWER(cdm.CAMPAIGN_NAME) LIKE LOWER(?)
            LIMIT 1
            """;

        try (Connection conn = getPolicyConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, "%" + campaignName.trim() + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String policyId = String.valueOf(rs.getLong("ID"));
                    logger.info("Found LeadPolicyId via campaign definition: {}", policyId);
                    return policyId;
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying policy via campaign definition: {}", campaignName, e);
        }

        logger.warn("No LeadPolicy found for campaign: {}", campaignName);
        return null;
    }

    /**
     * Query ContactPolicy by category.
     */
    public String findContactPolicyId(String category, String frequency, String threshold) {
        String sql = """
            SELECT nd.POLICY_ID 
            FROM NCP_DETAIL nd
            INNER JOIN NCP_DESCRIPTION ndesc ON nd.POLICY_ID = ndesc.POLICY_ID
            WHERE LOWER(nd.CATEGORY_FIELD) LIKE LOWER(?)
            AND ndesc.FREQUENCY = ?
            AND ndesc.THRESHOLD = ?
            LIMIT 1
            """;

        try (Connection conn = getPolicyConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, "%" + category + "%");
            stmt.setString(2, frequency);
            stmt.setString(3, threshold);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String policyId = String.valueOf(rs.getLong("POLICY_ID"));
                    logger.info("Found ContactPolicyId: {}", policyId);
                    return policyId;
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying ContactPolicy", e);
        }

        return null;
    }

    /**
     * Close connection safely.
     */
    public void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.warn("Error closing connection", e);
            }
        }
    }

    /**
     * Test database connections.
     */
    public boolean testConnections() {
        boolean productOk = false;
        boolean policyOk = false;

        try (Connection conn = getProductConnection()) {
            productOk = conn.isValid(5);
            logger.info("Product DB connection test: {}", productOk ? "SUCCESS" : "FAILED");
        } catch (SQLException e) {
            logger.error("Product DB connection test failed", e);
        }

        try (Connection conn = getPolicyConnection()) {
            policyOk = conn.isValid(5);
            logger.info("Policy DB connection test: {}", policyOk ? "SUCCESS" : "FAILED");
        } catch (SQLException e) {
            logger.error("Policy DB connection test failed", e);
        }

        return productOk && policyOk;
    }
}