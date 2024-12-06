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
package eu.openanalytics.containerproxy.stat;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingMicrometer;
import eu.openanalytics.containerproxy.stat.impl.InfluxDBCollector;
import eu.openanalytics.containerproxy.stat.impl.JDBCCollector;
import eu.openanalytics.containerproxy.stat.impl.Micrometer;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.List;


@Component
public class StatCollectorFactory implements BeanFactoryPostProcessor, EnvironmentAware {

    @Setter
    @Getter
    private List<UsageStats> usageStats;

    @Setter
    @Getter
    private String usageStatsUrl;

    @Setter
    @Getter
    private String usageStatsUsername;

    @Setter
    @Getter
    private String usageStatsPassword;

    @Setter
    @Getter
    private String usageStatsTableName;

    @Setter
    @Getter
    private List<UsageStatsAttribute> usageStatsAttributes;

    private final Logger log = LogManager.getLogger(StatCollectorFactory.class);

    private Environment environment;

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        BindResult<StatCollectorFactory> bindResult = Binder.get(environment).bind("proxy", StatCollectorFactory.class);
        if (!bindResult.isBound()) {
            return;
        }
        StatCollectorFactory result = bindResult.get();
        DefaultListableBeanFactory registry = (DefaultListableBeanFactory) configurableListableBeanFactory;

        if (result.getUsageStats() != null) {
            for (UsageStats usageStats : result.getUsageStats()) {
                createCollector(registry, usageStats.url, usageStats.username, usageStats.password, usageStats.tableName, usageStats.attributes);
            }
        }

        if (result.usageStatsUrl != null && !result.usageStatsUrl.isEmpty()) {
            createCollector(registry, result.usageStatsUrl, result.usageStatsUsername, result.usageStatsPassword, result.usageStatsTableName, result.usageStatsAttributes);
        }
    }

    private void createCollector(DefaultListableBeanFactory registry, String url, String username, String password, String tableName, List<UsageStatsAttribute> usageStatsAttributes) {
        log.info(String.format("Enabled. Sending usage statistics to %s.", url));

        BeanDefinition bd = new GenericBeanDefinition();
        if (url.toLowerCase().contains("/write?db=")) {
            bd.setBeanClassName(InfluxDBCollector.class.getName());
            bd.getConstructorArgumentValues().addGenericArgumentValue(url);
        } else if (url.toLowerCase().startsWith("jdbc")) {
            bd.setBeanClassName(JDBCCollector.class.getName());
            bd.getConstructorArgumentValues().addGenericArgumentValue(url);
            bd.getConstructorArgumentValues().addGenericArgumentValue(username);
            bd.getConstructorArgumentValues().addGenericArgumentValue(password);
            bd.getConstructorArgumentValues().addGenericArgumentValue(tableName == null ? "event" : tableName);
            bd.getConstructorArgumentValues().addGenericArgumentValue(usageStatsAttributes);
        } else if (url.equalsIgnoreCase("micrometer")) {
            bd.setBeanClassName(Micrometer.class.getName());

            BeanDefinition bd2 = new GenericBeanDefinition();
            bd2.setBeanClassName(ProxySharingMicrometer.class.getName());
            registry.registerBeanDefinition("ProxySharingMicrometer", bd2);
        } else {
            throw new IllegalArgumentException(String.format("Base url for statistics contains an unrecognized values, baseURL %s.", url));
        }

        registry.registerBeanDefinition(url, bd);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Data
    public static class UsageStats {

        private String url;
        private String username;
        private String password;
        private String tableName;
        private List<UsageStatsAttribute> attributes;

    }

    @Data
    public static class UsageStatsAttribute {

        private String name;
        private String expression;

    }

}
