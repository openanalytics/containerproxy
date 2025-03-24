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
package eu.openanalytics.containerproxy.stat;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingMicrometer;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;
import eu.openanalytics.containerproxy.stat.impl.CSVCollector;
import eu.openanalytics.containerproxy.stat.impl.InfluxDBCollector;
import eu.openanalytics.containerproxy.stat.impl.JDBCCollector;
import eu.openanalytics.containerproxy.stat.impl.Micrometer;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;


@Configuration
@ConfigurationProperties(prefix = "proxy")
public class StatCollectorFactory {

    @Setter
    @Getter
    private List<UsageStats> usageStats;

    @Setter
    @Getter
    private SpelField.String usageStatsUrl = new SpelField.String();

    @Setter
    @Getter
    private SpelField.String usageStatsUsername = new SpelField.String();

    @Setter
    @Getter
    private SpelField.String usageStatsPassword = new SpelField.String();

    @Setter
    @Getter
    private SpelField.String usageStatsTableName = new SpelField.String();

    @Setter
    @Getter
    private List<UsageStatsAttribute> usageStatsAttributes;

    private final Logger log = LogManager.getLogger(StatCollectorFactory.class);

    @Inject
    private SpecExpressionResolver specExpressionResolver;

    @Inject
    private ConfigurableListableBeanFactory beanFactory;

    @PostConstruct
    public void init() {
        SpecExpressionContext specExpressionContext = SpecExpressionContext.create();
        String resolvedUsageStatsUrl = usageStatsUrl.resolve(specExpressionResolver, specExpressionContext).getValueOrNull();
        if (resolvedUsageStatsUrl != null && !resolvedUsageStatsUrl.isEmpty()) {
            createBean(createCollector(
                resolvedUsageStatsUrl,
                usageStatsUsername.resolve(specExpressionResolver, specExpressionContext).getValueOrNull(),
                usageStatsPassword.resolve(specExpressionResolver, specExpressionContext).getValueOrNull(),
                usageStatsTableName.resolve(specExpressionResolver, specExpressionContext).getValueOrNull(),
                usageStatsAttributes), "IStatsCollector");
        }

        if (usageStats != null) {
            int i = 0;
            for (UsageStats usageStats : getUsageStats()) {
                UsageStats resolved = usageStats.resolve(specExpressionResolver, specExpressionContext);
                createBean(createCollector(
                    resolved.url.getValueOrNull(),
                    resolved.username.getValueOrNull(),
                    resolved.password.getValueOrNull(),
                    resolved.tableName.getValueOrNull(),
                    usageStats.attributes), "IStatsCollector-" + i);
                i++;
            }
        }

    }

    private IStatCollector createCollector(String url, String username, String password, String tableName, List<UsageStatsAttribute> usageStatsAttributes) {
        log.info("Enabled. Sending usage statistics to {}.", url);

        if (url.toLowerCase().contains("/write?db=")) {
            return new InfluxDBCollector(url);
        } else if (url.toLowerCase().startsWith("jdbc")) {
            return new JDBCCollector(url, username, password, tableName == null ? "event" : tableName, usageStatsAttributes);
        } else if (url.equalsIgnoreCase("micrometer")) {
            createBean(new ProxySharingMicrometer(), "ProxySharingMicrometer");
            return new Micrometer();
        } else if (url.toLowerCase().endsWith(".csv")) {
                return new CSVCollector(url, usageStatsAttributes);
        } else {
            throw new IllegalArgumentException(String.format("Base url for statistics contains an unrecognized values, baseURL %s.", url));
        }
    }

    @Data
    @Builder(toBuilder = true)
    @AllArgsConstructor(access = AccessLevel.PRIVATE) // force Spring to not use constructor
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
    public static class UsageStats {

        @Builder.Default
        private SpelField.String url = new SpelField.String();

        @Builder.Default
        private SpelField.String username = new SpelField.String();

        @Builder.Default
        private SpelField.String password = new SpelField.String();

        @Builder.Default
        private SpelField.String tableName = new SpelField.String();

        private List<UsageStatsAttribute> attributes;

        public UsageStats resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            return toBuilder()
                .url(url.resolve(specExpressionResolver, specExpressionContext))
                .username(username.resolve(specExpressionResolver, specExpressionContext))
                .password(password.resolve(specExpressionResolver, specExpressionContext))
                .tableName(tableName.resolve(specExpressionResolver, specExpressionContext))
                .build();
        }

    }

    @Data
    public static class UsageStatsAttribute {

        private String name;
        private String expression;

    }

    private <T> void createBean(T bean, String beanName) {
        beanFactory.autowireBean(bean);
        Object initializedBean = beanFactory.initializeBean(bean, beanName);
        beanFactory.registerSingleton(beanName, initializedBean);
    }

}
