/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
package eu.openanalytics.containerproxy.backend;

import eu.openanalytics.containerproxy.backend.proxysharing.DelegatingContainerBackend;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.RuntimeValueService;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Service
public class ContainerBackendFactory extends AbstractFactoryBean<IContainerBackend> implements ApplicationContextAware {

    private static final String PROPERTY_CONTAINER_BACKEND = "proxy.container-backend";

    private static final Map<String, Class<? extends IContainerBackend>> BACKENDS = new HashMap<>();
    @Inject
    protected Environment environment;
    @Inject
    private RuntimeValueService runtimeValueService;
    @Lazy
    @Inject
    private ProxyService proxyService;
    @Inject
    private SpecExpressionResolver expressionResolver;

    private ApplicationContext applicationContext;

    public static void addBackend(String name, Class<? extends IContainerBackend> backend) {
        if (BACKENDS.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Cannot register duplicate backend with name %s not found", name));
        }
        BACKENDS.put(name, backend);
    }

    private static IContainerBackend createFor(String name) throws Exception {
        if (!BACKENDS.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Backend with name %s not found", name));
        }
        return BACKENDS.get(name).newInstance();
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Class<?> getObjectType() {
        return IContainerBackend.class;
    }

    @Override
    protected IContainerBackend createInstance() throws Exception {
        String backendName = environment.getProperty(PROPERTY_CONTAINER_BACKEND, "docker");
        IContainerBackend backend = createFor(backendName);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
        backend.initialize();
        DelegatingContainerBackend containerSharingBackend = new DelegatingContainerBackend(runtimeValueService, proxyService, expressionResolver);
        containerSharingBackend.setDelegate(backend);
        return containerSharingBackend;
    }
}
