/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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

import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ParameterNames;
import eu.openanalytics.containerproxy.model.runtime.ParameterValues;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerIndexKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CreatedTimestampKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HeartbeatTimeoutKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.MaxLifetimeKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParameterNamesKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParameterValuesKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxiedAppKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxyIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RealmIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.TargetPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserGroupsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
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
    private UserService userService;

    @Inject
    protected IdentifierService identifierService;

    @PostConstruct
    public void init() {
        defaultHeartbeatTimeout = environment.getProperty(PROP_TIMEOUT, Long.class, DEFAULT_TIMEOUT);
        defaultMaxLifetime = environment.getProperty(PROP_DEFAULT_PROXY_MAX_LIFETIME, Long.class, -1L);
    }

    public void addRuntimeValuesBeforeSpel(ProxySpec spec, Map<String, String> parameters, Proxy proxy) throws InvalidParametersException {
        proxy.putRuntimeValue(new RuntimeValue(ProxiedAppKey.inst, "true"), false);
        proxy.putRuntimeValue(new RuntimeValue(ProxyIdKey.inst, proxy.getId()), false);
        proxy.putRuntimeValue(new RuntimeValue(InstanceIdKey.inst, identifierService.instanceId), false);
        proxy.putRuntimeValue(new RuntimeValue(ProxySpecIdKey.inst, spec.getId()), false);

        if (identifierService.realmId != null) {
            proxy.putRuntimeValue(new RuntimeValue(RealmIdKey.inst, identifierService.realmId), false);
        }
        proxy.putRuntimeValue(new RuntimeValue(UserIdKey.inst, proxy.getUserId()), false);
        String[] groups = UserService.getGroups(userService.getCurrentAuth());
        proxy.putRuntimeValue(new RuntimeValue(UserGroupsKey.inst, String.join(",", groups)), true);
        proxy.putRuntimeValue(new RuntimeValue(CreatedTimestampKey.inst, Long.toString(proxy.getCreatedTimestamp())), false);

        // parameters
        Optional<Pair<ParameterNames, ParameterValues>> providedParametersOptional = parametersService.parseAndValidateRequest(userService.getCurrentAuth(), spec, parameters);
        if (providedParametersOptional.isPresent()) {
            proxy.putRuntimeValue(new RuntimeValue(ParameterNamesKey.inst, providedParametersOptional.get().getKey()), true);
            proxy.putRuntimeValue(new RuntimeValue(ParameterValuesKey.inst, providedParametersOptional.get().getValue()), true);
        }
    }

    public void addRuntimeValuesAfterSpel(ProxySpec spec, Proxy proxy) {
        proxy.putRuntimeValue(new RuntimeValue(HeartbeatTimeoutKey.inst, spec.getHeartbeatTimeout().getValueOrDefault(defaultHeartbeatTimeout)), true);
        proxy.putRuntimeValue(new RuntimeValue(MaxLifetimeKey.inst, spec.getMaxLifeTime().getValueOrDefault(defaultMaxLifetime)), true);
    }

    public void addRuntimeValuesAfterSpel(ContainerSpec containerSpec, Container container) {
        container.putRuntimeValue(new RuntimeValue(ContainerIndexKey.inst, container.getIndex()), false);
        container.putRuntimeValue(new RuntimeValue(ContainerImageKey.inst, containerSpec.getImage().getValue()), false);
        if (containerSpec.getTargetPath().isPresent()) {
            container.putRuntimeValue(new RuntimeValue(TargetPathKey.inst, containerSpec.getTargetPath().getValue()), true);
        } else {
            container.putRuntimeValue(new RuntimeValue(TargetPathKey.inst, ""), true);
        }
    }

}
