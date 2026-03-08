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
package eu.openanalytics.containerproxy.backend.spcs;

import eu.openanalytics.containerproxy.backend.spcs.client.ApiClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import eu.openanalytics.containerproxy.backend.spcs.client.auth.HttpBearerAuth;
import eu.openanalytics.containerproxy.backend.spcs.client.api.ServiceApi;
import eu.openanalytics.containerproxy.backend.spcs.client.api.StatementsApi;
import eu.openanalytics.containerproxy.backend.spcs.client.model.Service;
import eu.openanalytics.containerproxy.backend.spcs.client.model.SubmitStatementRequest;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceContainer;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceEndpoint;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceSpec;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceSpecInlineText;
import com.fasterxml.jackson.core.type.TypeReference;
import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.event.NewProxyEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerName;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerIndexKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HttpHeaders;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PortMappingsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HttpHeadersKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxyIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RealmIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.util.Retrying;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import eu.openanalytics.containerproxy.auth.impl.spcs.SpcsAuthenticationToken;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

@Component
@ConditionalOnProperty(name = "proxy.container-backend", havingValue = "spcs")
public class SpcsBackend extends AbstractContainerBackend {

    private static final String PROPERTY_PREFIX = "proxy.spcs.";
    private static final String PROPERTY_ACCOUNT_IDENTIFIER = "account-identifier";
    private static final String PROPERTY_USERNAME = "username";
    private static final String PROPERTY_PROGRAMMATIC_ACCESS_TOKEN = "programmatic-access-token";
    private static final String PROPERTY_PRIVATE_RSA_KEY_PATH = "private-rsa-key-path";
    private static final String PROPERTY_DATABASE = "database";
    private static final String PROPERTY_SCHEMA = "schema";
    private static final String PROPERTY_COMPUTE_POOL = "compute-pool";
    private static final String PROPERTY_COMPUTE_WAREHOUSE = "compute-warehouse";
    private static final String PROPERTY_SERVICE_WAIT_TIME = "service-wait-time";
    private static final String PROPERTY_USE_ROLE = "use-role";

    private ServiceApi snowflakeServiceAPI;
    private StatementsApi snowflakeStatementsAPI;
    private ApiClient snowflakeAPIClient;
    private String snowflakeTokenType; // Token type for API calls (KEYPAIR_JWT, OAUTH, or PROGRAMMATIC_ACCESS_TOKEN)
    private int serviceWaitTime;
    
    // YAML mapper for service spec serialization/deserialization
    private final ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .build());
    
    // JSON mapper for comment metadata serialization/deserialization
    private final ObjectMapper jsonMapper = new ObjectMapper();
    
    private String database;
    private String schema;
    private String computePool;
    private String accountUrl;
    private String accountIdentifier; // Account identifier (e.g., "ORG-ACCOUNT" or "ACCOUNT") - stored separately from URL
    private String username;
    private AuthMethod authMethod;
    private SpcsKeypairAuth keypairAuth; // For keypair authentication
    private Supplier<String> jwtTokenSupplier; // Supplier to generate/regenerate JWT tokens for keypair auth

    private static final Path SPCS_SESSION_TOKEN_PATH = Paths.get("/snowflake/session/token");

    private enum AuthMethod {
        SPCS_SESSION_TOKEN,  // Running inside SPCS, token from /snowflake/session/token
        KEYPAIR,            // Username + private RSA key
        PAT                 // Username + PAT token
    }

    @Inject
    private IProxySpecProvider proxySpecProvider;
    
    @Override
    @PostConstruct
    public void initialize() {
        super.initialize();

        // Detect if running inside SPCS by checking SNOWFLAKE_SERVICE_NAME environment variable
        String snowflakeServiceName = environment.getProperty("SNOWFLAKE_SERVICE_NAME");
        boolean runningInsideSpcs = snowflakeServiceName != null && !snowflakeServiceName.isEmpty();

        if (runningInsideSpcs) {
            loadConfigurationFromEnvironment();
            setupSpcsSessionTokenAuth();
        } else {
            loadConfigurationFromProperties();
            setupExternalAuth();
        }

        serviceWaitTime = environment.getProperty(PROPERTY_PREFIX + PROPERTY_SERVICE_WAIT_TIME, Integer.class, 180000);

        initializeSnowflakeAPIClient();

        // Validate specs (log warnings only - actual validation happens when starting proxy)
        for (ProxySpec spec : proxySpecProvider.getSpecs()) {
            ContainerSpec containerSpec = spec.getContainerSpecs().get(0);
            if (!containerSpec.getImage().isOriginalValuePresent()) {
                log.warn("Spec with id '{}' has no 'container-image' configured, this is required for running on Snowflake SPCS. Proxy will fail to start if this field is missing.", spec.getId());
            }
            if (!containerSpec.getMemoryRequest().isOriginalValuePresent()) {
                log.warn("Spec with id '{}' has no 'container-memory-request' configured, this is required for running on Snowflake SPCS. Proxy will fail to start if this field is missing.", spec.getId());
            }
            if (!containerSpec.getCpuRequest().isOriginalValuePresent()) {
                log.warn("Spec with id '{}' has no 'container-cpu-request' configured, this is required for running on Snowflake SPCS. Proxy will fail to start if this field is missing.", spec.getId());
            }
            if (containerSpec.getMemoryLimit().isOriginalValuePresent()) {
                log.warn("Spec with id '{}' has 'memory-limit' configured, this is not supported by Snowflake SPCS and will be ignored.", spec.getId());
            }
            if (containerSpec.getCpuLimit().isOriginalValuePresent()) {
                log.warn("Spec with id '{}' has 'cpu-limit' configured, this is not supported by Snowflake SPCS and will be ignored.", spec.getId());
            }
            if (containerSpec.isPrivileged()) {
                log.warn("Spec with id '{}' has 'privileged: true' configured, this is not supported by Snowflake SPCS and will be ignored.", spec.getId());
            }
            
            // Validate that volume mounts reference volumes defined in spec's spcs.volumes
            SpcsSpecExtension specExtension = spec.getSpecExtension(SpcsSpecExtension.class);
            if (specExtension != null && containerSpec.getVolumes().isOriginalValuePresent() && containerSpec.getVolumes().getOriginalValue() != null && !containerSpec.getVolumes().getOriginalValue().isEmpty()) {
                java.util.Set<String> definedVolumeNames = new java.util.HashSet<>();
                if (specExtension.getSpcsVolumes() != null) {
                    for (SpcsVolume volume : specExtension.getSpcsVolumes()) {
                        if (volume != null && volume.getName() != null) {
                            definedVolumeNames.add(volume.getName());
                        }
                    }
                }
                for (String volumeMountString : containerSpec.getVolumes().getOriginalValue()) {
                    int colonIndex = volumeMountString.indexOf(':');
                    if (colonIndex > 0) {
                        String volumeName = volumeMountString.substring(0, colonIndex).trim();
                        if (!volumeName.isEmpty() && !definedVolumeNames.contains(volumeName)) {
                            throw new IllegalStateException(String.format("Error in configuration of specs: spec with id '%s' references volume '%s' in container-volumes which is not defined in spec's spcs.volumes", spec.getId(), volumeName));
                        }
                    }
                }
            }
        }
    }


    /**
     * Loads configuration from Snowflake environment variables (when running inside SPCS).
     */
    private void loadConfigurationFromEnvironment() {
        log.info("Detected running inside SPCS (SNOWFLAKE_SERVICE_NAME: {})", environment.getProperty("SNOWFLAKE_SERVICE_NAME"));
        
        // Load Snowflake environment variables
        String snowflakeHost = environment.getProperty("SNOWFLAKE_HOST");
        String snowflakeAccount = environment.getProperty("SNOWFLAKE_ACCOUNT");
        String snowflakeDatabase = environment.getProperty("SNOWFLAKE_DATABASE");
        String snowflakeSchema = environment.getProperty("SNOWFLAKE_SCHEMA");
        String snowflakeRegion = environment.getProperty("SNOWFLAKE_REGION");
        String snowflakeComputePool = environment.getProperty("SNOWFLAKE_COMPUTE_POOL");
        
        // Get account identifier from SNOWFLAKE_ACCOUNT (required)
        if (snowflakeAccount == null || snowflakeAccount.isEmpty()) {
            throw new IllegalStateException("Error in SPCS environment: SNOWFLAKE_ACCOUNT not set");
        }
        accountIdentifier = snowflakeAccount;
        
        // Use SNOWFLAKE_HOST if available, otherwise build from account identifier
        if (snowflakeHost != null && !snowflakeHost.isEmpty()) {
            // SNOWFLAKE_HOST contains the full hostname (e.g., "<identifier>.us-east-1.snowflakecomputing.com")
            // Add https:// if not present
            if (snowflakeHost.startsWith("https://") || snowflakeHost.startsWith("http://")) {
                accountUrl = snowflakeHost;
            } else {
                accountUrl = "https://" + snowflakeHost;
            }
            log.info("Using account URL from SNOWFLAKE_HOST environment variable: {}", accountUrl);
        } else {
            // Build account URL from account identifier by appending .snowflakecomputing.com
            // Format: https://{account_identifier}.snowflakecomputing.com
            accountUrl = String.format("https://%s.snowflakecomputing.com", accountIdentifier);
        }
        
        // Database and schema: application yaml config overrides environment variables (when running inside SPCS)
        database = getProperty(PROPERTY_DATABASE);
        if (database == null || database.isEmpty()) {
            database = snowflakeDatabase;
            if (database != null && !database.isEmpty()) {
                log.info("Using database from SNOWFLAKE_DATABASE environment variable: {}", database);
            } else {
                throw new IllegalStateException("Error in configuration of SPCS backend: SNOWFLAKE_DATABASE not set and proxy.spcs.database not configured");
            }
        } else {
            log.info("Using database from configuration: {} (SNOWFLAKE_DATABASE was: {})", database, snowflakeDatabase);
        }
        
        schema = getProperty(PROPERTY_SCHEMA);
        if (schema == null || schema.isEmpty()) {
            schema = snowflakeSchema;
            if (schema != null && !schema.isEmpty()) {
                log.info("Using schema from SNOWFLAKE_SCHEMA environment variable: {}", schema);
            } else {
                throw new IllegalStateException("Error in configuration of SPCS backend: SNOWFLAKE_SCHEMA not set and proxy.spcs.schema not configured");
            }
        } else {
            log.info("Using schema from configuration: {} (SNOWFLAKE_SCHEMA was: {})", schema, snowflakeSchema);
        }
        
        // Compute pool: use configured value if set, otherwise use SNOWFLAKE_COMPUTE_POOL
        computePool = getProperty(PROPERTY_COMPUTE_POOL);
        if (computePool == null) {
            if (snowflakeComputePool != null && !snowflakeComputePool.isEmpty()) {
                computePool = snowflakeComputePool;
                log.info("Using compute pool from SNOWFLAKE_COMPUTE_POOL environment variable: {}", computePool);
            } else {
                throw new IllegalStateException("Error in configuration of SPCS backend: proxy.spcs.compute-pool not set and SNOWFLAKE_COMPUTE_POOL environment variable not available");
            }
        } else {
            log.info("Using compute pool from configuration: {} (SNOWFLAKE_COMPUTE_POOL was: {})", computePool, snowflakeComputePool);
        }
        
        log.info("Loaded SPCS environment: account={}, region={}, compute-pool={}", 
            snowflakeAccount, snowflakeRegion, computePool);
    }

    /**
     * Loads configuration from properties (when running external to SPCS).
     */
    private void loadConfigurationFromProperties() {
        // Get account identifier from property (required when running external to SPCS)
        String accountIdentifierConfig = getProperty(PROPERTY_ACCOUNT_IDENTIFIER);
        if (accountIdentifierConfig == null || accountIdentifierConfig.isEmpty()) {
            throw new IllegalStateException("Error in configuration of SPCS backend: proxy.spcs.account-identifier not set");
        }
        accountIdentifier = accountIdentifierConfig;
        
        // Check for SNOWFLAKE_HOST environment variable for account URL (works both inside and outside SPCS)
        String snowflakeHost = environment.getProperty("SNOWFLAKE_HOST");
        if (snowflakeHost != null && !snowflakeHost.isEmpty()) {
            // SNOWFLAKE_HOST contains the full hostname
            // Add https:// if not present
            if (snowflakeHost.startsWith("https://") || snowflakeHost.startsWith("http://")) {
                accountUrl = snowflakeHost;
            } else {
                accountUrl = "https://" + snowflakeHost;
            }
            log.info("Using account URL from SNOWFLAKE_HOST environment variable: {}", accountUrl);
        } else {
            // Construct account URL from account identifier by appending .snowflakecomputing.com
            accountUrl = String.format("https://%s.snowflakecomputing.com", accountIdentifier);
        }
        
        database = getProperty(PROPERTY_DATABASE);
        if (database == null) {
            throw new IllegalStateException("Error in configuration of SPCS backend: proxy.spcs.database not set");
        }

        schema = getProperty(PROPERTY_SCHEMA);
        if (schema == null) {
            throw new IllegalStateException("Error in configuration of SPCS backend: proxy.spcs.schema not set");
        }

        computePool = getProperty(PROPERTY_COMPUTE_POOL);
        if (computePool == null) {
            throw new IllegalStateException("Error in configuration of SPCS backend: proxy.spcs.compute-pool not set");
        }
    }

    /**
     * Sets up SPCS session token authentication (when running inside SPCS).
     */
    private void setupSpcsSessionTokenAuth() {
        // Check for session token file
        if (!Files.exists(SPCS_SESSION_TOKEN_PATH) || !Files.isRegularFile(SPCS_SESSION_TOKEN_PATH)) {
            throw new IllegalStateException("Running inside SPCS but session token file not found: " + SPCS_SESSION_TOKEN_PATH);
        }
        
        // Use supplier to read token fresh on each request (allows SPCS to refresh it automatically)
        authMethod = AuthMethod.SPCS_SESSION_TOKEN;
        jwtTokenSupplier = () -> {
            try {
                String sessionToken = Files.readString(SPCS_SESSION_TOKEN_PATH).trim();
                if (sessionToken.isEmpty()) {
                    throw new IllegalStateException("SPCS session token file exists but is empty: " + SPCS_SESSION_TOKEN_PATH);
                }
                return sessionToken;
            } catch (IOException e) {
                throw new RuntimeException("Error reading SPCS session token from " + SPCS_SESSION_TOKEN_PATH + ": " + e.getMessage(), e);
            }
        };
        log.info("Running inside SPCS: will read authentication token from {} on each request", SPCS_SESSION_TOKEN_PATH);
    }

    /**
     * Initializes the Snowflake API client with authentication.
     */
    private void initializeSnowflakeAPIClient() {
        try {
            // Create a new API client instance for SPCS backend
            snowflakeAPIClient = new ApiClient();
            snowflakeAPIClient.setBasePath(accountUrl);
            
            // Set authentication token based on auth method
            // Use KeyPair authentication scheme (HttpBearerAuth) for all token types - works for JWT, OAuth, and PAT tokens
            HttpBearerAuth bearerAuth = (HttpBearerAuth) snowflakeAPIClient.getAuthentication("KeyPair");
            
            // Set bearer token using supplier so it is resolved on each request
            // For SPCS_SESSION_TOKEN: reads current value from file (allows SPCS to refresh token)
            // For KEYPAIR: generates JWT on demand (auto-refresh on expiry)
            // For PAT: supplier returns same token
            bearerAuth.setBearerToken(jwtTokenSupplier);
            
            // Set X-Snowflake-Authorization-Token-Type header
            // This helps snowflake identify the token type and useful in logs for debugging
            if (authMethod == AuthMethod.KEYPAIR) {
                snowflakeTokenType = "KEYPAIR_JWT";  // Key-pair JWT token
            } else if (authMethod == AuthMethod.SPCS_SESSION_TOKEN) {
                snowflakeTokenType = "OAUTH";  // OAuth token from SPCS session
            } else if (authMethod == AuthMethod.PAT) {
                snowflakeTokenType = "PROGRAMMATIC_ACCESS_TOKEN";  // Programmatic access token
            } else {
                throw new IllegalStateException("Unknown authentication method: " + authMethod);
            }
            snowflakeAPIClient.addDefaultHeader("X-Snowflake-Authorization-Token-Type", snowflakeTokenType);

            String useRole = getProperty(PROPERTY_USE_ROLE);
            if (useRole != null && !useRole.isBlank()) {
                snowflakeAPIClient.addDefaultHeader("X-Snowflake-Role", useRole.trim());
                log.info("SPCS REST API will use role: {}", useRole.trim());
            }

            snowflakeServiceAPI = new ServiceApi(snowflakeAPIClient);
            snowflakeStatementsAPI = new StatementsApi(snowflakeAPIClient);
            log.info("Initialized Snowflake SPCS backend with account URL: {} using authentication type: {}", accountUrl, snowflakeTokenType);
        } catch (Exception e) {
            throw new IllegalStateException("Error initializing Snowflake API client: " + e.getMessage(), e);
        }
    }

    /**
     * Sets up external authentication (keypair or PAT) when running external to SPCS.
     */
    private void setupExternalAuth() {
        username = getProperty(PROPERTY_USERNAME);
        
        String privateRsaKeyPath = getProperty(PROPERTY_PRIVATE_RSA_KEY_PATH);
        String programmaticAccessToken = getProperty(PROPERTY_PROGRAMMATIC_ACCESS_TOKEN);
        
        if (username == null) {
            throw new IllegalStateException("Error in configuration of SPCS backend: proxy.spcs.username not set (required when running external to Snowflake SPCS)");
        }
        
        if (privateRsaKeyPath != null && Files.exists(Paths.get(privateRsaKeyPath))) {
            // Priority 2: Keypair authentication
            keypairAuth = new SpcsKeypairAuth(accountIdentifier, username, accountUrl, privateRsaKeyPath);
            authMethod = AuthMethod.KEYPAIR;
            // JWT token will be generated on-demand via supplier
            // This allows automatic regeneration when the token expires
            jwtTokenSupplier = () -> keypairAuth.generateJwtToken();
            // For REST API, we'll use the supplier to get fresh JWT tokens
            // For ingress, JWT needs to be exchanged for Snowflake Token (handled separately)
            log.info("Using keypair authentication for user: {} with RSA key: {}", username, privateRsaKeyPath);
        } else if (programmaticAccessToken != null && !programmaticAccessToken.isEmpty()) {
            // Priority 3: Programmatic Access Token (PAT) authentication
            // Use supplier for consistency and to support future token rotation scenarios
            authMethod = AuthMethod.PAT;
            final String patToken = programmaticAccessToken; // Final variable for use in lambda
            jwtTokenSupplier = () -> patToken;
            log.info("Using programmatic access token authentication for user: {}", username);
        } else {
            throw new IllegalStateException("Error in configuration of SPCS backend: one of the following must be set: proxy.spcs.private-rsa-key-path or proxy.spcs.programmatic-access-token");
        }
    }

    /**
     * Creates one SPCS service with one container per proxy.
     * ShinyProxy uses one container per spec; no multi-container or shared services.
     */
    @Override
    public Proxy startContainer(Authentication authentication, Container initialContainer, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException {
        Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();
        rContainerBuilder.id(UUID.randomUUID().toString());

        SpcsSpecExtension specExtension = proxySpec.getSpecExtension(SpcsSpecExtension.class);
        String serviceName = generateServiceName(proxy);
        String fullServiceName = database + "." + schema + "." + serviceName;
        String containerName = generateContainerName(proxy.getId(), 0);

        try {
            proxyStartupLogBuilder.startingContainer(0);

            // Build and create service (one container)
            Map<String, String> env = buildEnv(authentication, spec, proxy);
            String serviceSpecYaml = buildServiceSpecYaml(spec, env, specExtension, proxy, serviceName, containerName, authentication);

            ServiceSpecInlineText serviceSpec = new ServiceSpecInlineText();
            serviceSpec.setSpecType("from_inline");
            serviceSpec.setSpecText(serviceSpecYaml);

            Service service = new Service();
            service.setName(serviceName);
            service.setComputePool(specExtension != null ? specExtension.getSpcsComputePool().getValueOrDefault(computePool) : computePool);
            service.setSpec(serviceSpec);
            if (specExtension != null && specExtension.getSpcsExternalAccessIntegrations() != null && !specExtension.getSpcsExternalAccessIntegrations().isEmpty()) {
                service.setExternalAccessIntegrations(specExtension.getSpcsExternalAccessIntegrations());
            }
            rContainerBuilder.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, new BackendContainerName(fullServiceName)), false);
            service.setComment(createCommentMetadata(proxy, rContainerBuilder.build()));

            snowflakeServiceAPI.createService(database, schema, service, "ifNotExists").block();
            slog.info(proxy, String.format("Created Snowflake service: %s", fullServiceName));

            applicationEventPublisher.publishEvent(new NewProxyEvent(proxy.toBuilder().updateContainer(rContainerBuilder.build()).build(), authentication));

            // Wait for container and endpoints
            boolean needsEndpoints = !isRunningInsideSpcs() && spec.getPortMapping() != null && !spec.getPortMapping().isEmpty();
            String waitMessage = needsEndpoints ? "SPCS Container and Endpoints" : "SPCS Container";

            boolean serviceReady = Retrying.retry((currentAttempt, maxAttempts) -> {
                try {
                    List<ServiceContainer> containers = snowflakeServiceAPI.listServiceContainers(database, schema, serviceName).collectList().block();
                    boolean containerRunning = false;
                    if (containers != null && !containers.isEmpty()) {
                        for (ServiceContainer sc : containers) {
                            String scName = sc.getContainerName();
                            if (scName != null && containerName.equalsIgnoreCase(scName)) {
                                String status = sc.getStatus();
                                String svcStatus = sc.getServiceStatus();
                                if ((status != null && ("RUNNING".equals(status) || "UP".equals(status) || "READY".equals(status))) ||
                                    (svcStatus != null && ("RUNNING".equals(svcStatus) || "UP".equals(svcStatus) || "READY".equals(svcStatus)))) {
                                    containerRunning = true;
                                    break;
                                }
                                if (status != null && ("FAILED".equals(status) || "ERROR".equals(status))) {
                                    slog.warn(proxy, String.format("SPCS container %s failed: status=%s", containerName, status));
                                    return new Retrying.Result(false, false);
                                }
                            }
                        }
                    }
                    if (!containerRunning) return Retrying.FAILURE;

                    if (needsEndpoints) {
                        List<ServiceEndpoint> endpoints = snowflakeServiceAPI.showServiceEndpoints(database, schema, serviceName).collectList().block();
                        if (endpoints == null || endpoints.isEmpty()) return Retrying.FAILURE;
                        for (eu.openanalytics.containerproxy.model.spec.PortMapping pm : spec.getPortMapping()) {
                            boolean found = false;
                            for (ServiceEndpoint ep : endpoints) {
                                if (ep.getPort() != null && ep.getPort().equals(pm.getPort()) &&
                                    Boolean.TRUE.equals(ep.getIsPublic()) && "HTTP".equalsIgnoreCase(ep.getProtocol())) {
                                    String url = ep.getIngressUrl();
                                    if (url != null && !url.isEmpty() && !url.toLowerCase().contains("provisioning") &&
                                        !url.toLowerCase().contains("progress") && (url.contains("://") || url.contains("."))) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            if (!found) return Retrying.FAILURE;
                        }
                    }
                    return Retrying.SUCCESS;
                } catch (WebClientResponseException e) {
                    slog.warn(proxy, String.format("Error checking service status: %s", e.getMessage()));
                    return Retrying.FAILURE;
                }
            }, serviceWaitTime, waitMessage, 10, proxy, slog);

            if (!serviceReady) {
                String logInfo = fetchServiceLogsForError(database, schema, serviceName);
                String errorMessage = "Service failed to start" + (needsEndpoints ? " or endpoints failed to provision" : "");
                if (logInfo != null && !logInfo.isEmpty()) {
                    slog.warn(proxy, String.format("Container logs for failed service %s:\n%s", serviceName, logInfo));
                    errorMessage += ". Container logs: " + logInfo;
                }
                throw new ContainerFailedToStartException(errorMessage, null, rContainerBuilder.build());
            }

            proxyStartupLogBuilder.containerStarted(0);

            if (!spec.getImage().isPresent() || spec.getImage().getValue() == null || spec.getImage().getValue().isEmpty()) {
                throw new ContainerFailedToStartException(String.format("Error starting proxy: spec with id '%s' has no 'container-image' configured", proxy.getSpecId()), null, rContainerBuilder.build());
            }
            rContainerBuilder.addRuntimeValue(new RuntimeValue(ContainerImageKey.inst, spec.getImage().getValue()), false);

            Proxy.ProxyBuilder proxyBuilder = proxy.toBuilder();
            Map<Integer, Integer> portBindings = new HashMap<>();
            Container rContainer = rContainerBuilder.build();
            Map<String, URI> targets;
            try {
                targets = setupPortMappingExistingProxy(proxy, rContainer, portBindings);
            } catch (Exception e) {
                throw new ContainerFailedToStartException("Failed to set up port mapping: " + e.getMessage(), e, rContainer);
            }
            proxyBuilder.addTargets(targets);
            String endpointUrl = extractEndpointUrlFromTargets(targets);
            Map<String, String> headers = setupProxyContainerHTTPHeaders(proxy, endpointUrl);
            proxyBuilder.addRuntimeValue(new RuntimeValue(HttpHeadersKey.inst, new HttpHeaders(headers)), true);
            proxyBuilder.updateContainer(rContainer);
            return proxyBuilder.build();
        } catch (ContainerFailedToStartException e) {
            throw e;
        } catch (Throwable e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ContainerFailedToStartException("SPCS container failed to start", e, rContainerBuilder.build());
        }
    }

    /**
     * Builds service YAML spec for a single container.
     */
    private String buildServiceSpecYaml(ContainerSpec spec, Map<String, String> env, SpcsSpecExtension specExtension, Proxy proxy, String serviceName, String containerName, Authentication authentication) throws IOException {
        Map<String, Object> serviceSpec = new HashMap<>();
        Map<String, Object> specSection = new HashMap<>();

        specSection.put("containers", java.util.Collections.singletonList(buildContainerMap(spec, env, containerName, specExtension)));

        List<String> endpointNames = new ArrayList<>();
        if (spec.getPortMapping() != null && !spec.getPortMapping().isEmpty()) {
            List<Map<String, Object>> endpoints = new ArrayList<>();
            for (eu.openanalytics.containerproxy.model.spec.PortMapping pm : spec.getPortMapping()) {
                endpointNames.add(pm.getName());
                Map<String, Object> ep = new HashMap<>();
                ep.put("name", pm.getName());
                ep.put("port", pm.getPort());
                ep.put("protocol", "HTTP");
                ep.put("public", !isRunningInsideSpcs());
                endpoints.add(ep);
            }
            specSection.put("endpoints", endpoints);
        }

        if (specExtension != null && specExtension.getSpcsVolumes() != null && !specExtension.getSpcsVolumes().isEmpty()) {
            List<Map<String, Object>> volumes = new ArrayList<>();
            for (SpcsVolume vol : specExtension.getSpcsVolumes()) {
                Map<String, Object> vm = buildVolumeMap(vol);
                if (vm != null) volumes.add(vm);
            }
            if (!volumes.isEmpty()) specSection.put("volumes", volumes);
        }

        serviceSpec.put("spec", specSection);

        boolean executeAsCaller = authentication instanceof SpcsAuthenticationToken &&
            authentication.getCredentials() != null && !authentication.getCredentials().toString().isBlank();
        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> securityContext = new HashMap<>();
        securityContext.put("executeAsCaller", executeAsCaller);
        capabilities.put("securityContext", securityContext);
        serviceSpec.put("capabilities", capabilities);

        if (!endpointNames.isEmpty()) {
            Map<String, Object> role = new HashMap<>();
            role.put("name", serviceName);
            role.put("endpoints", endpointNames);
            serviceSpec.put("serviceRoles", java.util.Collections.singletonList(role));
        }

        return yamlMapper.writeValueAsString(serviceSpec);
    }

    /**
     * Creates comment metadata for recovery (single container).
     */
    private String createCommentMetadata(Proxy proxy, Container container) {
        Map<String, Object> commentMetadata = new HashMap<>();
        Map<String, Object> proxyEntry = new HashMap<>();
        proxyEntry.put("proxyRuntimeValues", buildProxyRuntimeValues(proxy));
        Map<String, Object> containersMap = new HashMap<>();
        containersMap.put("0", buildContainerMetadata(container));
        proxyEntry.put("containers", containersMap);
        commentMetadata.put("proxies", java.util.Collections.singletonMap(proxy.getId(), proxyEntry));
        try {
            return jsonMapper.writeValueAsString(commentMetadata);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize comment metadata to JSON", e);
        }
    }

    /**
     * Generates a service name for SPCS.
     * Format: SP_SERVICE__{proxyId}
     * One service per proxy; services are not shared between proxies.
     */
    private String generateServiceName(Proxy proxy) {
        String normalizedProxyId = normalizeServiceName(proxy.getId());
        String serviceName = "SP_SERVICE__" + normalizedProxyId;
        if (serviceName.length() > 255) {
            serviceName = serviceName.substring(0, 255);
        }
        return serviceName;
    }
    
    /**
     * Normalizes a name for use in Snowflake service names.
     * Converts to uppercase and replaces invalid characters with underscores.
     * 
     * @param name The name to normalize
     * @return Normalized name
     */
    private String normalizeServiceName(String name) {
        if (name == null) {
            return "";
        }
        // Convert to uppercase and replace invalid chars (non-alphanumeric, not underscore) with underscores
        // Also replace hyphens with underscores for consistency
        return name.toUpperCase().replace("-", "_").replaceAll("[^A-Z0-9_]", "_");
    }

    /**
     * Normalizes a proxyId for use in container names.
     * Container names follow DNS-1123 subdomain rules:
     * - Lowercase alphanumeric characters and hyphens only
     * - Must start and end with alphanumeric character (handled by "sp--" prefix)
     * - No underscores (replaced with dashes)
     * - Other special characters are removed
     * 
     * @param proxyId The proxy ID to normalize (user-configurable, may contain special characters)
     * @return Normalized proxyId suitable for container names
     */
    private String normalizeContainerName(String proxyId) {
        if (proxyId == null) {
            return "";
        }
        // Convert to lowercase, replace underscores with dashes, then remove any remaining non-alphanumeric/dash characters
        return proxyId.toLowerCase()
            .replace("_", "-")
            .replaceAll("[^a-z0-9-]", "");
    }

    /**
     * Generates a container name for use in SPCS service YAML.
     * Format: sp--{proxyId}--{containerIndex}
     * Requirements:
     * - Must start with alpha character (starts with "sp")
     * - Lowercase, no underscores (SPCS doesn't support underscores in container names)
     * - Maximum 63 characters
     * - Uses double dashes as separators
     * 
     * @param proxyId The proxy ID (user-configurable, may contain special characters)
     * @param containerIndex The container index (0-based)
     * @return Container name in format sp--{proxyId}--{containerIndex}
     */
    private String generateContainerName(String proxyId, Integer containerIndex) {
        // Normalize proxyId: sanitize for container name restrictions
        String normalizedProxyId = normalizeContainerName(proxyId);
        // Format: sp--{proxyId}--{containerIndex}
        return "sp--" + normalizedProxyId + "--" + containerIndex;
    }

    /**
     * Builds container-specific metadata including portMappings and runtime values.
     * Note: containerId is not stored as it's a random UUID that can be regenerated during recovery.
     * 
     * @param initialContainer The container to extract metadata from
     * @return Map containing container metadata
     */
    private Map<String, Object> buildContainerMetadata(Container initialContainer) {
        Map<String, Object> containerMetadata = new HashMap<>();
        
        // Store container-specific runtime values
        Map<String, String> containerRuntimeValues = new HashMap<>();
        initialContainer.getRuntimeValues().values().stream()
            .filter(runtimeValue -> runtimeValue.getKey().isContainerSpecific())
            .forEach(runtimeValue -> {
                if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
                    containerRuntimeValues.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
                }
            });
        containerMetadata.put("runtimeValues", containerRuntimeValues);
        
        // Store PortMappingsKey (container-specific)
        RuntimeValue portMappingsValue = initialContainer.getRuntimeValues().get(PortMappingsKey.inst);
        if (portMappingsValue != null) {
            String portMappingsJson = portMappingsValue.getKey().serializeToString(portMappingsValue.getObject());
            containerMetadata.put("portMappings", portMappingsJson);
        }
        
        return containerMetadata;
    }

    /**
     * Builds proxy-level runtime values map.
     * 
     * @param proxy The proxy to extract runtime values from
     * @return Map containing proxy-level runtime values
     */
    private Map<String, String> buildProxyRuntimeValues(Proxy proxy) {
        Map<String, String> proxyRuntimeValues = new HashMap<>();
        proxy.getRuntimeValues().values().stream()
            .filter(runtimeValue -> !runtimeValue.getKey().isContainerSpecific())
            .forEach(runtimeValue -> {
                if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
                    proxyRuntimeValues.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
                }
            });
        return proxyRuntimeValues;
    }

    /**
     * Builds a container map structure for YAML serialization.
     * @param spec The container spec
     * @param env Environment variables map
     * @param containerName The name for this container (sp--{proxyId}--{containerIndex} format)
     * @param specExtension The SPCS spec extension (contains secrets configuration)
     * @return Map representing the container structure
     */
    private Map<String, Object> buildContainerMap(ContainerSpec spec, Map<String, String> env, String containerName, SpcsSpecExtension specExtension) {
        Map<String, Object> container = new HashMap<>();
        container.put("name", containerName);
        
        // Image from Snowflake image repository
        if (!spec.getImage().isPresent() || spec.getImage().getValue() == null || spec.getImage().getValue().isEmpty()) {
            throw new IllegalStateException("Error building container spec: 'container-image' is required but not specified. Please configure 'container-image' in your proxy spec.");
        }
        String image = spec.getImage().getValue();
        image = formatSnowflakeImageName(image);
        container.put("image", image);
        
        // Command
        if (spec.getCmd().isPresent() && !spec.getCmd().getValue().isEmpty()) {
            container.put("command", spec.getCmd().getValue());
        }
        
        // Environment variables (must be a map, not a list)
        if (!env.isEmpty()) {
            container.put("env", new HashMap<>(env));
        }
        
        // Resources (memory and CPU - requests and limits)
        Map<String, Object> resources = new HashMap<>();
        
        // Build requests map
        Map<String, Object> requests = new HashMap<>();
        boolean hasRequests = false;
        if (spec.getMemoryRequest().isPresent()) {
            String memoryValue = formatMemoryValue(spec.getMemoryRequest().getValue());
            requests.put("memory", memoryValue);
            hasRequests = true;
        }
        if (spec.getCpuRequest().isPresent()) {
            String cpuValue = formatCpuValue(spec.getCpuRequest().getValue());
            requests.put("cpu", cpuValue);
            hasRequests = true;
        }
        if (hasRequests) {
            resources.put("requests", requests);
        }
        
        // Build limits map
        Map<String, Object> limits = new HashMap<>();
        boolean hasLimits = false;
        if (spec.getMemoryLimit().isPresent()) {
            String memoryValue = formatMemoryValue(spec.getMemoryLimit().getValue());
            limits.put("memory", memoryValue);
            hasLimits = true;
        }
        if (spec.getCpuLimit().isPresent()) {
            String cpuValue = formatCpuValue(spec.getCpuLimit().getValue());
            limits.put("cpu", cpuValue);
            hasLimits = true;
        }
        if (hasLimits) {
            resources.put("limits", limits);
        }
        
        // Only add resources if we have at least requests or limits
        if (!resources.isEmpty()) {
            container.put("resources", resources);
        }
        
        // Secrets (optional list)
        if (specExtension != null && specExtension.getSpcsSecrets() != null && !specExtension.getSpcsSecrets().isEmpty()) {
            List<Map<String, Object>> secrets = new ArrayList<>();
            for (SpcsSecret secret : specExtension.getSpcsSecrets()) {
                Map<String, Object> secretMap = new HashMap<>();
                
                // Build snowflakeSecret object (objectName OR objectReference)
                Map<String, Object> snowflakeSecret = new HashMap<>();
                if (secret.getObjectName() != null && !secret.getObjectName().isEmpty()) {
                    snowflakeSecret.put("objectName", secret.getObjectName());
                } else if (secret.getObjectReference() != null && !secret.getObjectReference().isEmpty()) {
                    snowflakeSecret.put("objectReference", secret.getObjectReference());
                } else {
                    // Skip this secret if neither objectName nor objectReference is specified
                    continue;
                }
                secretMap.put("snowflakeSecret", snowflakeSecret);
                
                // Add directoryPath OR envVarName (mutually exclusive)
                if (secret.getDirectoryPath() != null && !secret.getDirectoryPath().isEmpty()) {
                    secretMap.put("directoryPath", secret.getDirectoryPath());
                } else if (secret.getEnvVarName() != null && !secret.getEnvVarName().isEmpty()) {
                    secretMap.put("envVarName", secret.getEnvVarName());
                    // secretKeyRef is required when using envVarName
                    if (secret.getSecretKeyRef() != null && !secret.getSecretKeyRef().isEmpty()) {
                        secretMap.put("secretKeyRef", secret.getSecretKeyRef());
                    }
                }
                // If neither directoryPath nor envVarName is specified, skip this secret
                if (!secretMap.containsKey("directoryPath") && !secretMap.containsKey("envVarName")) {
                    continue;
                }
                
                secrets.add(secretMap);
            }
            if (!secrets.isEmpty()) {
                container.put("secrets", secrets);
            }
        }
        
        // Readiness probe (optional)
        if (specExtension != null && specExtension.getSpcsReadinessProbe() != null) {
            SpcsReadinessProbe readinessProbe = specExtension.getSpcsReadinessProbe();
            if (readinessProbe.getPort() != null && readinessProbe.getPath() != null && !readinessProbe.getPath().isEmpty()) {
                Map<String, Object> readinessProbeMap = new HashMap<>();
                readinessProbeMap.put("port", readinessProbe.getPort());
                readinessProbeMap.put("path", readinessProbe.getPath());
                container.put("readinessProbe", readinessProbeMap);
            }
        }
        
        // Volume mounts (optional list) - parsed from ContainerSpec.volumes
        if (spec.getVolumes() != null && spec.getVolumes().isPresent() && !spec.getVolumes().getValue().isEmpty()) {
            List<Map<String, Object>> volumeMounts = new ArrayList<>();
            for (String volumeMountString : spec.getVolumes().getValue()) {
                SpcsVolumeMount volumeMount = parseVolumeMount(volumeMountString);
                if (volumeMount != null) {
                    Map<String, Object> volumeMountMap = new HashMap<>();
                    volumeMountMap.put("name", volumeMount.getName());
                    volumeMountMap.put("mountPath", volumeMount.getMountPath());
                    volumeMounts.add(volumeMountMap);
                }
            }
            if (!volumeMounts.isEmpty()) {
                container.put("volumeMounts", volumeMounts);
            }
        }
        
        return container;
    }
    
    /**
     * Builds a volume map structure for YAML serialization from SpcsVolume object.
     * 
     * @param volume The SpcsVolume object
     * @return Map representing the volume structure, or null if invalid
     */
    private Map<String, Object> buildVolumeMap(SpcsVolume volume) {
        if (volume == null || volume.getName() == null || volume.getSource() == null) {
            return null;
        }
        
        Map<String, Object> volumeMap = new HashMap<>();
        volumeMap.put("name", volume.getName());
        volumeMap.put("source", volume.getSource());
        
        if (volume.getSize() != null && !volume.getSize().isEmpty()) {
            // Normalize size format for block and memory volumes - Snowflake requires "Gi" suffix
            String normalizedSize = normalizeVolumeSize(volume.getSize(), volume.getSource());
            volumeMap.put("size", normalizedSize);
        }
        
        if (volume.getUid() != null) {
            volumeMap.put("uid", volume.getUid());
        }
        
        if (volume.getGid() != null) {
            volumeMap.put("gid", volume.getGid());
        }
        
        if (volume.getBlockConfig() != null) {
            Map<String, Object> blockConfigMap = new HashMap<>();
            SpcsBlockConfig blockConfig = volume.getBlockConfig();
            
            if (blockConfig.getInitialContents() != null && blockConfig.getInitialContents().getFromSnapshot() != null) {
                Map<String, Object> initialContentsMap = new HashMap<>();
                initialContentsMap.put("fromSnapshot", blockConfig.getInitialContents().getFromSnapshot());
                blockConfigMap.put("initialContents", initialContentsMap);
            }
            
            if (blockConfig.getIops() != null) {
                blockConfigMap.put("iops", blockConfig.getIops());
            }
            
            if (blockConfig.getThroughput() != null) {
                blockConfigMap.put("throughput", blockConfig.getThroughput());
            }
            
            if (blockConfig.getEncryption() != null) {
                blockConfigMap.put("encryption", blockConfig.getEncryption());
            }
            
            // snapshotOnDelete: default to true for volumes managed by ShinyProxy
            // This allows safe deletion of services with volumes
            Boolean snapshotOnDelete = blockConfig.getSnapshotOnDelete();
            if (snapshotOnDelete == null) {
                snapshotOnDelete = true; // Default to true for ShinyProxy-managed volumes
            }
            blockConfigMap.put("snapshotOnDelete", snapshotOnDelete);
            
            if (!blockConfigMap.isEmpty()) {
                volumeMap.put("blockConfig", blockConfigMap);
            }
        }
        
        if (volume.getStageConfig() != null) {
            Map<String, Object> stageConfigMap = new HashMap<>();
            SpcsStageConfig stageConfig = volume.getStageConfig();
            
            if (stageConfig.getName() != null) {
                stageConfigMap.put("name", stageConfig.getName());
            }
            
            if (stageConfig.getMetadataCache() != null) {
                stageConfigMap.put("metadataCache", stageConfig.getMetadataCache());
            }
            
            if (stageConfig.getResources() != null) {
                Map<String, Object> resourcesMap = new HashMap<>();
                boolean hasResources = false;
                
                if (stageConfig.getResources().getRequests() != null) {
                    Map<String, Object> requestsMap = new HashMap<>();
                    if (stageConfig.getResources().getRequests().getMemory() != null) {
                        requestsMap.put("memory", stageConfig.getResources().getRequests().getMemory());
                        hasResources = true;
                    }
                    if (stageConfig.getResources().getRequests().getCpu() != null) {
                        requestsMap.put("cpu", stageConfig.getResources().getRequests().getCpu());
                        hasResources = true;
                    }
                    if (!requestsMap.isEmpty()) {
                        resourcesMap.put("requests", requestsMap);
                    }
                }
                
                if (stageConfig.getResources().getLimits() != null) {
                    Map<String, Object> limitsMap = new HashMap<>();
                    if (stageConfig.getResources().getLimits().getMemory() != null) {
                        limitsMap.put("memory", stageConfig.getResources().getLimits().getMemory());
                        hasResources = true;
                    }
                    if (stageConfig.getResources().getLimits().getCpu() != null) {
                        limitsMap.put("cpu", stageConfig.getResources().getLimits().getCpu());
                        hasResources = true;
                    }
                    if (!limitsMap.isEmpty()) {
                        resourcesMap.put("limits", limitsMap);
                    }
                }
                
                if (hasResources && !resourcesMap.isEmpty()) {
                    stageConfigMap.put("resources", resourcesMap);
                }
            }
            
            if (!stageConfigMap.isEmpty()) {
                volumeMap.put("stageConfig", stageConfigMap);
            }
        }
        
        return volumeMap;
    }
    
    /**
     * Normalizes volume size format for Snowflake SPCS requirements.
     * For block and memory volumes, Snowflake requires the size to end with "Gi" suffix.
     * If a plain number is provided (e.g., "20"), it will be converted to "20Gi".
     * 
     * @param size The size value from configuration
     * @param source The volume source type (block, memory, stage, local)
     * @return Normalized size string with proper unit suffix
     */
    private String normalizeVolumeSize(String size, String source) {
        if (size == null || size.isEmpty()) {
            return size;
        }
        
        String trimmed = size.trim();
        
        // For block and memory volumes, Snowflake requires "Gi" suffix
        if ("block".equals(source) || "memory".equals(source)) {
            // Check if it's already a valid format (ends with Gi, case-insensitive)
            if (trimmed.toLowerCase().endsWith("gi")) {
                return trimmed; // Already has Gi suffix
            }
            
            // Check if it's a plain number (optionally with whitespace)
            try {
                // Try to parse as integer
                String numberPart = trimmed.replaceAll("\\s+", "");
                Integer.parseInt(numberPart);
                // If successful, it's a plain number - append "Gi"
                return numberPart + "Gi";
            } catch (NumberFormatException e) {
                // Not a plain number, might have other units - throw error with helpful message
                throw new IllegalStateException(String.format(
                    "Invalid size format '%s' for %s volume. Block and memory volumes require size to be an integer with 'Gi' suffix (e.g., '20Gi'). Got: '%s'", 
                    size, source, trimmed));
            }
        }
        
        // For other volume types (local, stage), return as-is
        return trimmed;
    }
    
    /**
     * Parses a volume mount string in ECS-style format: "volume-name:/mount/path"
     * 
     * @param volumeMountString The volume mount string (format: "volume-name:/mount/path")
     * @return SpcsVolumeMount object, or null if parsing fails
     */
    private SpcsVolumeMount parseVolumeMount(String volumeMountString) {
        if (volumeMountString == null || volumeMountString.isEmpty()) {
            return null;
        }
        
        int colonIndex = volumeMountString.indexOf(':');
        if (colonIndex <= 0 || colonIndex >= volumeMountString.length() - 1) {
            log.warn("Invalid volume mount format (expected 'volume-name:/mount/path'): {}", volumeMountString);
            return null;
        }
        
        String volumeName = volumeMountString.substring(0, colonIndex).trim();
        String mountPath = volumeMountString.substring(colonIndex + 1).trim();
        
        if (volumeName.isEmpty() || mountPath.isEmpty()) {
            log.warn("Invalid volume mount format (volume name or mount path is empty): {}", volumeMountString);
            return null;
        }
        
        return SpcsVolumeMount.builder()
            .name(volumeName)
            .mountPath(mountPath)
            .build();
    }
    
    /**
     * Formats a Snowflake image name to the correct format.
     * If the image contains a snowflakecomputing.com domain, removes it and extracts the path.
     * Expected format: /<database>/<schema>/<repository>/<image>:<tag>
     * 
     * @param image The image name (e.g., "org-account.registry.snowflakecomputing.com/path/to/image:tag" or "/path/to/image:tag")
     * @return Formatted image name with domain removed (e.g., "/path/to/image:tag")
     */
    private String formatSnowflakeImageName(String image) {
        if (image == null || image.isEmpty()) {
            return image;
        }
        
        // If image contains snowflakecomputing.com, extract the path part after the domain
        if (image.contains("snowflakecomputing.com")) {
            // Find the path part after the domain (look for / after snowflakecomputing.com)
            int domainIndex = image.indexOf("snowflakecomputing.com");
            int pathStart = image.indexOf('/', domainIndex);
            if (pathStart >= 0) {
                // Extract path starting from /
                return image.substring(pathStart);
            }
            // If no / found after domain, return as-is (unlikely but handle gracefully)
            return image;
        }
        
        // If image already starts with /, it's already in the correct format
        // Otherwise, return as-is (might be a different format)
        return image;
    }

    /**
     * Formats a memory value for Snowflake SPCS.
     * If the value already has a unit (g, gi, m, mi, t, ti), returns it as-is.
     * Otherwise, assumes the value is in megabytes and converts to appropriate unit.
     * 
     * Supported units: g, gi, m, mi, t, ti (Snowflake accepts capitalized forms like Gi, Mi)
     * Converts large values to larger units (e.g., 2048m -> 2Gi, 1024m -> 1Gi)
     * 
     * @param memoryValue The memory value (e.g., "2048" or "2Gi")
     * @return Formatted memory value with capitalized unit (e.g., "2Gi" or "512Mi")
     */
    private String formatMemoryValue(String memoryValue) {
        if (memoryValue == null || memoryValue.isEmpty()) {
            return memoryValue;
        }
        // Check if value already has a supported unit (case-insensitive)
        // Supported units per Snowflake docs: M, Mi, G, Gi (uppercase first letter required)
        String lowerValue = memoryValue.toLowerCase();
        if (lowerValue.matches(".*(m|mi|g|gi)$")) {
            // Already has a unit, normalize to uppercase first letter
            // Extract the numeric part and unit
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)([mM][iI]?|[gG][iI]?)$");
            java.util.regex.Matcher matcher = pattern.matcher(memoryValue);
            if (matcher.matches()) {
                String numberPart = matcher.group(1);
                String unitPart = matcher.group(2).toLowerCase();
                // Normalize unit: m -> M, mi -> Mi, g -> G, gi -> Gi
                String normalizedUnit;
                if (unitPart.equals("m")) {
                    normalizedUnit = "M";
                } else if (unitPart.equals("mi")) {
                    normalizedUnit = "Mi";
                } else if (unitPart.equals("g")) {
                    normalizedUnit = "G";
                } else if (unitPart.equals("gi")) {
                    normalizedUnit = "Gi";
                } else {
                    normalizedUnit = unitPart; // Should not happen
                }
                return numberPart + normalizedUnit;
            }
            // If regex doesn't match, return as-is (shouldn't happen)
            return memoryValue;
        }
        
        // Parse the numeric value
        try {
            double value = Double.parseDouble(memoryValue);
            
            // Convert megabytes to appropriate unit with capitalized format
            // 1024 MB = 1 GiB, 1000 MB = 1 GB
            // Use binary units (Gi, Mi) for consistency with Kubernetes conventions
            if (value >= 1024) {
                // Convert to GiB (gibibytes)
                double gib = value / 1024;
                // Format to remove unnecessary decimals
                if (gib == Math.floor(gib)) {
                    return String.valueOf((long) gib) + "Gi";
                } else {
                    // Round to 2 decimal places for large values
                    return String.format("%.2fGi", gib).replaceAll("\\.?0+$", "");
                }
            } else {
                // Use MiB (mebibytes) for values < 1024 MB
                return (long) value + "Mi";
            }
        } catch (NumberFormatException e) {
            // If parsing fails, just append "Mi" as fallback
            return memoryValue + "Mi";
        }
    }

    /**
     * Formats a CPU value for Snowflake SPCS.
     * CPU can be specified as:
     * - Millicores with "m" suffix (e.g., "500m", "1000m") - returned as-is
     *   According to Snowflake docs, fractional CPUs can be expressed as "m" (e.g., 500m = 0.5 CPUs)
     * - Fractional CPUs directly (e.g., "0.5", "1", "2.5") - returned as-is
     * - Whole numbers >= 1 without unit - treated as millicores and converted to fractional CPUs
     *   (e.g., "256" -> "0.256", "1000" -> "1", "1500" -> "1.5")
     * 
     * Reference: https://docs.snowflake.com/en/developer-guide/snowpark-container-services/specification-reference#containers-resources-field
     * 
     * @param cpuValue The CPU value (e.g., "256", "1000", "0.5", "500m", or "1000m")
     * @return Formatted CPU value (e.g., "0.256", "1", "0.5", "500m", or "1000m")
     */
    private String formatCpuValue(String cpuValue) {
        if (cpuValue == null || cpuValue.isEmpty()) {
            return cpuValue;
        }
        // Check if value already has "m" unit (millicores)
        String lowerValue = cpuValue.toLowerCase();
        if (lowerValue.endsWith("m")) {
            // Already has millicore unit, return as-is (Snowflake accepts "m" suffix for fractional CPUs)
            return cpuValue;
        }
        
        // Parse the numeric value
        try {
            double value = Double.parseDouble(cpuValue);
            
            // If value < 1, assume it's already in fractional CPU format (e.g., 0.5)
            if (value < 1.0) {
                return cpuValue;
            }
            
            // Value >= 1, assume it's in millicores and convert to fractional CPUs
            // (e.g., 256 millicores = 0.256 CPUs, 1000 millicores = 1 CPU)
            double cpus = value / 1000.0;
            // Format to remove unnecessary decimals
            if (cpus == Math.floor(cpus)) {
                return String.valueOf((long) cpus);
            } else {
                // Round to 3 decimal places and remove trailing zeros
                return String.format("%.3f", cpus).replaceAll("\\.?0+$", "");
            }
        } catch (NumberFormatException e) {
            // If parsing fails, return as-is
            return cpuValue;
        }
    }


    @Override
    public void pauseProxy(Proxy proxy) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        for (Container container : proxy.getContainers()) {
            String fullServiceName = container.getRuntimeValue(BackendContainerNameKey.inst);
            if (fullServiceName != null) {
                // Parse full service name: database.schema.service
                String[] parts = fullServiceName.split("\\.");
                if (parts.length == 3) {
                    String serviceDb = parts[0];
                    String serviceSchema = parts[1];
                    String serviceName = parts[2];
                    try {
                        snowflakeServiceAPI.suspendService(serviceDb, serviceSchema, serviceName, false).block();
                        slog.info(proxy, String.format("Suspended Snowflake service: %s", fullServiceName));
                    } catch (WebClientResponseException e) {
                        slog.warn(proxy, String.format("Error suspending Snowflake service %s: %s", fullServiceName, e.getMessage()));
                        throw new ContainerProxyException("Failed to suspend Snowflake service: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    @Override
    public Proxy resumeProxy(Authentication user, Proxy proxy, ProxySpec proxySpec) throws ProxyFailedToStartException {
        if (Thread.currentThread().isInterrupted()) {
            throw new ProxyFailedToStartException("Thread interrupted", null, proxy);
        }
        
        // All containers in a proxy share the same service, so we only need to resume once
        Container firstContainer = proxy.getContainers().isEmpty() ? null : proxy.getContainers().get(0);
        if (firstContainer == null) {
            throw new ProxyFailedToStartException("Proxy has no containers", null, proxy);
        }
        
        String fullServiceName = firstContainer.getRuntimeValue(BackendContainerNameKey.inst);
        if (fullServiceName == null) {
            throw new ProxyFailedToStartException("Service name not found in container runtime values", null, proxy);
        }
        
        // Parse full service name: database.schema.service
        String[] parts = fullServiceName.split("\\.");
        if (parts.length != 3) {
            throw new ProxyFailedToStartException("Invalid service name format: " + fullServiceName, null, proxy);
        }
        
        String serviceDb = parts[0];
        String serviceSchema = parts[1];
        String serviceName = parts[2];
        
        try {
            // Resume the service
            snowflakeServiceAPI.resumeService(serviceDb, serviceSchema, serviceName, false).block();
            slog.info(proxy, String.format("Resuming Snowflake service: %s", fullServiceName));
            
            // Wait for service to be ready (similar to start container logic)
            boolean serviceReady = Retrying.retry((currentAttempt, maxAttempts) -> {
                try {
                    List<ServiceContainer> containers = snowflakeServiceAPI.listServiceContainers(serviceDb, serviceSchema, serviceName).collectList().block();
                    if (containers == null || containers.isEmpty()) {
                        return Retrying.FAILURE;
                    }
                    
                    // Check if all containers in the service are running/ready
                    boolean allRunning = true;
                    for (ServiceContainer serviceContainer : containers) {
                        String status = serviceContainer.getStatus();
                        String serviceStatus = serviceContainer.getServiceStatus();
                        if (status != null && (status.equals("RUNNING") || status.equals("UP") || status.equals("READY"))) {
                            continue;
                        }
                        if (serviceStatus != null && (serviceStatus.equals("RUNNING") || serviceStatus.equals("UP") || serviceStatus.equals("READY"))) {
                            continue;
                        }
                        if (status != null && (status.equals("FAILED") || status.equals("ERROR"))) {
                            slog.warn(proxy, String.format("SPCS container failed during resume: status=%s, message=%s", status, serviceContainer.getMessage()));
                            return new Retrying.Result(false, false);
                        }
                        // Container is not running yet
                        allRunning = false;
                        break;
                    }
                    
                    if (!allRunning) {
                        return Retrying.FAILURE;
                    }
                    
                    return Retrying.SUCCESS;
                } catch (WebClientResponseException e) {
                    slog.warn(proxy, String.format("Error checking service containers during resume: %s", e.getMessage()));
                    return Retrying.FAILURE;
                }
            }, serviceWaitTime, "SPCS Service Resume", 10, proxy, slog);
            
            if (!serviceReady) {
                throw new ProxyFailedToStartException("Service did not resume within timeout period", null, proxy);
            }
            
            // On resume we only re-apply HTTP headers (e.g. fresh ingress Authorization). Ingress/private URL is not recalculated.
            boolean needsEndpoints = !isRunningInsideSpcs();
            if (needsEndpoints) {
                Map<String, URI> existingTargets = proxy.getTargets();
                if (existingTargets != null && !existingTargets.isEmpty()) {
                    Proxy.ProxyBuilder proxyBuilder = proxy.toBuilder();
                    String endpointUrl = extractEndpointUrlFromTargets(existingTargets);
                    Map<String, String> headers = setupProxyContainerHTTPHeaders(proxy, endpointUrl);
                    proxyBuilder.addRuntimeValue(new RuntimeValue(HttpHeadersKey.inst, new HttpHeaders(headers)), true);
                    slog.info(proxy, String.format("Setting up Authorization header for SPCS ingress access on resume (auth method: %s)", snowflakeTokenType != null ? snowflakeTokenType : authMethod.name()));
                    slog.info(proxy, String.format("Resumed Snowflake service: %s", fullServiceName));
                    return proxyBuilder.build();
                }
            }
            slog.info(proxy, String.format("Resumed Snowflake service: %s", fullServiceName));
            return proxy;
            
        } catch (WebClientResponseException e) {
            slog.warn(proxy, String.format("Error resuming Snowflake service %s: %s", fullServiceName, e.getMessage()));
            throw new ProxyFailedToStartException("Failed to resume Snowflake service: " + e.getMessage(), e, proxy);
        }
    }

    @Override
    public Boolean supportsPause() {
        return true;
    }

    @Override
    protected void doStopProxy(Proxy proxy) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        for (Container container : proxy.getContainers()) {
            String fullServiceName = container.getRuntimeValueOrNull(BackendContainerNameKey.inst);
            if (fullServiceName != null) {
                // Parse full service name: database.schema.service
                String[] parts = fullServiceName.split("\\.");
                if (parts.length == 3) {
                    String serviceDb = parts[0];
                    String serviceSchema = parts[1];
                    String serviceName = parts[2];
                    try {
                        deleteService(serviceDb, serviceSchema, serviceName);
                        slog.info(proxy, String.format("Deleted Snowflake service: %s", fullServiceName));
                    } catch (WebClientResponseException e) {
                        slog.warn(proxy, String.format("Error deleting Snowflake service %s: %s", fullServiceName, e.getMessage()));
                    }
                }
            }
        }

        // Wait for service to be stopped
        boolean isInactive = Retrying.retry((currentAttempt, maxAttempts) -> {
            for (Container container : proxy.getContainers()) {
                String fullServiceName = container.getRuntimeValueOrNull(BackendContainerNameKey.inst);
                if (fullServiceName != null) {
                    String[] parts = fullServiceName.split("\\.");
                    if (parts.length == 3) {
                        try {
                            List<ServiceContainer> containers = snowflakeServiceAPI.listServiceContainers(parts[0], parts[1], parts[2]).collectList().block();
                            // If we can list containers, service is not fully stopped
                            if (containers != null && !containers.isEmpty()) {
                            // Check if containers are stopped/suspended
                            boolean allStopped = true;
                            for (ServiceContainer serviceContainer : containers) {
                                String status = serviceContainer.getStatus();
                                String serviceStatus = serviceContainer.getServiceStatus();
                                if (status != null && !status.equals("SUSPENDED") && !status.equals("STOPPED") && !status.equals("DELETED")) {
                                    allStopped = false;
                                    break;
                                }
                                if (serviceStatus != null && !serviceStatus.equals("SUSPENDED") && !serviceStatus.equals("STOPPED") && !serviceStatus.equals("DELETED")) {
                                    allStopped = false;
                                    break;
                                }
                            }
                                if (!allStopped) {
                                    return Retrying.FAILURE;
                                }
                            }
                        } catch (WebClientResponseException e) {
                            // Service might be deleted already
                            if (e.getStatusCode().value() == 404) {
                                return Retrying.SUCCESS;
                            }
                        }
                    }
                }
            }
            return Retrying.SUCCESS;
        }, serviceWaitTime, "Stopping SPCS Service", 0, proxy, slog);

        if (!isInactive) {
            slog.warn(proxy, "Service did not get into stopping state");
        }
    }

    @Override
    public void stopProxies(Collection<Proxy> proxies) {
        for (Proxy proxy : proxies) {
            try {
                stopProxy(proxy);
            } catch (Exception e) {
                log.error("Error stopping proxy", e);
            }
        }
    }

    @Override
    protected String getPropertyPrefix() {
        return PROPERTY_PREFIX;
    }

    @Override
    public List<ExistingContainerInfo> scanExistingContainers() {
        log.info("Scanning for existing SPCS services to recover in database schema {}.{}", database, schema);
        ArrayList<ExistingContainerInfo> containers = new ArrayList<>();
        
        try {
            List<Service> services = snowflakeServiceAPI.listServices(database, schema, "SP_SERVICE__%", null, null, null).collectList().block();
            java.util.Set<Service> allServices = new java.util.HashSet<>();
            if (services != null) {
                allServices.addAll(services);
            }
            
            if (allServices.isEmpty()) {
                log.info("No existing SPCS services found to recover");
                return containers;
            }
            
            log.info("Found {} SPCS service(s) to scan for recovery", allServices.size());
            
            for (Service service : allServices) {
                String serviceName = service.getName();
                if (serviceName == null || !serviceName.startsWith("SP_SERVICE__")) {
                    continue;
                }
                
                // Use service status to decide recovery; list is expected to return status
                String status = service.getStatus();
                if (status == null) {
                    deleteServiceLogReason(serviceName, "Service status unknown (null)");
                    continue;
                }
                String statusUpper = status.toUpperCase(java.util.Locale.ROOT);
                if ("FAILED".equals(statusUpper) || "INTERNAL_ERROR".equals(statusUpper)) {
                    deleteServiceLogReason(serviceName, "Service status: " + status);
                    continue;
                }
                // DONE/DELETING/DELETED: service no longer exists (DONE is typically for job services)
                if ("DONE".equals(statusUpper) || "DELETING".equals(statusUpper) || "DELETED".equals(statusUpper)) {
                    continue;
                }
                
                // PENDING, RUNNING = up; SUSPENDED, SUSPENDING = paused
                ProxyStatus recoveredStatus = ("SUSPENDED".equals(statusUpper) || "SUSPENDING".equals(statusUpper))
                    ? ProxyStatus.Paused : null;
                
                // Fetch service and extract metadata
                Map<String, String> serviceMetadata = new HashMap<>();
                String specText = null;
                
                if (!fetchServiceImageAndMetadata(serviceName, serviceMetadata)) {
                    deleteServiceLogReason(serviceName, "Error fetching service metadata");
                    continue;
                }
                
                // Get spec text for container extraction
                try {
                    Service fullService = snowflakeServiceAPI.fetchService(database, schema, serviceName).block();
                    ServiceSpec serviceSpec = fullService.getSpec();
                    if (serviceSpec instanceof ServiceSpecInlineText) {
                        specText = ((ServiceSpecInlineText) serviceSpec).getSpecText();
                    }
                } catch (WebClientResponseException e) {
                    log.warn("Error fetching service spec for recovery: {}", e.getMessage());
                }
                
                // Validate and delete if unrecoverable (uses comment metadata as primary source)
                if (!validateAndDeleteIfUnrecoverable(serviceName, serviceMetadata)) {
                    continue; // Service was deleted
                }
                
                // Extract container metadata from service comment (supports multiple proxies per service)
                // Structure: {"proxies": {"proxyId": {"proxyRuntimeValues": {...}, "containers": {...}}}}
                Map<String, Object> allContainersMetadata = new HashMap<>(); // All containers from all proxies
                Map<String, String> allProxyRuntimeValues = new HashMap<>(); // Proxy runtime values keyed by proxyId
                try {
                    Service fullService = snowflakeServiceAPI.fetchService(database, schema, serviceName).block();
                    String comment = fullService.getComment();
                    if (comment != null && !comment.isEmpty()) {
                        Map<String, Object> commentMetadata = jsonMapper.readValue(comment, new TypeReference<Map<String, Object>>() {});
                        if (commentMetadata != null) {
                            // Extract proxies map
                            if (commentMetadata.containsKey("proxies")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> proxies = (Map<String, Object>) commentMetadata.get("proxies");
                                if (proxies != null) {
                                    // Iterate through all proxies and extract their containers and runtime values
                                    for (Map.Entry<String, Object> proxyEntry : proxies.entrySet()) {
                                        String proxyId = proxyEntry.getKey();
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> proxyData = (Map<String, Object>) proxyEntry.getValue();
                                        
                                        // Extract proxy-level runtime values for this proxy
                                        if (proxyData.containsKey("proxyRuntimeValues")) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, String> proxyValues = (Map<String, String>) proxyData.get("proxyRuntimeValues");
                                            if (proxyValues != null) {
                                                // Store with proxyId prefix to avoid conflicts
                                                for (Map.Entry<String, String> entry : proxyValues.entrySet()) {
                                                    allProxyRuntimeValues.put(proxyId + ":" + entry.getKey(), entry.getValue());
                                                }
                                            }
                                        }
                                        
                                        // Extract containers for this proxy
                                        // Containers are keyed by container index (e.g., "0", "1")
                                        // We need to map them to container names for lookup during recovery
                                        if (proxyData.containsKey("containers")) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> proxyContainers = (Map<String, Object>) proxyData.get("containers");
                                            if (proxyContainers != null) {
                                                // Convert container index keys to container names for lookup
                                                // Container name format: sp--{proxyId}--{containerIndex}
                                                for (Map.Entry<String, Object> containerEntry : proxyContainers.entrySet()) {
                                                    String containerIndex = containerEntry.getKey();
                                                    // Build container name from proxyId and index (use same normalization as generateContainerName)
                                                    String normalizedProxyId = normalizeContainerName(proxyId);
                                                    String expectedContainerName = "sp--" + normalizedProxyId + "--" + containerIndex;
                                                    allContainersMetadata.put(expectedContainerName, containerEntry.getValue());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error extracting container metadata from comment for service {}: {}", serviceName, e.getMessage());
                }
                
                // Extract all containers from the service YAML
                // Note: We pass all containers metadata, and extractContainersFromSpecText will match by container name
                List<Map<String, String>> containerMetadataList = extractContainersFromSpecText(specText, allProxyRuntimeValues, allContainersMetadata);
                
                // recoveredStatus already set from service status above (SUSPENDED/SUSPENDING -> Paused)
                
                // Build ExistingContainerInfo for each container
                for (Map<String, String> containerMetadata : containerMetadataList) {
                    String containerName = containerMetadata.get("containerName");
                    // Container name format: sp--{proxyId}--{containerIndex}
                    // Container metadata (including portMappings, runtimeValues) is already merged
                    // in extractContainersFromSpecText. containerId is a random UUID that can be regenerated.
                    String containerId = UUID.randomUUID().toString();
                    
                    ExistingContainerInfo containerInfo = buildExistingContainerInfo(containerId, serviceName, containerMetadata, recoveredStatus);
                containers.add(containerInfo);
                    String recoveredUserId = containerMetadata.get(UserIdKey.inst.getKeyAsLabel());
                    String recoveredProxyId = containerMetadata.get(ProxyIdKey.inst.getKeyAsLabel());
                    String recoveredSpecId = containerMetadata.get(ProxySpecIdKey.inst.getKeyAsLabel());
                    log.info("Added container {} (id: {}) from service {} to recovery list (userId: {}, proxyId: {}, specId: {})", 
                                containerName, containerId, serviceName, recoveredUserId, recoveredProxyId, recoveredSpecId);
                }
            }
            
            log.info("Completed scanning SPCS services: {} recoverable service(s) found", containers.size());
        } catch (WebClientResponseException e) {
            log.error("Error listing services for scan: {}", e.getMessage(), e);
        }
        
        return containers;
    }

    /**
     * Deletes a service with error handling and logging.
     */
    private void deleteServiceLogReason(String serviceName, String reason) {
        log.warn("Deleting service {} due to: {}", serviceName, reason);
        try {
            deleteService(database, schema, serviceName);
            log.info("Deleted Snowflake service: {}.{}.{}", database, schema, serviceName);
        } catch (WebClientResponseException e) {
            log.warn("Error deleting Snowflake service {}.{}.{}: {}", database, schema, serviceName, e.getMessage());
        }
    }
    
    
    /**
     * Also calculates and stores Authorization header for SPCS ingress access
     * Adds image, comment metadata, and endpoint URL to the metadata map.
     * @param serviceName The service name to fetch
     * @param metadata Map to populate with service metadata (will also contain "endpointUrl" key)
     * @return true if successful, false on error
     */
    private boolean fetchServiceImageAndMetadata(String serviceName, Map<String, String> metadata) {
        try {
            // Get the Service object - polymorphic deserialization is now handled correctly by CustomTypeAdapterFactory
            Service fullService = snowflakeServiceAPI.fetchService(database, schema, serviceName).block();
            ServiceSpec serviceSpec = fullService.getSpec();
            
            // Extract spec_text (YAML string) from the properly deserialized ServiceSpecInlineText
            String specText = null;
            if (serviceSpec instanceof ServiceSpecInlineText) {
                specText = ((ServiceSpecInlineText) serviceSpec).getSpecText();
            }
            
            // Extract comment (metadata)
            // Comment format: {"proxies": {"proxyId": {"proxyRuntimeValues": {...}, "containers": {...}}}}
            String comment = fullService.getComment();
            if (comment != null && !comment.isEmpty()) {
                try {
                    Map<String, Object> commentRaw = jsonMapper.readValue(comment, new TypeReference<Map<String, Object>>() {});
                    if (commentRaw != null && commentRaw.containsKey("proxies") && commentRaw.get("proxies") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> proxies = (Map<String, Object>) commentRaw.get("proxies");
                        for (Object proxyDataObj : proxies.values()) {
                            if (proxyDataObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> proxyData = (Map<String, Object>) proxyDataObj;
                                Object pv = proxyData.get("proxyRuntimeValues");
                                if (pv instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, String> proxyValues = (Map<String, String>) pv;
                                    if (proxyValues != null) {
                                        metadata.putAll(proxyValues);
                                    }
                                }
                                break; // one proxy's runtime values enough for instanceId/realmId validation
                            }
                        }
                        log.debug("Extracted proxy runtime values from comment for service {}", serviceName);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing metadata from comment for service {}: {}", serviceName, e.getMessage());
                }
            }
            
            if (specText != null && !specText.isEmpty()) {
                String image = extractImageFromSpecText(specText);
                if (image != null && !image.isEmpty()) {
                    metadata.put("image", image);
                } else {
                    log.warn("Failed to extract image from service spec for service {} (specText length: {})", serviceName, specText.length());
                }
            } else {
                log.warn("Service spec text is null or empty for service {}", serviceName);
            }
                       
            return true;
        } catch (WebClientResponseException e) {
            log.warn("Error fetching service spec for {}: {}", serviceName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts all containers from service YAML spec text.
     * Returns a list of metadata maps, one per container, including container name and image.
     * Container-specific runtime values (like ContainerIndexKey and PortMappingsKey) are derived per-container.
     * 
     * @param specText The service YAML spec text
     * @param allProxyRuntimeValues Proxy-level runtime values from service comment (keyed by "proxyId:key" or just "key" for backward compatibility)
     * @param allContainersMetadata Map of container names to container metadata objects (from all proxies in service comment)
     * @return List of container metadata maps, each with container-specific values added
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractContainersFromSpecText(String specText, Map<String, String> allProxyRuntimeValues, Map<String, Object> allContainersMetadata) {
        List<Map<String, String>> containerList = new ArrayList<>();
        
        if (specText == null || specText.isEmpty()) {
            log.warn("extractContainersFromSpecText: specText is null or empty");
            return containerList;
        }
        
        try {
            // Parse YAML into Map structure
            Map<String, Object> serviceSpec = yamlMapper.readValue(specText, Map.class);
            Map<String, Object> specSection = (Map<String, Object>) serviceSpec.get("spec");
            if (specSection == null) {
                log.warn("extractContainersFromSpecText: spec section not found in YAML");
                return containerList;
            }
            
            List<Map<String, Object>> containers = (List<Map<String, Object>>) specSection.get("containers");
            if (containers == null) {
                log.warn("extractContainersFromSpecText: containers list not found in YAML");
                return containerList;
            }
            
            // Process each container
            int containerIndex = 0;
            for (Map<String, Object> containerMap : containers) {
                Map<String, String> currentContainer = new HashMap<>();
                
                // Extract container name
                Object nameObj = containerMap.get("name");
                if (nameObj == null) {
                    log.warn("extractContainersFromSpecText: container at index {} has no name", containerIndex);
                    containerIndex++;
                    continue;
                }
                String currentContainerName = nameObj.toString();
                currentContainer.put("containerName", currentContainerName);
                
                // Extract image
                Object imageObj = containerMap.get("image");
                if (imageObj != null) {
                    currentContainer.put("image", imageObj.toString());
                }
                
                // Extract proxyId from container name (format: sp--{proxyId}--{containerIndex})
                String proxyId = null;
                if (currentContainerName.startsWith("sp--") && currentContainerName.contains("--")) {
                    String[] parts = currentContainerName.split("--", 3);
                    if (parts.length == 3) {
                        proxyId = parts[1]; // Extract proxyId from container name
                    }
                }
                
                // Add proxy-level runtime values for this proxy
                if (proxyId != null) {
                    for (Map.Entry<String, String> entry : allProxyRuntimeValues.entrySet()) {
                        String key = entry.getKey();
                        if (key.startsWith(proxyId + ":")) {
                            // Extract the actual key name (remove proxyId prefix)
                            String actualKey = key.substring(proxyId.length() + 1);
                            currentContainer.put(actualKey, entry.getValue());
                        } else if (!allProxyRuntimeValues.containsKey(proxyId + ":" + key)) {
                            // Only add unprefixed keys if there's no proxyId-prefixed version
                            currentContainer.put(key, entry.getValue());
                        }
                    }
                } else {
                    // Fallback: add all proxy runtime values if we can't extract proxyId
                    currentContainer.putAll(allProxyRuntimeValues);
                }
                
                // Merge container-specific metadata from allContainersMetadata map
                Map<String, Object> containerMeta = (Map<String, Object>) allContainersMetadata.get(currentContainerName);
                if (containerMeta != null) {
                    // Add portMappings
                    if (containerMeta.containsKey("portMappings")) {
                        currentContainer.put(PortMappingsKey.inst.getKeyAsLabel(), containerMeta.get("portMappings").toString());
                    }
                    
                    // Add container-specific runtime values
                    if (containerMeta.containsKey("runtimeValues")) {
                        Map<String, String> containerRuntimeValues = (Map<String, String>) containerMeta.get("runtimeValues");
                        if (containerRuntimeValues != null) {
                            currentContainer.putAll(containerRuntimeValues);
                        }
                    }
                }
                
                // Add container index (0-based)
                currentContainer.put(ContainerIndexKey.inst.getKeyAsLabel(), String.valueOf(containerIndex));
                containerList.add(currentContainer);
                containerIndex++;
            }
        } catch (IOException e) {
            log.error("Failed to parse YAML spec text: {}", e.getMessage(), e);
        }
        
        return containerList;
    }
    
    /**
     * Extracts image name from YAML spec text (legacy method, extracts first container's image).
     * Expected YAML structure:
     *   spec:
     *     containers:
     *     - name: <name>
     *       image: <image>
     */
    @SuppressWarnings("unchecked")
    private String extractImageFromSpecText(String specText) {
        if (specText == null || specText.isEmpty()) {
            log.warn("extractImageFromSpecText: specText is null or empty");
            return null;
        }
        
        try {
            Map<String, Object> serviceSpec = yamlMapper.readValue(specText, Map.class);
            Map<String, Object> specSection = (Map<String, Object>) serviceSpec.get("spec");
            if (specSection == null) {
                return null;
            }
            
            List<Map<String, Object>> containers = (List<Map<String, Object>>) specSection.get("containers");
            if (containers == null || containers.isEmpty()) {
                return null;
            }
            
            Map<String, Object> firstContainer = containers.get(0);
            Object imageObj = firstContainer.get("image");
            if (imageObj != null) {
                String image = imageObj.toString();
                log.debug("extractImageFromSpecText: extracted image '{}'", image);
                return image;
            }
        } catch (IOException e) {
            log.warn("extractImageFromSpecText: failed to parse YAML: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Validates service metadata and deletes service if unrecoverable. Returns false if service was deleted.
     * Uses comment metadata as primary source (label keys like "openanalytics.eu/sp-proxy-id").
     */
    private boolean validateAndDeleteIfUnrecoverable(String serviceName, Map<String, String> metadata) {
        // Get values from comment metadata using label keys
        String instanceId = metadata.get(InstanceIdKey.inst.getKeyAsLabel());
        
        // Check realm-id validation
        // If current instance has realm-id configured but service doesn't, delete it (orphaned from before realm-id was used)
        // If both have realm-id but they don't match, skip it (belongs to another ShinyProxy instance)
        String storedRealmId = metadata.get(RealmIdKey.inst.getKeyAsLabel());
        String currentRealmId = identifierService.realmId;
        if (currentRealmId != null && storedRealmId == null) {
            // Current instance uses realm-id but service doesn't have it - delete orphaned service
            deleteServiceLogReason(serviceName, "Missing realm-id");
            return false;
        }
        if (storedRealmId != null && currentRealmId != null && !storedRealmId.equals(currentRealmId)) {
            // Both have realm-id but they don't match - skip it (belongs to another instance)
            log.debug("Skipping service {} because realm-id mismatch (stored: {}, current: {})", 
                    serviceName, storedRealmId, currentRealmId);
            return false;
        }
        
        // Check if we can recover this proxy based on instanceId
        // This ensures we only recover containers started with the current config (unless recover-running-proxies-from-different-config is enabled)
        if (!appRecoveryService.canRecoverProxy(instanceId)) {
            deleteServiceLogReason(serviceName, String.format("InstanceId mismatch (stored: %s, current: %s)", 
                    instanceId, identifierService.instanceId));
            return false;
        }
        
        return true;
    }
    
    /**
     * Parses service comment JSON to extract runtime values (similar to Docker's parseLabelsAsRuntimeValues).
     * This automatically extracts all runtime values that were stored in the comment during service creation.
     * 
     * @param serviceName The service name (for logging)
     * @param commentMetadata Map of label keys to string values from the service comment JSON
     * @return Map of RuntimeValueKey to RuntimeValue, or null if parsing failed
     */
    private Map<RuntimeValueKey<?>, RuntimeValue> parseCommentAsRuntimeValues(String serviceName, Map<String, String> commentMetadata) {
        if (commentMetadata == null || commentMetadata.isEmpty()) {
            log.warn("Comment metadata is null or empty for service {}", serviceName);
            return new HashMap<>();
        }

        Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = new HashMap<>();

        for (RuntimeValueKey<?> key : RuntimeValueKeyRegistry.getRuntimeValueKeys()) {
            if (key.getIncludeAsLabel() || key.getIncludeAsAnnotation()) {
                String value = commentMetadata.get(key.getKeyAsLabel());
                if (value != null) {
                    try {
                        runtimeValues.put(key, new RuntimeValue(key, key.deserializeFromString(value)));
                    } catch (Exception e) {
                        log.warn("Error deserializing runtime value {} for service {}: {}", key.getKeyAsLabel(), serviceName, e.getMessage());
                    }
                } else if (key.isRequired()) {
                    // value is null but is required - this might be a problem, but don't fail here
                    // Some required values might come from other sources (service name, service properties, etc.)
                    log.debug("Required runtime value {} not found in comment for service {}", key.getKeyAsLabel(), serviceName);
                }
            }
        }

        return runtimeValues;
    }
    
    /**
     * Builds ExistingContainerInfo from metadata map containing all extracted service data.
     * Uses parseCommentAsRuntimeValues to automatically extract runtime values from comment (similar to Docker labels).
     * When proxyStatus is ProxyStatus.Paused, recovery will show the app as paused and use resume flow.
     * 
     * @param containerId The container ID (UUID format)
     * @param serviceName The service name (for constructing full service name)
     * @param metadata Container metadata including image and runtime values
     * @param proxyStatus Optional status for recovered proxy (e.g. Paused if service was suspended)
     */
    private ExistingContainerInfo buildExistingContainerInfo(String containerId, String serviceName, Map<String, String> metadata, ProxyStatus proxyStatus) {
        // Full service name: database.schema.serviceName
        String fullServiceName = database + "." + schema + "." + serviceName;
        
        // Extract values needed for validation/special handling
        String image = metadata.get("image");

        
        // Parse runtime values from comment metadata (similar to Docker labels)
        // This automatically extracts all runtime values that were stored in the comment
        // Note: PortMappingsKey may already be in metadata if extracted from containerPortMappings during container extraction
        Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseCommentAsRuntimeValues(serviceName, metadata);
        
        // Add/override special values that can't be stored in comment (they have includeAsLabel/includeAsAnnotation=false):
        
        // 1. BackendContainerName - full service name (not stored in comment, derived from service name)
        // BackendContainerNameKey has includeAsLabel=false, so it must be set manually (same as Docker backend does)
        runtimeValues.put(BackendContainerNameKey.inst, new RuntimeValue(BackendContainerNameKey.inst, new BackendContainerName(fullServiceName)));
        
        // 2. ContainerImageKey - extracted from service spec (not stored in comment - has includeAsLabel=false)
        // Always add ContainerImageKey (required by ShinyProxy AdminController)
        if (image == null || image.isEmpty()) {
            log.warn("Image not found for service {}, using empty string for ContainerImageKey", serviceName);
            image = "";
        }
        runtimeValues.put(ContainerImageKey.inst, new RuntimeValue(ContainerImageKey.inst, image));
        
        // 2.5. PortMappingsKey - extracted from containerPortMappings map (if not already parsed)
        // PortMappingsKey has includeAsAnnotation=true, but is container-specific, so stored per-container
        String portMappingsJson = metadata.get(PortMappingsKey.inst.getKeyAsLabel());
        if (portMappingsJson != null && !portMappingsJson.isEmpty() && !runtimeValues.containsKey(PortMappingsKey.inst)) {
            try {
                PortMappings portMappings = PortMappingsKey.inst.deserializeFromString(portMappingsJson);
                runtimeValues.put(PortMappingsKey.inst, new RuntimeValue(PortMappingsKey.inst, portMappings));
            } catch (Exception e) {
                log.warn("Error deserializing PortMappingsKey for container {} in service {}: {}", containerId, serviceName, e.getMessage());
            }
        }
        
        // 3. Add Authorization header to HttpHeaders
        // Extract endpoint URL for authorization header (needed for keypair auth token exchange scope)
        String endpointUrl = extractEndpointUrlFromService(serviceName);
        if (endpointUrl != null) {
            HttpHeaders existingHeaders = null;
            RuntimeValue existingHeadersValue = runtimeValues.get(HttpHeadersKey.inst);
            if (existingHeadersValue != null) {
                existingHeaders = existingHeadersValue.getObject();
            }
            Map<String, String> headers = existingHeaders != null ? 
                new HashMap<>(existingHeaders.jsonValue()) : new HashMap<>();
            
            // Get the ingress authorization header
            String authorizationHeader = getIngressAuthorization(endpointUrl);
            if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
                headers.put("Authorization", authorizationHeader);
                log.debug("Added Authorization header for recovered SPCS service {} (auth method: {})", serviceName, authMethod);
            } else if (authMethod == AuthMethod.KEYPAIR) {
                log.warn("Ingress authorization header not available for recovered SPCS service {} (endpoint URL: {})", serviceName, endpointUrl);
            }
            
            // Note: Sf-Context-Current-User-Token header is not available during recovery
            // as we don't have access to the Authentication context. 
            // TODO: It will be added dynamically per request 
            
            runtimeValues.put(HttpHeadersKey.inst, new RuntimeValue(HttpHeadersKey.inst, new HttpHeaders(headers)));
        }

        // Empty port bindings (SPCS uses endpoints instead of Docker-style port bindings)
        // Ports are exposed via SPCS service endpoints, not mapped to host ports
        Map<Integer, Integer> portBindings = new HashMap<>();
        
        if (proxyStatus != null) {
            return new ExistingContainerInfo(containerId, runtimeValues, image, portBindings, proxyStatus);
        }
        return new ExistingContainerInfo(containerId, runtimeValues, image, portBindings);
    }

    @Override
    public boolean isProxyHealthy(Proxy proxy) {
        // Same as recovery: PENDING or RUNNING = healthy; otherwise not. Not called when proxy status is Paused.
        for (Container container : proxy.getContainers()) {
            String fullServiceName = container.getRuntimeValue(BackendContainerNameKey.inst);
            if (fullServiceName == null) {
                slog.warn(proxy, "SPCS container failed: service name not found");
                return false;
            }
            String[] parts = fullServiceName.split("\\.");
            if (parts.length != 3) {
                slog.warn(proxy, "SPCS container failed: invalid service name format");
                return false;
            }
            String serviceName = parts[2];
            try {
                Service service = snowflakeServiceAPI.fetchService(database, schema, serviceName).block();
                String status = service != null ? service.getStatus() : null;
                if (status == null) {
                    slog.warn(proxy, "SPCS container failed: service status unknown");
                    return false;
                }
                String statusUpper = status.toUpperCase(java.util.Locale.ROOT);
                if (!"PENDING".equals(statusUpper) && !"RUNNING".equals(statusUpper)) {
                    slog.warn(proxy, String.format("SPCS container not healthy: service status %s", status));
                    return false;
                }
            } catch (WebClientResponseException e) {
                slog.warn(proxy, String.format("SPCS container failed: error fetching service status: %s", e.getMessage()));
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the endpoint URL from target URIs for keypair auth token exchange scope.
     * This is used when targets are already available (e.g., during startup).
     * 
     * @param targets The target URIs (may be null or empty)
     * @return The endpoint URL (scheme + host) or null if not available
     */
    private String extractEndpointUrlFromTargets(Map<String, URI> targets) {
        if (authMethod == AuthMethod.KEYPAIR && targets != null && !targets.isEmpty()) {
            // Extract endpoint URL from the first target URI (already set to ingress URL by calculateTarget)
            URI firstTarget = targets.values().iterator().next();
            if (firstTarget != null && "https".equals(firstTarget.getScheme())) {
                // Extract base URL (scheme + host) for the scope
                return firstTarget.getScheme() + "://" + firstTarget.getHost();
            }
        }
        return null;
    }

    /**
     * Extracts the endpoint URL from a service name by fetching its endpoints.
     * Uses the same logic as calculateTarget to find the first public endpoint's ingress URL.
     * This is used during recovery when we need the endpoint URL but don't have targets yet.
     * 
     * @param serviceName The service name (short name, not fully qualified)
     * @return The endpoint URL (scheme + host) or null if not available
     */
    private String extractEndpointUrlFromService(String serviceName) {

        try {
            // Fetch endpoints (same as calculateTarget does)
            List<ServiceEndpoint> endpoints = snowflakeServiceAPI.showServiceEndpoints(database, schema, serviceName).collectList().block();
            if (endpoints != null && !endpoints.isEmpty()) {
                // Find the first public HTTP endpoint (same logic as calculateTarget)
                for (ServiceEndpoint endpoint : endpoints) {
                    if (endpoint.getIsPublic() != null && endpoint.getIsPublic() &&
                        endpoint.getIngressUrl() != null && !endpoint.getIngressUrl().isEmpty() &&
                        "HTTP".equalsIgnoreCase(endpoint.getProtocol())) {
                        String ingressUrl = endpoint.getIngressUrl();
                        
                        // Ingress URL format is typically: https://{endpoint}.snowflakecomputing.com
                        // Extract base URL (scheme + host) for the scope
                        if (!ingressUrl.startsWith("https://") && !ingressUrl.startsWith("http://")) {
                            ingressUrl = "https://" + ingressUrl;
                        }
                        
                        try {
                            URI uri = new URI(ingressUrl);
                            return uri.getScheme() + "://" + uri.getHost();
                        } catch (java.net.URISyntaxException e) {
                            log.debug("Invalid ingress URL format for endpoint: {}", ingressUrl);
                        }
                        break;
                    }
                }
            }
        } catch (WebClientResponseException e) {
            log.debug("Could not fetch endpoints to get endpoint URL for keypair auth: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Sets up HTTP headers for proxy container communication.
     * Sets Authorization header only when running external to SPCS (for ingress access).
     * SPCS user context headers (Sf-Context-Current-User, Sf-Context-Current-User-Token) are
     * added per request by SpcsAuthenticationBackend.addHeaders() in HttpHeaders.getUndertowHeaderMap().
     *
     * @param proxy The proxy (used to get existing headers and container info)
     * @param endpointUrl Optional endpoint URL for keypair auth token exchange scope (only used when external)
     * @return Map of merged headers
     */
    private Map<String, String> setupProxyContainerHTTPHeaders(Proxy proxy, String endpointUrl) {
        // Get existing headers from proxy and merge with new headers
        HttpHeaders existingHeaders = proxy.getRuntimeObject(HttpHeadersKey.inst);
        Map<String, String> mergedHeaders = existingHeaders != null ?
            new HashMap<>(existingHeaders.jsonValue()) : new HashMap<>();

        // Set Authorization header only when running external to SPCS (needed for ingress access)
        if (!isRunningInsideSpcs()) {
            String authorizationHeader = getIngressAuthorization(endpointUrl);

            if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
                mergedHeaders.put("Authorization", authorizationHeader);
                log.info("Setting up Authorization header for SPCS ingress access (auth method: {})", authMethod);
            } else if (authMethod == AuthMethod.KEYPAIR && endpointUrl != null) {
                log.warn("Ingress authorization header not available for SPCS access (endpoint URL: {})", endpointUrl);
            }
        }

        return mergedHeaders;
    }

    protected URI calculateTarget(Container container, PortMappings.PortMappingEntry portMapping, Integer hostPort) throws Exception {
        String fullServiceName = container.getRuntimeValue(BackendContainerNameKey.inst);
        if (fullServiceName == null) {
            throw new ContainerFailedToStartException("Service name not found while calculating target", null, container);
        }

        // database.schema.service name
        String[] parts = fullServiceName.split("\\.");
        if (parts.length != 3) {
            throw new ContainerFailedToStartException("Invalid service name format: " + fullServiceName, null, container);
        }

        // Fetch service to get DNS name or ingress URL
        try {
            Service service = snowflakeServiceAPI.fetchService(parts[0], parts[1], parts[2]).block();
            
            if (isRunningInsideSpcs()) {
                // When running inside SPCS: use internal DNS name for communication
                // The DNS name format is: service-name.unique-id.svc.spcs.internal
                String serviceDnsName = service.getDnsName();            
                if (serviceDnsName == null || serviceDnsName.isEmpty()) {
                    throw new ContainerFailedToStartException("Service DNS name not available from REST API", null, container);
                }
                
                log.info("Proxy container DNS name: {}", serviceDnsName);
                log.debug("calculateTarget: Service={}, DNS={}, Port={}, Path={}, Protocol={}", 
                    fullServiceName, serviceDnsName, portMapping.getPort(), portMapping.getTargetPath(), getDefaultTargetProtocol());
                
                int targetPort = portMapping.getPort();
                URI targetUri = new URI(String.format("%s://%s:%s%s", getDefaultTargetProtocol(), serviceDnsName, targetPort, portMapping.getTargetPath()));
                log.debug("calculateTarget: Final target URI={}", targetUri);
                return targetUri;
            } else {
                // When running external to SPCS: use ingress URL (HTTPS) for communication
                // Note: Public endpoints have protocol HTTP, but ingress URLs use HTTPS scheme
                // Need to get the ingress URL from public service endpoints that match the port
                List<ServiceEndpoint> endpoints = snowflakeServiceAPI.showServiceEndpoints(parts[0], parts[1], parts[2]).collectList().block();
                int targetPort = portMapping.getPort();
                
                // Find public endpoint matching the port (when running external, we need public access)
                // Public endpoints have protocol HTTP (not HTTPS), but ingress URLs use HTTPS
                ServiceEndpoint matchingEndpoint = null;
                for (ServiceEndpoint endpoint : endpoints) {
                    if (endpoint.getPort() != null && endpoint.getPort().equals(targetPort) &&
                        endpoint.getIsPublic() != null && endpoint.getIsPublic() &&
                        "HTTP".equalsIgnoreCase(endpoint.getProtocol())) {
                        matchingEndpoint = endpoint;
                        break;
                    }
                }
                
                if (matchingEndpoint == null) {
                    throw new ContainerFailedToStartException("No public HTTP endpoint found for port " + targetPort + " in service " + fullServiceName, null, container);
                }
                
                // Get ingress URL (should already be ready since we wait for endpoints during startup)
                String ingressUrl = matchingEndpoint.getIngressUrl();
                log.debug("calculateTarget: Service={}, Port={}, Found matching endpoint: port={}, public={}, protocol={}, ingressUrl={}", 
                    fullServiceName, targetPort, matchingEndpoint.getPort(), matchingEndpoint.getIsPublic(), 
                    matchingEndpoint.getProtocol(), ingressUrl);
                
                if (ingressUrl == null || ingressUrl.isEmpty()) {
                    throw new ContainerFailedToStartException("Ingress URL not available for port " + targetPort + " in service " + fullServiceName, null, container);
                }
                
                // Check if endpoint is still provisioning (should not happen, but validate anyway)
                String lowerUrl = ingressUrl.toLowerCase();
                if (lowerUrl.contains("provisioning") || lowerUrl.contains("progress")) {
                    throw new ContainerFailedToStartException("Endpoint for port " + targetPort + " in service " + fullServiceName + " is still provisioning: " + ingressUrl, null, container);
                }
                
                // Ingress URL format is typically: https://{endpoint}.snowflakecomputing.com
                // Ensure it starts with https://
                String originalIngressUrl = ingressUrl;
                if (!ingressUrl.startsWith("https://") && !ingressUrl.startsWith("http://")) {
                    ingressUrl = "https://" + ingressUrl;
                    log.debug("calculateTarget: Added https:// prefix to ingress URL: {} -> {}", originalIngressUrl, ingressUrl);
                }
                
                // Validate and create URI
                try {
                    // Append the target path if specified
                    String targetPath = portMapping.getTargetPath() != null ? portMapping.getTargetPath() : "";
                    String fullUrl = ingressUrl + targetPath;
                    log.debug("calculateTarget: Service={}, Final URL={} (ingressUrl={}, targetPath={})", 
                        fullServiceName, fullUrl, ingressUrl, targetPath);
                    URI uri = new URI(fullUrl);
                    log.debug("calculateTarget: Final target URI={}", uri);
                    return uri;
                } catch (java.net.URISyntaxException e) {
                    throw new ContainerFailedToStartException("Invalid ingress URL format for port " + targetPort + ": " + ingressUrl, e, container);
                }
            }
        } catch (WebClientResponseException e) {
            throw new ContainerFailedToStartException("Failed to fetch service or endpoints: " + e.getMessage(), e, container);
        }
    }

    /**
     * Gets the Authorization header value for use in HTTPS proxy forwarding via ingress.
     * When running external to SPCS and forwarding HTTPS requests via ingress,
     * this header value should be used for the Authorization header.
     * 
     * For PAT and key-pair tokens: returns "Snowflake Token=\"<token>\""
     * For SPCS session token: returns "Bearer <token>" (token is read from session file)
     * 
     * Reference: https://docs.snowflake.com/en/developer-guide/snowpark-container-services/tutorials/advanced/tutorial-8-access-public-endpoint-programmatically#option-2-send-requests-to-the-service-endpoint-programmatically-by-using-a-jwt
     * 
     * @param endpointUrl Optional endpoint URL for key-pair token exchange scope. 
     *                    If null, a basic scope will be used.
     * @return The Authorization header value (e.g., "Bearer <token>" or "Snowflake Token=\"<token>\""), or null if not available
     */
    public String getIngressAuthorization(String endpointUrl) {
        String token;
        if (authMethod == AuthMethod.SPCS_SESSION_TOKEN) {
            // For SPCS session token: use supplier to get token directly from session file
            token = jwtTokenSupplier != null ? jwtTokenSupplier.get() : null;
            if (token == null || token.isEmpty()) {
                return null;
            }
            // Format: Snowflake Token="<token>"
            return "Snowflake Token=\"" + token + "\"";
        } else if (authMethod == AuthMethod.PAT) {
            // For PAT: use supplier to get token directly
            token = jwtTokenSupplier != null ? jwtTokenSupplier.get() : null;
            if (token == null || token.isEmpty()) {
                return null;
            }
            // Format: Snowflake Token="<token>"
            return "Snowflake Token=\"" + token + "\"";
        } else if (authMethod == AuthMethod.KEYPAIR) {
            // For keypair: exchange JWT for Snowflake OAuth token
            if (jwtTokenSupplier == null) {
                return null;
            }
            String jwtToken = jwtTokenSupplier.get();
            try {
                token = keypairAuth.exchangeJwtForSnowflakeToken(jwtToken, endpointUrl);
                if (token == null || token.isEmpty()) {
                    return null;
                }
                // Format: Snowflake Token="<token>"
                return "Snowflake Token=\"" + token + "\"";
            } catch (IOException e) {
                log.error("Failed to exchange JWT for Snowflake OAuth token for ingress", e);
                throw new RuntimeException("Failed to exchange JWT token for ingress authentication: " + e.getMessage(), e);
            }
        }
        throw new IllegalStateException("Unknown authentication method: " + authMethod);
    }

    /**
     * Gets the username used for authentication.
     *
     * @return The username, or null if using SPCS session token
     */
    String getUsername() {
        return username;
    }

    /**
     * Gets the authentication method currently being used.
     *
     * @return The authentication method
     */
    AuthMethod getAuthMethod() {
        return authMethod;
    }

    /**
     * Checks if ShinyProxy is running inside SPCS.
     * 
     * @return true if running inside SPCS, false if running external
     */
    public boolean isRunningInsideSpcs() {
        return authMethod == AuthMethod.SPCS_SESSION_TOKEN;
    }

    /**
     * Deletes a Snowflake service using the SQL API (DROP SERVICE ... FORCE).
     * The REST API doesn't support force deletion, so we use the SQL API.
     *
     * @param database Database name
     * @param schema Schema name
     * @param serviceName Service name
     * @throws WebClientResponseException If the deletion fails
     */
    private void deleteService(String database, String schema, String serviceName) throws WebClientResponseException {
        String sql = String.format("DROP SERVICE %s.%s.%s FORCE",
            escapeSqlIdentifier(database),
            escapeSqlIdentifier(schema),
            escapeSqlIdentifier(serviceName));

        SubmitStatementRequest request = new SubmitStatementRequest();
        request.setStatement(sql);
        request.setDatabase(database);
        request.setSchema(schema);
        String computeWarehouse = getProperty(PROPERTY_COMPUTE_WAREHOUSE);
        if (computeWarehouse != null && !computeWarehouse.isBlank()) {
            request.setWarehouse(computeWarehouse.trim().toUpperCase());
        }

        try {
            snowflakeStatementsAPI.submitStatement(
                "ContainerProxy/1.0",
                request,
                null,
                false,
                false,
                "application/json",
                null
            ).block();
            log.debug("Successfully force deleted service {}.{}.{} using SQL API", database, schema, serviceName);
        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("Failed to force delete service {}.{}.{} using SQL API: {}; response body: {}",
                database, schema, serviceName, e.getMessage(), responseBody != null && !responseBody.isEmpty() ? responseBody : "(empty)");
            throw e;
        }
    }
    
    /**
     * Escapes a SQL identifier by wrapping it in double quotes if needed.
     * Simple implementation - just wraps in double quotes for safety.
     */
    private String escapeSqlIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "\"\"";
        }
        // Replace any double quotes with escaped double quotes
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * Attempts to fetch service logs when a service fails to start.
     * This is a best-effort attempt - if it fails, returns null and the error handling will provide SQL instructions instead.
     * 
     * Reference: https://docs.snowflake.com/en/developer-guide/snowflake-rest-api/services/services-introduction
     * REST API endpoint: GET /api/v2/databases/{database}/schemas/{schema}/services/{service_name}/logs
     * 
     * @param database Database name
     * @param schema Schema name
     * @param serviceName Service name
     * @return A summary of recent logs (last few lines), or null if logs cannot be fetched
     */
    private String fetchServiceLogsForError(String database, String schema, String serviceName) {
        try {
            // First, try to get container info to get instance and container names
            List<ServiceContainer> containers = snowflakeServiceAPI.listServiceContainers(database, schema, serviceName).collectList().block();
            if (containers == null || containers.isEmpty()) {
                return null;
            }
            
            // Use the first container that has info
            ServiceContainer container = containers.get(0);
            String instanceIdStr = container.getInstanceId();
            String containerName = container.getContainerName();
            
            if (instanceIdStr == null || containerName == null) {
                return null;
            }
            
            // Convert instanceId from String to Integer (API expects Integer)
            Integer instanceId;
            try {
                instanceId = Integer.parseInt(instanceIdStr);
            } catch (NumberFormatException e) {
                log.debug("Invalid instanceId format: {}", instanceIdStr);
                return null;
            }
            
            // Use the generated API method to fetch logs
            eu.openanalytics.containerproxy.backend.spcs.client.model.FetchServiceLogs200Response response = 
                snowflakeServiceAPI.fetchServiceLogs(database, schema, serviceName, instanceId, containerName, 50).block();
            
            if (response == null || response.getSystem$getServiceLogs() == null) {
                return null;
            }
            
            String logContent = response.getSystem$getServiceLogs();
            if (logContent.isEmpty()) {
                return null;
            }
            
            // Extract last few lines (limit to avoid huge error messages)
            String[] lines = logContent.split("\n");
            int maxLines = 10;
            if (lines.length > maxLines) {
                StringBuilder summary = new StringBuilder();
                summary.append("(last ").append(maxLines).append(" lines of ").append(lines.length).append(" total)\n");
                for (int i = lines.length - maxLines; i < lines.length; i++) {
                    summary.append(lines[i]).append("\n");
                }
                return summary.toString().trim();
            }
            return logContent.trim();
        } catch (WebClientResponseException e) {
            log.debug("Could not fetch service logs via API: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("Could not fetch service logs for error reporting: {}", e.getMessage());
            return null;
        }
    }

}
