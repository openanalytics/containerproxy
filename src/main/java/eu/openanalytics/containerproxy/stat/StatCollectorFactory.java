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
package eu.openanalytics.containerproxy.stat;

import eu.openanalytics.containerproxy.stat.impl.InfluxDBCollector;
import eu.openanalytics.containerproxy.stat.impl.JDBCCollector;
import eu.openanalytics.containerproxy.stat.impl.Micrometer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class StatCollectorFactory implements BeanFactoryPostProcessor, EnvironmentAware {

    private final Logger log = LogManager.getLogger(StatCollectorFactory.class);

    private static final String PROP_USAGE_STATS_MULTI_URL = "proxy.usage-stats[%d].url";
    private static final String PROP_USAGE_STATS_MULTI_USERNAME = "proxy.usage-stats[%d].username";
    private static final String PROP_USAGE_STATS_MULTI_PASSWORD = "proxy.usage-stats[%d].password";

    private Environment environment;

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        DefaultListableBeanFactory registry = (DefaultListableBeanFactory) configurableListableBeanFactory;

        int i = 0;
        String usageStatsUrl = environment.getProperty(String.format(PROP_USAGE_STATS_MULTI_URL, i));
        while (usageStatsUrl != null) {
            String username = environment.getProperty(String.format(PROP_USAGE_STATS_MULTI_USERNAME, i));
            String password = environment.getProperty(String.format(PROP_USAGE_STATS_MULTI_PASSWORD, i));
            createCollector(registry, usageStatsUrl, username, password);

            i++;
            usageStatsUrl = environment.getProperty(String.format(PROP_USAGE_STATS_MULTI_URL, i));
        }

        String baseURL = environment.getProperty("proxy.usage-stats-url");
        if (baseURL != null && !baseURL.isEmpty()) {
            String username = environment.getProperty("proxy.usage-stats-username");
            String password = environment.getProperty("proxy.usage-stats-password");
            createCollector(registry, baseURL, username, password);
        }
    }

    private void createCollector(DefaultListableBeanFactory registry, String url, String username, String password) {
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
        } else if (url.equalsIgnoreCase("micrometer")) {
            bd.setBeanClassName(Micrometer.class.getName());
        } else {
            throw new IllegalArgumentException(String.format("Base url for statistics contains an unrecognized values, baseURL %s.", url));
        }

        registry.registerBeanDefinition(url, bd);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
