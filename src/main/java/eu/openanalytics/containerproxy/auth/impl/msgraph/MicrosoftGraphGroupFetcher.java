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
package eu.openanalytics.containerproxy.auth.impl.msgraph;

import eu.openanalytics.containerproxy.util.EnvironmentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConditionalOnProperty("proxy.ms-graph.client-id")
@Component
public class MicrosoftGraphGroupFetcher {

    private static final String REGISTRATION_ID = "shinyproxy-ms-graph";
    private final String tenantId;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final WebClient webClient;

    public MicrosoftGraphGroupFetcher(Environment environment) {
        String clientId = environment.getProperty("proxy.ms-graph.client-id");
        String clientSecret = environment.getProperty("proxy.ms-graph.client-secret");
        String graphApiUrl = environment.getProperty("proxy.ms-graph.api-url", "https://graph.microsoft.com");
        String tokenUrl = environment.getProperty("proxy.ms-graph.token-url");
        List<String> scopes = EnvironmentUtils.readList(environment, "proxy.ms-graph.scopes");
        if (scopes == null || scopes.isEmpty()) {
            scopes = List.of("https://graph.microsoft.com/.default");
        }
        tenantId = environment.getProperty("proxy.ms-graph.tenant-id");

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tokenUri(tokenUrl)
            .scope(scopes)
            .build();
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth = getServerOAuth2AuthorizedClientExchangeFilterFunction(clientRegistration);

        webClient = WebClient.builder()
            .baseUrl(graphApiUrl)
            .filter(oauth)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private ServerOAuth2AuthorizedClientExchangeFilterFunction getServerOAuth2AuthorizedClientExchangeFilterFunction(ClientRegistration clientRegistration) {
        InMemoryReactiveClientRegistrationRepository clientRegistrations = new InMemoryReactiveClientRegistrationRepository(clientRegistration);
        InMemoryReactiveOAuth2AuthorizedClientService clientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrations);
        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrations, clientService);
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth.setDefaultClientRegistrationId(REGISTRATION_ID);
        return oauth;
    }

    public Set<GrantedAuthority> fetchGroups(String userId) {
        try {
            MemberOfResponse memberships = webClient.get()
                .uri(String.format("/v1.0/%s/users/%s/memberOf", tenantId, userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                    response -> response.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new IllegalStateException(String.format("Error from Microsoft Graph API, status: %s, response: %s", response.statusCode(), body))))
                )
                .bodyToFlux(MemberOfResponse.class).blockLast();

            if (memberships == null) {
                logger.warn("No group memberships found for {}", userId);
                return Set.of();
            }

            Set<GrantedAuthority> result = new HashSet<>(memberships.value.stream().map(m -> {
                if (m == null || m.displayName == null) {
                    return null;
                }
                String mappedRole = m.displayName
                    .toUpperCase()
                    .startsWith("ROLE_") ? m.displayName : "ROLE_" + m.displayName;
                return new SimpleGrantedAuthority(mappedRole.toUpperCase());
            }).filter(Objects::nonNull).toList());
            logger.debug("Received groups from Microsoft Graph api for user: {}, groups: {}", userId, result);
            return result;
        } catch (Exception e) {
            logger.warn("Error while fetching groups from Microsoft Graph API - continuing without groups", e);
            return Set.of();
        }
    }

    private record MemberOfResponse(List<GroupMembership> value) {
    }

    private record GroupMembership(String id, String displayName) {
    }

}
