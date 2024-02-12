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
package eu.openanalytics.containerproxy.auth.impl.oidc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.stereotype.Component;

import static eu.openanalytics.containerproxy.auth.impl.oidc.OpenIDConfiguration.REG_ID;

@Component
@ConditionalOnProperty(name = "proxy.authentication", havingValue = "openid")
public class AccessTokenDecoder implements JwtDecoder {

    private final JwtDecoder delegate;

    public AccessTokenDecoder(ClientRegistrationRepository clientRegistrationRepository, Environment environment) {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.from(environment.getProperty("proxy.openid.jwks-signature-algorithm", "RS256"));
        factory.setJwsAlgorithmResolver(clientRegistration -> signatureAlgorithm);
        factory.setJwtValidatorFactory(clientRegistration -> JwtValidators.createDefault());
        delegate = factory.createDecoder(clientRegistrationRepository.findByRegistrationId(REG_ID));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        return delegate.decode(token);
    }

}
