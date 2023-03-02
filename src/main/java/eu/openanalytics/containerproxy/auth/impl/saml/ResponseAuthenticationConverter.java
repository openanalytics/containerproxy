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
package eu.openanalytics.containerproxy.auth.impl.saml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.OpenSamlAuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.DEFAULT_NAME_ATTRIBUTE;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.PROP_LOG_ATTRIBUTES;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.PROP_NAME_ATTRIBUTE;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.PROP_ROLES_ATTRIBUTE;

@SuppressWarnings("deprecation")
public class ResponseAuthenticationConverter implements Converter<OpenSamlAuthenticationProvider.ResponseToken, AbstractAuthenticationToken> {

    private final Boolean logAttributes;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String nameAttribute;
    private final String rolesAttribute;

    public ResponseAuthenticationConverter(Environment environment) {
        logAttributes = environment.getProperty(PROP_LOG_ATTRIBUTES, Boolean.class, false);
        nameAttribute = environment.getProperty(PROP_NAME_ATTRIBUTE, DEFAULT_NAME_ATTRIBUTE);
        rolesAttribute = environment.getProperty(PROP_ROLES_ATTRIBUTE);
    }

    @Override
    public AbstractAuthenticationToken convert(@Nonnull OpenSamlAuthenticationProvider.ResponseToken responseToken) {
        Saml2Authentication authentication = OpenSamlAuthenticationProvider
                .createDefaultResponseAuthenticationConverter()
                .convert(responseToken);

        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("No authentication found to convert");
        }

        DefaultSaml2AuthenticatedPrincipal principal = (DefaultSaml2AuthenticatedPrincipal) authentication.getPrincipal();

        String nameId = principal.getName();

        if (logAttributes) {
            logAttributes(principal);
        }

        Optional<String> nameValue = getSingleAttributeValue(principal, nameAttribute);
        if (!nameValue.isPresent()) {
            throw new UsernameNotFoundException(String.format("[SAML] User: \"%s\" => name attribute missing from SAML assertion", nameId));
        }

        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        if (rolesAttribute != null && !rolesAttribute.trim().isEmpty()) {
            Optional<List<String>> rolesValue = getMultipleAttributeValues(principal, rolesAttribute);
            if (!rolesValue.isPresent()) {
                logger.warn("[SAML] User: \"{}\" => roles attribute missing from SAML assertion", nameId);
            } else {
                grantedAuthorities = rolesValue.get().stream()
                        .map(r -> {
                            if (!r.startsWith("ROLE_")) {
                                r = "ROLE_" + r;
                            }
                            return new SimpleGrantedAuthority(r);
                        })
                        .collect(Collectors.toList());
            }
        }

        if (logAttributes) {
            logger.warn("[SAML] User: \"{}\" => has roles \"{}\"", nameId, grantedAuthorities);
        }

        return new Saml2Authentication(
                new Saml2AuthenticatedPrincipal(nameId, nameValue.get(), principal.getAttributes()),
                authentication.getSaml2Response(),
                grantedAuthorities);
    }

    private void logAttributes(DefaultSaml2AuthenticatedPrincipal principal) {
        for (Map.Entry<String, List<Object>> attribute : principal.getAttributes().entrySet()) {
            logger.info(String.format("[SAML] User: \"%s\" => attribute => name=\"%s\" => value \"%s\"",
                    principal.getName(),
                    attribute.getKey(),
                    attribute.getValue().stream().map(Object::toString).collect(Collectors.joining(", "))));
        }
    }

    private Optional<String> getSingleAttributeValue(DefaultSaml2AuthenticatedPrincipal principal, String attributeName) {
        Optional<List<Object>> res = getAttributeIgnoringCase(principal, attributeName);
        if (!res.isPresent() || res.get().size() == 0) {
            return Optional.empty();
        }
        return Optional.of(res.get().get(0).toString());
    }

    private Optional<List<String>> getMultipleAttributeValues(DefaultSaml2AuthenticatedPrincipal principal, String attributeName) {
        return getAttributeIgnoringCase(principal, attributeName)
                .map(objects -> objects
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())
                );
    }

    private Optional<List<Object>> getAttributeIgnoringCase(DefaultSaml2AuthenticatedPrincipal principal, String attributeName) {
        return principal.getAttributes()
                .entrySet()
                .stream()
                .filter(c -> c.getKey().equalsIgnoreCase(attributeName))
                .findAny()
                .map(Map.Entry::getValue);
    }

    public static class Saml2AuthenticatedPrincipal extends DefaultSaml2AuthenticatedPrincipal {

        private final String nameId;

        public Saml2AuthenticatedPrincipal(String nameId, String principalName, Map<String, List<Object>> attributes) {
            super(principalName, attributes);
            this.nameId = nameId;
        }

        public String getNameId() {
            return nameId;
        }

    }

}
