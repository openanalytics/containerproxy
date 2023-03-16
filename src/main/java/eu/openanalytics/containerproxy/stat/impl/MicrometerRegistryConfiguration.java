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
package eu.openanalytics.containerproxy.stat.impl;

import eu.openanalytics.containerproxy.service.IdentifierService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.micrometer.prometheus.PrometheusNamingConvention;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.inject.Inject;

@Configuration(proxyBeanMethods = false)
public class MicrometerRegistryConfiguration {

    private static final String PROP_METRIC_PREFIX = "proxy.usage-stats-micrometer-prefix";

    @Inject
    private Environment environment;

    @Inject
    private IdentifierService identifierService;

    private String getPrefix() {
        String prefix = environment.getProperty(PROP_METRIC_PREFIX, "").trim();
        if (!prefix.isEmpty()) {
            prefix += ".";
        }
        return prefix;
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        String prefix = getPrefix();
        return (registry) -> registry
                .config()
                .namingConvention(new PrometheusNamingConvention() {
                    @NonNull
                    @Override
                    public String name(@NonNull String name, @NonNull Meter.Type type, @Nullable String baseUnit) {
                        return super.name(prefix + name, type, baseUnit);
                    }

                    @NonNull
                    @Override
                    public String tagKey(@NonNull String key) {
                        return super.tagKey(key);
                    }
                })
                // add a common tag with the instanceId of this server. (it cannot simple be called instance, since that is already a default Prometheus label).
                .commonTags("shinyproxy_instance", identifierService.instanceId,
                            "shinyproxy_realm", identifierService.realmId != null ? identifierService.realmId : "");
    }


}