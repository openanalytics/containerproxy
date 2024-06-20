/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.containerproxy;

import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingDispatcher;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.hearbeat.ActiveProxiesService;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import eu.openanalytics.containerproxy.service.hearbeat.IHeartbeatProcessor;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.util.LoggingConfigurer;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import io.undertow.Handlers;
import io.undertow.server.handlers.SameSiteCookieHandler;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.api.SessionManagerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.FormContentFilter;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;

import static eu.openanalytics.containerproxy.api.ApiSecurityService.PROP_API_SECURITY_HIDE_SPEC_DETAILS;
import static eu.openanalytics.containerproxy.service.AppRecoveryService.PROPERTY_RECOVER_RUNNING_PROXIES;
import static eu.openanalytics.containerproxy.service.AppRecoveryService.PROPERTY_RECOVER_RUNNING_PROXIES_FROM_DIFFERENT_CONFIG;
import static eu.openanalytics.containerproxy.service.ProxyService.PROPERTY_STOP_PROXIES_ON_SHUTDOWN;
import static io.undertow.server.handlers.resource.ResourceManager.EMPTY_RESOURCE_MANAGER;

@EnableScheduling
@EnableAsync
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class, DataSourceAutoConfiguration.class, RedisAutoConfiguration.class})
@ComponentScan("eu.openanalytics")
public class ContainerProxyApplication {
    public static final String CONFIG_FILENAME = "application.yml";
    public static final String CONFIG_DEMO_PROFILE = "demo";
    private static final String PROP_PROXY_SAME_SITE_COOKIE = "proxy.same-site-cookie";
    private static final String SAME_SITE_COOKIE_DEFAULT_VALUE = "Lax";
    private static final String PROP_SERVER_SECURE_COOKIES = "server.secure-cookies";
    private static final Boolean SECURE_COOKIES_DEFAULT_VALUE = false;
    public static Boolean secureCookiesEnabled;
    public static String sameSiteCookiePolicy;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final Logger log = LogManager.getLogger(getClass());
    @Inject
    private Environment environment;
    @Inject
    private ProxyMappingManager mappingManager;
    @Inject
    private DefaultCookieSerializer defaultCookieSerializer;
    @Autowired(required = false)
    private SessionManagerFactory sessionManagerFactory;
    @Inject
    private IProxySpecProvider proxySpecProvider;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ContainerProxyApplication.class);

        app.addListeners(new LoggingConfigurer());

        boolean hasExternalConfig = Files.exists(Paths.get(CONFIG_FILENAME))
            || System.getProperty("spring.config.location") != null
            || System.getenv("SPRING_CONFIG_LOCATION") != null
            || Arrays.stream(args).anyMatch(s -> s.contains("--spring.config.location"));

        if (!hasExternalConfig) {
            app.setAdditionalProfiles(CONFIG_DEMO_PROFILE);
            Logger log = LogManager.getLogger(ContainerProxyApplication.class);
            log.warn("WARNING: Did not found configuration, using fallback configuration!");
        }

        setDefaultProperties(app);

        try {
            app.setLogStartupInfo(false);
            app.run(args);
        } catch (Exception e) {
            // Workaround for bug in UndertowEmbeddedServletContainer.start():
            // If undertow.start() fails, started remains false which prevents undertow.stop() from ever being called.
            // Undertow's (non-daemon) XNIO worker threads will then prevent the JVM from exiting.
            if (e instanceof PortInUseException) System.exit(-1);
        }
    }

    public static Properties getDefaultProperties() {
        Properties properties = new Properties();

        // use in-memory session storage by default. Can be overwritten in application.yml
        properties.put("spring.session.store-type", "none");
        // required for proper working of the SP_USER_INITIATED_LOGOUT session attribute in the UserService
        properties.put("spring.session.redis.flush-mode", "IMMEDIATE");
        properties.put("spring.session.redis.repository-type", "indexed");

        // disable multi-part handling by Spring. We don't need this anywhere in the application.
        // When enabled this will cause problems when proxying file-uploads to the shiny apps.
        properties.put("spring.servlet.multipart.enabled", "false");

        // disable logging of requests, since this reads part of the requests and therefore undertow is unable to correctly handle those requests
        properties.put("logging.level.org.springframework.web.servlet.DispatcherServlet", "INFO");
        properties.put("logging.level.io.fabric8.kubernetes.client.dsl.internal.VersionUsageUtils", "ERROR");

        properties.put("spring.application.name", "ContainerProxy");
        // do not include application name in every log message
        properties.put("logging.include-application-name", "false");

        // Metrics configuration
        // ====================

        // disable all supported exporters by default
        properties.put("management.defaults.metrics.export.enabled", "false");
        // set actuator to port 9090 (can be overwritten)
        properties.put("management.server.port", "9090");
        // enable prometheus endpoint by default (but not the exporter)
        properties.put("management.endpoint.prometheus.enabled", "true");
        properties.put("management.endpoint.recyclable.enabled", "true");
        // include prometheus and health endpoint in exposure
        properties.put("management.endpoints.web.exposure.include", "health,prometheus,recyclable");

        // ====================

        // Health configuration
        // ====================

        // enable redisSession check for the readiness probe
        properties.put("management.endpoint.health.group.readiness.include", "appRecoveryReadyIndicator");
        // disable ldap health endpoint
        properties.put("management.health.ldap.enabled", false);
        // disable default redis health endpoint since it's managed by redisSession
        properties.put("management.health.redis.enabled", "false");
        // enable Kubernetes probes
        properties.put("management.endpoint.health.probes.enabled", true);

        // ====================

        // disable openapi docs and swagger ui
        properties.put("springdoc.api-docs.enabled", false);
        properties.put("springdoc.swagger-ui.enabled", false);

        return properties;
    }

    private static void setDefaultProperties(SpringApplication app) {
        app.setDefaultProperties(getDefaultProperties());
        // See: https://github.com/keycloak/keycloak/pull/7053
        System.setProperty("jdk.serialSetFilterAfterRead", "true");
    }

    // Disable specific Spring filters that parse the request body, preventing it from being proxied.

    @PostConstruct
    public void init() {
        if (environment.getProperty("server.use-forward-headers") != null) {
            log.warn("WARNING: Using server.use-forward-headers will not work in this ShinyProxy release, you need to change your configuration to use another property. See https://shinyproxy.io/documentation/security/#forward-headers on" +
                " how to change your configuration.");
        }

        sameSiteCookiePolicy = environment.getProperty(PROP_PROXY_SAME_SITE_COOKIE, SAME_SITE_COOKIE_DEFAULT_VALUE);
        secureCookiesEnabled = environment.getProperty(PROP_SERVER_SECURE_COOKIES, Boolean.class, SECURE_COOKIES_DEFAULT_VALUE);

        log.debug("Setting sameSiteCookie policy to {}", sameSiteCookiePolicy);
        defaultCookieSerializer.setSameSite(sameSiteCookiePolicy);
        defaultCookieSerializer.setUseSecureCookie(secureCookiesEnabled);

        if (sameSiteCookiePolicy.equalsIgnoreCase("none") && !secureCookiesEnabled) {
            log.warn("WARNING: Invalid configuration detected: same-site-cookie policy is set to None, but secure-cookies are not enabled. Secure cookies must be enabled when using None as same-site-cookie policy ");
        }

        if (environment.getProperty("proxy.store-mode", "").equalsIgnoreCase("Redis")) {
            if (!environment.getProperty("spring.session.store-type", "").equalsIgnoreCase("redis")) {
                // running in HA mode, but not using Redis sessions
                log.warn("WARNING: Invalid configuration detected: store-mode is set to Redis (i.e. High-Availability mode), but you are not using Redis for user sessions!");
            }
            if (environment.getProperty(PROPERTY_STOP_PROXIES_ON_SHUTDOWN, Boolean.class, true)) {
                // running in HA mode, but proxies are removed when shutting down
                log.warn("WARNING: Invalid configuration detected: store-mode is set to Redis (i.e. High-Availability mode), but proxies are stopped at shutdown of server!");
            }
            if (environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES, Boolean.class, false) ||
                environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES_FROM_DIFFERENT_CONFIG, Boolean.class, false)) {
                log.warn("WARNING: Invalid configuration detected: cannot use store-mode with Redis (i.e. High-Availability mode) and app recovery at the same time. Disable app recovery!");
            }
        }

        if (environment.getProperty("spring.session.store-type", "").equalsIgnoreCase("redis")) {
            if (!environment.getProperty("proxy.store-mode", "").equalsIgnoreCase("Redis")) {
                // using Redis sessions, but not running in HA mode -> this does not make sense
                // even with one replica, the HA mode should be used in order for the server to survive restarts (which is the reason Redis sessions are used)
                log.warn("WARNING: Invalid configuration detected: user sessions are stored in Redis, but store-more is not set to Redis. Change store-mode so that app sessions are stored in Redis!");
            }
            if (environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES, Boolean.class, false) ||
                environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES_FROM_DIFFERENT_CONFIG, Boolean.class, false)) {
                // using Redis sessions together with app recovery -> this does not make sense
                // if already using Redis for sessions there is no reason to not store app sessions
                log.warn("WARNING: Invalid configuration detected: user sessions are stored in Redis and App Recovery is enabled. Instead of using App Recovery, change store-mode so that app sessions are stored in Redis!");
            }
        }
        if (environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES, Boolean.class, false) ||
            environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES_FROM_DIFFERENT_CONFIG, Boolean.class, false)) {
            for (ProxySpec proxySpec : proxySpecProvider.getSpecs()) {
                if (ProxySharingDispatcher.supportSpec(proxySpec)) {
                    throw new IllegalStateException("Cannot use App Recovery together with container pre-initialization or sharing");
                }
            }
        }

        boolean hideSpecDetails = environment.getProperty(PROP_API_SECURITY_HIDE_SPEC_DETAILS, Boolean.class, true);
        if (!hideSpecDetails) {
            log.warn("WARNING: Insecure configuration detected: The API is configured to return the full spec of proxies, " +
                "this may contain sensitive values such as the container image, secret environment variables etc. " +
                "Remove the proxy.api-security.hide-spec-details property to enable API security.");
        }

    }

    @Bean
    public UndertowServletWebServerFactory servletContainer() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        factory.addDeploymentInfoCustomizers(info -> {
            info.setPreservePathOnForward(false); // required for the /api/route/{id}/ endpoint to work properly
            if (Boolean.parseBoolean(environment.getProperty("logging.requestdump", "false"))) {
                info.addOuterHandlerChainWrapper(Handlers::requestDump);
            }
            info.addInnerHandlerChainWrapper(defaultHandler -> mappingManager.createHttpHandler(defaultHandler));

            log.debug("Setting sameSiteCookie policy for session cookies to {}", sameSiteCookiePolicy);
            info.addOuterHandlerChainWrapper(defaultHandler -> new SameSiteCookieHandler(defaultHandler, sameSiteCookiePolicy, null, true, true, false));

            ServletSessionConfig sessionConfig = new ServletSessionConfig();
            sessionConfig.setHttpOnly(true);
            sessionConfig.setSecure(secureCookiesEnabled);
            info.setServletSessionConfig(sessionConfig);
            if (sessionManagerFactory != null) {
                info.setSessionManagerFactory(sessionManagerFactory);
            }
            info.setResourceManager(EMPTY_RESOURCE_MANAGER);
        });
        try {
            factory.setAddress(InetAddress.getByName(environment.getProperty("proxy.bind-address", "0.0.0.0")));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid bind address specified", e);
        }
        factory.setPort(Integer.parseInt(environment.getProperty("proxy.port", "8080")));
        return factory;
    }

    @Bean
    public FilterRegistrationBean<FormContentFilter> registration2(FormContentFilter filter) {
        FilterRegistrationBean<FormContentFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Register the Jackson module which implements compatibility between javax.json and Jackson.
     *
     * @return
     */
    @Bean
    public JSR353Module jsr353Module() {
        return new JSR353Module();
    }

    /**
     * This Bean ensures that User Session are properly expired when using Redis for session storage.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
    public <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.initialize();
        return executor;
    }

    @Bean
    public HeartbeatService heartbeatService(List<IHeartbeatProcessor> heartbeatProcessors, Environment environment) {
        return new HeartbeatService(heartbeatProcessors, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActiveProxiesService activeProxiesService() {
        return new ActiveProxiesService();
    }

    @Bean
    public GroupedOpenApi groupOpenApi() {
        return GroupedOpenApi.builder()
            .group("v1")
            .addOpenApiCustomizer(openApi -> {
                Set<String> endpoints = new HashSet<>(Arrays.asList("/app_direct_i/**", "/app_direct/**", "/app_proxy/{targetId}/**", "/error"));
                openApi.getPaths().entrySet().stream().filter(p -> endpoints.contains(p.getKey()))
                    .forEach(p -> {
                        p.getValue().setHead(null);
                        p.getValue().setPost(null);
                        p.getValue().setDelete(null);
                        p.getValue().setParameters(null);
                        p.getValue().setOptions(null);
                        p.getValue().setPut(null);
                        p.getValue().setPatch(null);
                    });
            })
            .build();
    }

}
