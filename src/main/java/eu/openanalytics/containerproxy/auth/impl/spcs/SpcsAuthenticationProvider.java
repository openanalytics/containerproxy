/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.auth.impl.spcs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.backend.spcs.client.ApiClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import eu.openanalytics.containerproxy.backend.spcs.client.api.StatementsApi;
import eu.openanalytics.containerproxy.backend.spcs.client.auth.HttpBearerAuth;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ResultSet;
import eu.openanalytics.containerproxy.backend.spcs.client.model.SubmitStatementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SpcsAuthenticationProvider implements AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(SpcsAuthenticationProvider.class);
    private static final String USER_AGENT = "ContainerProxy/1.2.2";
    
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final Environment environment;
    private final String computeWarehouse;

    public SpcsAuthenticationProvider(Environment environment) {
        this.environment = environment;
        
        // Load optional compute warehouse for SPCS authentication role retrieval
        this.computeWarehouse = environment.getProperty("proxy.spcs.compute-warehouse");
        if (computeWarehouse != null && !computeWarehouse.isEmpty()) {
            logger.debug("SPCS compute warehouse configured for role retrieval: {}", computeWarehouse);
        }
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        SpcsAuthenticationToken authRequest = (SpcsAuthenticationToken) authentication;

        if (authRequest.isValid()) {
            String spcsIngressUserToken = authRequest.getCredentials() != null ? authRequest.getCredentials().toString() : null;
            
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            
            // Retrieve all available roles for the user using CURRENT_AVAILABLE_ROLES()
            // This stores roles with the principal, allowing authorization logic to check both:
            // - admin-groups: via UserService.isAdmin() which uses isMember() to check authorities
            // - access-groups: via AccessControlEvaluationService which checks authorities against proxy spec access-groups
            if (computeWarehouse == null || computeWarehouse.isEmpty()) {
                logger.debug("Compute warehouse not configured (proxy.spcs.compute-warehouse), skipping role retrieval");
            } else if (spcsIngressUserToken == null || spcsIngressUserToken.isBlank()) {
                logger.debug("User token not available (executeAsCaller may not be enabled), skipping role retrieval");
            } else {
                // All conditions met: retrieve available roles
                // Requires: user token and compute warehouse
                logger.debug("User token available, retrieving available roles using warehouse {}", computeWarehouse);
                List<String> availableRoles = getAvailableRoles(spcsIngressUserToken);
                logger.debug("User has {} available roles", availableRoles.size());
                for (String role : availableRoles) {
                    // Format: ROLE_ROLENAME (UserService.getGroups() strips the "ROLE_" prefix)
                    String roleName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                    authorities.add(new SimpleGrantedAuthority(roleName));
                }
            }
            
            return new SpcsAuthenticationToken(
                authRequest.getPrincipal().toString(),
                spcsIngressUserToken,
                authorities,
                authRequest.getDetails(),
                true
            );
        }

        throw new SpcsAuthenticationException("Invalid Snowflake username");
    }

    /**
     * Retrieves all available roles for the user using CURRENT_AVAILABLE_ROLES().
     * Uses the Sf-Context-Current-User-Token to authenticate as the caller.
     * 
     * @param userToken The Sf-Context-Current-User-Token
     * @return List of role names available to the user (may be empty)
     */
    private List<String> getAvailableRoles(String userToken) {
        List<String> availableRoles = new ArrayList<>();
        
        try {
            // Get account URL from environment
            String accountUrl = getAccountUrl();
            if (accountUrl == null) {
                logger.warn("Cannot retrieve available roles: account URL not available");
                return availableRoles;
            }
            
            // Create a new API client instance (not shared) with user token as bearer token
            // Use KeyPair authentication scheme (HttpBearerAuth) - works for OAuth tokens from SPCS ingress
            ApiClient apiClient = new ApiClient();
            apiClient.setBasePath(accountUrl);
            
            HttpBearerAuth bearerAuth = (HttpBearerAuth) apiClient.getAuthentication("KeyPair");
            
            // SPCS authentication requires combined token format: <service-oauth-token>.<Sf-Context-Current-User-Token>
            // Read service OAuth token from file and combine with user token
            String serviceToken = readSpcsSessionTokenFromFile();
            if (serviceToken == null || serviceToken.isEmpty()) {
                logger.warn("Service OAuth token not available from file for role retrieval");
                return availableRoles;
            }
            
            // Format: <service-oauth-token>.<Sf-Context-Current-User-Token>
            String combinedToken = serviceToken + "." + userToken;           
            bearerAuth.setBearerToken(combinedToken);
            
            // Using SQL statement endpoint to query CURRENT_AVAILABLE_ROLES()
            StatementsApi statementsApi = new StatementsApi(apiClient);
            
            // Query CURRENT_AVAILABLE_ROLES() which returns a JSON array of role names
            String sql = "SELECT CURRENT_AVAILABLE_ROLES() AS available_roles";
            
            // Prepare request
            SubmitStatementRequest request = new SubmitStatementRequest();
            request.setStatement(sql);
            // Set warehouse for SQL execution
            request.setWarehouse(computeWarehouse.toUpperCase());
            
            try {
                ResultSet result = statementsApi.submitStatement(
                    USER_AGENT,
                    request,
                    null, // requestId
                    false, // async
                    false, // nullable
                    null, // accept
                    "OAUTH" // xSnowflakeAuthorizationTokenType: OAuth token from SPCS ingress
                ).block();
                
                // Parse result: CURRENT_AVAILABLE_ROLES() returns a JSON array string
                // ResultSet.data is List<List<String>>, first row, first column contains the JSON array
                if (result.getData() != null && !result.getData().isEmpty()) {
                    List<String> firstRow = result.getData().get(0);
                    if (firstRow != null && !firstRow.isEmpty()) {
                        String jsonArrayString = firstRow.get(0);
                        if (jsonArrayString != null && !jsonArrayString.isEmpty()) {
                            // Parse JSON array: ["ROLE1", "ROLE2", "ROLE3"]
                            List<String> roles = jsonMapper.readValue(jsonArrayString, new TypeReference<List<String>>() {});
                            if (roles != null) {
                                for (String role : roles) {
                                    if (role != null && !role.isEmpty()) {
                                        availableRoles.add(role.toUpperCase());
                                    }
                                }
                                logger.debug("Retrieved {} available roles from CURRENT_AVAILABLE_ROLES()", availableRoles.size());
                            }
                        }
                    }
                }
            } catch (WebClientResponseException e) {
                // Log error details: exception includes message and stack trace
                logger.warn("Failed to retrieve available roles (user will not have role-based privileges): code={}", 
                    e.getStatusCode().value(), e);
                // Return empty roles on failure (no fallback)
                return availableRoles;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logger.warn("Failed to parse available roles JSON (user will not have role-based privileges): {}", 
                    e.getMessage(), e);
                // Return empty roles on JSON parsing failure
                return availableRoles;
            }
        } catch (Exception e) {
            logger.error("Error retrieving available roles: {}", e.getMessage(), e);
            // Return empty roles on exception (no fallback)
            return availableRoles;
        }
        
        return availableRoles;
    }
    
    /**
     * Reads the SPCS session token from the standard file location if available.
     * @return The token string or null if not available.
     */
    private String readSpcsSessionTokenFromFile() {
        try {
            Path tokenPath = Paths.get("/snowflake/session/token");
            if (Files.exists(tokenPath) && Files.isRegularFile(tokenPath)) {
                String token = Files.readString(tokenPath).trim();
                if (!token.isEmpty()) {
                    return token;
                } else {
                    logger.warn("SPCS session token file exists but is empty: {}", tokenPath);
                }
            }
        } catch (Exception ex) {
            logger.warn("Error reading SPCS session token from file: {}", ex.getMessage());
        }
        return null;
    }
    
    /**
     * Gets the Snowflake account URL from environment variables.
     * 
     * @return Account URL or null if not available
     */
    private String getAccountUrl() {
        // Check SNOWFLAKE_HOST first (preferred when running inside SPCS)
        String snowflakeHost = environment.getProperty("SNOWFLAKE_HOST");
        if (snowflakeHost != null && !snowflakeHost.isEmpty()) {
            if (snowflakeHost.startsWith("https://") || snowflakeHost.startsWith("http://")) {
                return snowflakeHost;
            } else {
                return "https://" + snowflakeHost;
            }
        }
        
        // Fall back to constructing from SNOWFLAKE_ACCOUNT
        String snowflakeAccount = environment.getProperty("SNOWFLAKE_ACCOUNT");
        if (snowflakeAccount != null && !snowflakeAccount.isEmpty()) {
            return String.format("https://%s.snowflakecomputing.com", snowflakeAccount);
        }
        
        return null;
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return SpcsAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
