/*
 * PKCE with confidential clients will eventually become the default behavior in Spring Security,
 * but for now a custom authorization request resolver is necessary.
 *
 * Code is based on example on Okta blog:
 * https://developer.okta.com/blog/2020/01/23/pkce-oauth2-spring-boot#making-pkce-work-for-confidential-clients-in-spring-security
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

package eu.openanalytics.containerproxy.security;

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AlwaysPkceAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final OAuth2AuthorizationRequestResolver defaultResolver;
    private final StringKeyGenerator secureKeyGenerator = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

    public AlwaysPkceAuthorizationRequestResolver(ClientRegistrationRepository repo, String authorizationRequestBaseUri) {
        defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest servletRequest) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(servletRequest);
        return customizeAuthorizationRequest(req);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest servletRequest, String clientRegistrationId) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(servletRequest, clientRegistrationId);
        return customizeAuthorizationRequest(req);
    }

    private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest req) {
        if (req == null) {
            return null;
        }

        Map<String, Object> attributes = new HashMap<>(req.getAttributes());
        Map<String, Object> additionalParameters = new HashMap<>(req.getAdditionalParameters());
        addPkceParameters(attributes, additionalParameters);
        return OAuth2AuthorizationRequest.from(req)
                .attributes(attributes)
                .additionalParameters(additionalParameters)
                .build();
    }

    private void addPkceParameters(Map<String, Object> attributes, Map<String, Object> additionalParameters) {
        String codeVerifier = this.secureKeyGenerator.generateKey();
        attributes.put(PkceParameterNames.CODE_VERIFIER, codeVerifier);
        try {
            String codeChallenge = createHash(codeVerifier);
            additionalParameters.put(PkceParameterNames.CODE_CHALLENGE, codeChallenge);
            additionalParameters.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256");
        } catch (NoSuchAlgorithmException e) {
            additionalParameters.put(PkceParameterNames.CODE_CHALLENGE, codeVerifier);
        }
    }

    private static String createHash(String value) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
