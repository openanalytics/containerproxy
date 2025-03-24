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
package eu.openanalytics.containerproxy.test.helpers;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.backend.dispatcher.DefaultProxyDispatcher;
import eu.openanalytics.containerproxy.backend.dispatcher.ProxyDispatcherService;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.IProxySharingStoreFactory;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.util.Retrying;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;


public class ShinyProxyInstance implements AutoCloseable {

    public final ShinyProxyClient client;
    public final ProxyService proxyService;
    public final IProxySpecProvider specProvider;
    private final int port;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Thread thread;
    private ConfigurableApplicationContext app;
    private boolean cleanup;

    public ShinyProxyInstance(String configFileName, Map<String, String> properties) {
        this(configFileName, 7583, "demo", properties, false);
    }

    public ShinyProxyInstance(String configFileName, Map<String, String> properties, boolean useNotInternalOnlyTestStrategyConfiguration) {
        this(configFileName, 7583, "demo", properties, useNotInternalOnlyTestStrategyConfiguration);
    }

    public ShinyProxyInstance(String configFileName) {
        this(configFileName, 7583, "demo", new HashMap<>(), false);
    }

    public ShinyProxyInstance(String configFileName, int port, String usernameAndPassword, Map<String, String> properties, boolean useNotInternalOnlyTestStrategyConfiguration) {
        try {
            this.port = port;
            int mgmtPort = port % 1000 + 9000;

            SpringApplication application = new SpringApplication(ContainerProxyApplication.class);
            if (useNotInternalOnlyTestStrategyConfiguration) {
                // only works if networking is NOT internal!
                application.addPrimarySources(Collections.singletonList(NotInternalOnlyTestStrategyConfiguration.class));
            }
            application.addPrimarySources(Collections.singletonList(TestConfiguration.class));
            Properties allProperties = ContainerProxyApplication.getDefaultProperties();
            allProperties.put("spring.config.location", "src/test/resources/" + configFileName);
            allProperties.put("server.port", port);
            allProperties.put("management.server.port", mgmtPort);
            allProperties.put("proxy.kubernetes.namespace", "itest");
            allProperties.putAll(properties);
            application.setDefaultProperties(allProperties);

            client = new ShinyProxyClient(usernameAndPassword, port);

            AtomicReference<Throwable> exception = new AtomicReference<>();
            thread = new Thread(() -> app = application.run());
            thread.setUncaughtExceptionHandler((thread, ex) -> {
                exception.set(ex);
            });
            thread.start();

            boolean available = Retrying.retry((c, m) -> {
                if (exception.get() != null) {
                    logger.warn("Exception during startup");
                    return true;
                }
                return client.checkAlive();
            }, 60_000, "ShinyProxyInstance available", 1, true);
            Throwable ex = exception.get();
            if (ex != null) {
                throw ex;
            }
            if (!available) {
                throw new TestHelperException("ShinyProxy did not become available!");
            } else {
                logger.info("ShinyProxy available!");
            }

            proxyService = app.getBean("proxyService", ProxyService.class);
            specProvider = app.getBean("defaultSpecProvider", IProxySpecProvider.class);

        } catch (Throwable t) {
            throw new TestHelperException("Error during startup of ShinyProxy", t);
        }
    }

    public ShinyProxyClient getClient(String usernameAndPassword) {
        return new ShinyProxyClient(usernameAndPassword, port);
    }

    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        return app.getBean(name, requiredType);
    }

    @Override
    public void close() {
        if (cleanup) {
            stopAllApps();
            IProxySpecProvider proxySpecProvider = getBean("defaultSpecProvider", IProxySpecProvider.class);
            for (ProxySpec proxySpec : proxySpecProvider.getSpecs()) {
                try {
                    ProxySharingScaler proxySharingScaler = getBean("proxySharingScaler_" + proxySpec.getId(), ProxySharingScaler.class);
                    proxySharingScaler.stopAll();
                } catch (NoSuchBeanDefinitionException ex) {
                    // no container sharing, ignore
                }
            }
        }
        app.stop();
        app.close();
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void enableCleanup() {
        cleanup = true;
    }

    public void stopAllApps() {
        for (Proxy proxy : proxyService.getAllProxies()) {
            proxyService.stopProxy(null, proxy, true).run();
        }
    }

    public static class NotInternalOnlyTestStrategyConfiguration {

        @Primary
        @Bean
        public NotInternalOnlyTestStrategy notInternalOnlyTestStrategy() {
            return new NotInternalOnlyTestStrategy();
        }

    }

    public static class TestConfiguration {

        @Primary
        @Bean
        public JavaMailSender javaMailSender() {
            return new JavaMailSender() {

                @Override
                public void send(SimpleMailMessage... simpleMessages) throws MailException {

                }

                @Override
                public MimeMessage createMimeMessage() {
                    return new MimeMessage(Session.getDefaultInstance(new Properties()));
                }

                @Override
                public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
                    return new MimeMessage(Session.getDefaultInstance(new Properties()));
                }

                @Override
                public void send(MimeMessage... mimeMessages) throws MailException {

                }
            };
        }

        @Primary
        @Bean
        public ProxyDispatcherService proxyDispatcherService(IProxySpecProvider proxySpecProvider,
                                                             IProxySharingStoreFactory storeFactory,
                                                             ConfigurableListableBeanFactory beanFactory,
                                                             DefaultProxyDispatcher defaultProxyDispatcher) {
            return new ProxyDispatcherService(proxySpecProvider, storeFactory, beanFactory, defaultProxyDispatcher) {
                protected TestProxySharingScaler createProxySharingScaler(ISeatStore seatStore, ProxySpec proxySpec, IDelegateProxyStore delegateProxyStore) {
                    return new TestProxySharingScaler(seatStore, proxySpec, delegateProxyStore);
                }
            };
        }
    }

}
