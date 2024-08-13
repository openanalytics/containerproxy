/**
 * ContainerProxy
 *
<<<<<<< HEAD
 * Copyright (C) 2016-2024 Open Analytics
=======
 * Copyright (C) 2016-2023 Open Analytics
>>>>>>> d57455466c9e5d7069e2878c7b751ec110a99b8c
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
package eu.openanalytics.containerproxy.auth.impl.customHeader;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "proxy.authentication", havingValue = "customHeader")
public class CustomHeaderConfiguration {

    public static final String REG_ID = "shinyproxy";
    public static final String PROP_CUSTOM_HEADER = "proxy.customHeader";


    @Bean
    public CustomHeaderAuthenticationFilter customHeaderAuthorizeFilter() {
        return new CustomHeaderAuthenticationFilter();
    }
    
}
