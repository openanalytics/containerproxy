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
package eu.openanalytics.containerproxy.auth.impl.oidc;

import eu.openanalytics.containerproxy.auth.impl.oidc.redis.RedisOAuth2AuthorizedClientService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Configuration
@ConditionalOnProperty(name = "proxy.authentication", havingValue = "openid")
public class OpenIDConfiguration {

    public static final String REG_ID = "shinyproxy";

    @Inject
    private Environment environment;

    @Bean
    public OAuth2AuthorizedClientService oAuth2AuthorizedClientService() {
        if (environment.getProperty("proxy.store-mode", "None").equals("Redis")) {
            return new RedisOAuth2AuthorizedClientService();
        }
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository());
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        Set<String> scopes = new HashSet<>();
        scopes.add("openid");
        scopes.add("email");

        for (int i = 0; ; i++) {
            String scope = environment.getProperty(String.format("proxy.openid.scopes[%d]", i));
            if (scope == null) break;
            else scopes.add(scope);
        }

        ClientRegistration client = ClientRegistration.withRegistrationId(REG_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientName(REG_ID)
                .redirectUriTemplate("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(scopes.toArray(new String[scopes.size()]))
                .userNameAttributeName(environment.getProperty("proxy.openid.username-attribute", "email"))
                .authorizationUri(environment.getProperty("proxy.openid.auth-url"))
                .tokenUri(environment.getProperty("proxy.openid.token-url"))
                .jwkSetUri(environment.getProperty("proxy.openid.jwks-url"))
                .clientId(environment.getProperty("proxy.openid.client-id"))
                .clientSecret(environment.getProperty("proxy.openid.client-secret"))
                .userInfoUri(environment.getProperty("proxy.openid.userinfo-url"))
                .build();

        return new InMemoryClientRegistrationRepository(Collections.singletonList(client));
    }

    @Bean
    public OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(oAuth2AuthorizedClientService());
    }

    @Bean
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager() {
        return new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository(), oAuth2AuthorizedClientRepository());
    }

    @Bean
    public OpenIdReAuthorizeFilter openIdReAuthorizeFilter() {
        return new OpenIdReAuthorizeFilter();
    }

}
