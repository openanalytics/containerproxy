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
package eu.openanalytics.containerproxy.auth.impl.oidc.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public class RedisOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService  {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    private RedisTemplate<String, OAuth2AuthorizedClient> redisTemplate;

    private String redisKey;

    private HashOperations<String, String, OAuth2AuthorizedClient> ops;

    @PostConstruct
    public void init() {
        redisKey = "shinyproxy_spring_oauth_authorized_clients__";
        ops = redisTemplate.opsForHash();
    }

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        logger.debug("Load AuthorizedClient for {}", principalName);
        return (T) ops.get(redisKey + '_' + clientRegistrationId, principalName);
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        logger.debug("Save AuthorizedClient for {}", principal.getName());
        ops.put(redisKey + '_' + authorizedClient.getClientRegistration().getRegistrationId(), principal.getName(), authorizedClient);
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        logger.debug("Remove AuthorizedClient for {}", principalName);
        ops.delete(redisKey + '_' + clientRegistrationId, principalName);
    }

}
