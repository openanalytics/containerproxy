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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ParameterNames;
import eu.openanalytics.containerproxy.model.runtime.ParameterValues;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerIndexKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CreatedTimestampKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.DisplayNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HeartbeatTimeoutKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.MaxLifetimeKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParameterNamesKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParameterValuesKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PortMappingsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxiedAppKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxyIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RealmIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserGroupsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.PortMapping;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.springframework.core.env.Environment;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Determines the RuntimeValue when a proxy gets started.
 */
@Service
public class RuntimeValueService {

    private static final String PROP_TIMEOUT = "proxy.heartbeat-timeout";

    private static final String PROP_DEFAULT_PROXY_MAX_LIFETIME = "proxy.default-proxy-max-lifetime";

    private static final Long DEFAULT_TIMEOUT = 60000L;

    private long defaultHeartbeatTimeout;

    private long defaultMaxLifetime;

    @Inject
    private ParametersService parametersService;

    @Inject
    private Environment environment;

    @Inject
    protected IdentifierService identifierService;

    @PostConstruct
    public void init() {
        defaultHeartbeatTimeout = environment.getProperty(PROP_TIMEOUT, Long.class, DEFAULT_TIMEOUT);
        defaultMaxLifetime = environment.getProperty(PROP_DEFAULT_PROXY_MAX_LIFETIME, Long.class, -1L);
    }

    public Proxy addRuntimeValuesBeforeSpel(Authentication user, ProxySpec spec, Proxy proxy) {
        Proxy.ProxyBuilder proxyBuilder = proxy.toBuilder();
        proxyBuilder.addRuntimeValue(new RuntimeValue(ProxiedAppKey.inst, "true"), false);
        proxyBuilder.addRuntimeValue(new RuntimeValue(ProxyIdKey.inst, proxy.getId()), false);
        proxyBuilder.addRuntimeValue(new RuntimeValue(InstanceIdKey.inst, identifierService.instanceId), false);
        proxyBuilder.addRuntimeValue(new RuntimeValue(ProxySpecIdKey.inst, spec.getId()), false);
        if (spec.getDisplayName() == null || spec.getDisplayName().isEmpty()) {
            proxyBuilder.addRuntimeValue(new RuntimeValue(DisplayNameKey.inst, spec.getId()), true);
        } else {
            proxyBuilder.addRuntimeValue(new RuntimeValue(DisplayNameKey.inst, spec.getDisplayName()), true);
        }

        if (identifierService.realmId != null) {
            proxyBuilder.addRuntimeValue(new RuntimeValue(RealmIdKey.inst, identifierService.realmId), false);
        }
        proxyBuilder.addRuntimeValue(new RuntimeValue(UserIdKey.inst, proxy.getUserId()), false);
        List<String> groups = UserService.getGroups(user);
        proxyBuilder.addRuntimeValue(new RuntimeValue(UserGroupsKey.inst, String.join(",", groups)), true);
        proxyBuilder.addRuntimeValue(new RuntimeValue(CreatedTimestampKey.inst, Long.toString(proxy.getCreatedTimestamp())), false);

        return proxyBuilder.build();
    }

    public Proxy addRuntimeValuesAfterSpel(ProxySpec spec, Proxy proxy) {
        Proxy.ProxyBuilder proxyBuilder = proxy.toBuilder();

        proxyBuilder.addRuntimeValue(new RuntimeValue(HeartbeatTimeoutKey.inst, spec.getHeartbeatTimeout().getValueOrDefault(defaultHeartbeatTimeout)), true);
        proxyBuilder.addRuntimeValue(new RuntimeValue(MaxLifetimeKey.inst, spec.getMaxLifeTime().getValueOrDefault(defaultMaxLifetime)), true);

        return proxyBuilder.build();
    }

    public Container addRuntimeValuesAfterSpel(ContainerSpec containerSpec, Container container) {
        Container.ContainerBuilder containerBuilder = container.toBuilder();
        containerBuilder.addRuntimeValue(new RuntimeValue(ContainerIndexKey.inst, container.getIndex()), false);
        containerBuilder.addRuntimeValue(new RuntimeValue(ContainerImageKey.inst, containerSpec.getImage().getValue()), false);

        PortMappings portMappings = new PortMappings();
        for (PortMapping portMapping : containerSpec.getPortMapping()) {
            portMappings.addPortMapping(new PortMappings.PortMappingEntry(
                    portMapping.getName(), portMapping.getPort(),
                    AbstractContainerBackend.computeTargetPath(portMapping.getTargetPath().getValueOrNull())));
        }

        containerBuilder.addRuntimeValue(new RuntimeValue(PortMappingsKey.inst, portMappings), false);
        return containerBuilder.build();
    }

    public Proxy processParameters(Authentication user, ProxySpec spec, Map<String, String> parameters, Proxy proxy) throws InvalidParametersException {
        Proxy.ProxyBuilder proxyBuilder = proxy.toBuilder();
        Optional<Pair<ParameterNames, ParameterValues>> providedParametersOptional = parametersService.parseAndValidateRequest(user, spec, parameters);
        if (providedParametersOptional.isPresent()) {
            proxyBuilder.addRuntimeValue(new RuntimeValue(ParameterNamesKey.inst, providedParametersOptional.get().getFirst()), true);
            proxyBuilder.addRuntimeValue(new RuntimeValue(ParameterValuesKey.inst, providedParametersOptional.get().getSecond()), true);
        }
        return proxyBuilder.build();
    }
}
