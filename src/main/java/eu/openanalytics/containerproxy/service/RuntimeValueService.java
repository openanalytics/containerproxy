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

import eu.openanalytics.containerproxy.model.runtime.ProvidedParameters;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HeartbeatTimeoutKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.MaxLifetimeKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParametersKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
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
    private SpecExpressionResolver expressionResolver;

    @Inject
    private Environment environment;

    @Inject
    private UserService userService;

    @PostConstruct
    public void init() {
        defaultHeartbeatTimeout = environment.getProperty(PROP_TIMEOUT, Long.class, DEFAULT_TIMEOUT);
        defaultMaxLifetime = environment.getProperty(PROP_DEFAULT_PROXY_MAX_LIFETIME, Long.class, -1L);
    }

    public void createRuntimeValues(ProxySpec spec, Map<String, String> parameters, Proxy proxy) throws InvalidParametersException {
        Optional<ProvidedParameters> providedParametersOptional = parametersService.parseAndValidateRequest(userService.getCurrentAuth(), spec, parameters);
        if (providedParametersOptional.isPresent()) {
            proxy.addRuntimeValue(new RuntimeValue(ParametersKey.inst, providedParametersOptional.get()));
        }
        SpecExpressionContext context = SpecExpressionContext.create(
                proxy,
                proxy.getSpec(),
                userService.getCurrentAuth().getPrincipal(),
                userService.getCurrentAuth().getCredentials());

        if (spec.getHeartbeatTimeout() != null) {
            Long timeout = expressionResolver.evaluateToLong(spec.getHeartbeatTimeout(), context);
            proxy.addRuntimeValue(new RuntimeValue(HeartbeatTimeoutKey.inst, timeout));
        } else {
            proxy.addRuntimeValue(new RuntimeValue(HeartbeatTimeoutKey.inst, defaultHeartbeatTimeout));
        }

        if (spec.getMaxLifeTime() != null) {
            Long maxLifetime = expressionResolver.evaluateToLong(spec.getMaxLifeTime(), context);
            proxy.addRuntimeValue(new RuntimeValue(MaxLifetimeKey.inst, maxLifetime));
        } else {
            proxy.addRuntimeValue(new RuntimeValue(MaxLifetimeKey.inst, defaultMaxLifetime));
        }
    }

}
