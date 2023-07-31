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
package eu.openanalytics.containerproxy.backend.proxysharing;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.RuntimeValueService;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;

import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class DelegatingContainerBackend implements IContainerBackend {

    private IContainerBackend delegateBackend;

    private final RuntimeValueService runtimeValueService;

    private final ProxyService proxyService;

    private final SpecExpressionResolver expressionResolver;

    public DelegatingContainerBackend(RuntimeValueService runtimeValueService, ProxyService proxyService, SpecExpressionResolver expressionResolver) {
        this.runtimeValueService = runtimeValueService;
        this.proxyService = proxyService;
        this.expressionResolver = expressionResolver;
    }

    @Override
    public void initialize() throws ContainerProxyException {

    }

    @Override
    public Proxy startProxy(Authentication user, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ProxyFailedToStartException {
        Proxy.ProxyBuilder resultProxy = proxy.toBuilder();
        Pair<String, Map<String, URI>> resp = assignDelegatedProxy(proxy.getSpecId());
        resultProxy.targetId(resp.getFirst());
        resultProxy.addTargets(resp.getSecond());
        String publicPath = proxy.getRuntimeObjectOrNull(PublicPathKey.inst);
        if (publicPath != null) {
            resultProxy.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, publicPath.replaceAll(proxy.getId(), resp.getFirst())), true);
        }
        return resultProxy.build();
    }

    @Override
    public void stopProxy(Proxy proxy) throws ContainerProxyException {

    }

    @Override
    public BiConsumer<OutputStream, OutputStream> getOutputAttacher(Proxy proxy) {
        return null;
    }

    @Override
    public List<ExistingContainerInfo> scanExistingContainers() throws Exception {
        return new ArrayList<>();
    }

    @Override
    public Map<String, URI> setupPortMappingExistingProxy(Proxy proxy, Container container, Map<Integer, Integer> portBindings) throws Exception {
        return null;
    }

    public void setDelegate(IContainerBackend backend) {
        this.delegateBackend = backend;
    }

    public Pair<String, Map<String, URI>> assignDelegatedProxy(String specId) {
        Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
        String id = UUID.randomUUID().toString();
        proxyBuilder.id(id);
        proxyBuilder.targetId(id);
        proxyBuilder.status(ProxyStatus.New);
        proxyBuilder.userId("SHINYPROXY_CONTAINER_SHARING"); // TODO
        proxyBuilder.specId(specId);
        proxyBuilder.createdTimestamp(System.currentTimeMillis());
        // TODO add minimal set of runtimevalues
        proxyBuilder.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, "/app_proxy/" + id), false); // TODO

        // create container objects
        ProxySpec proxySpec = proxyService.getProxySpec(specId);

        Proxy proxy = proxyBuilder.build();

        SpecExpressionContext context = SpecExpressionContext.create(proxy, proxySpec);
        proxySpec = proxySpec.firstResolve(expressionResolver, context);
        context = context.copy(proxySpec, proxy);
        proxySpec = proxySpec.finalResolve(expressionResolver, context);

        for (ContainerSpec containerSpec : proxySpec.getContainerSpecs()) {
            Container.ContainerBuilder containerBuilder = Container.builder();
            containerBuilder.index(containerSpec.getIndex());
            Container container = containerBuilder.build();
            container = runtimeValueService.addRuntimeValuesAfterSpel(containerSpec, container);
            proxyBuilder.addContainer(container);
        }
        proxy = proxyBuilder.build();
        // TODO use startupLog
        proxy = delegateBackend.startProxy(null, proxy,  proxySpec, null);
        // TODO store proxy
        // TODO store link

        return Pair.of(proxy.getTargetId(), proxy.getTargets());
    }

}
