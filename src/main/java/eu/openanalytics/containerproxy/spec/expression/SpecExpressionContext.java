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
package eu.openanalytics.containerproxy.spec.expression;

import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.saml.ResponseAuthenticationConverter;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.keycloak.KeycloakPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode()
@Builder(toBuilder = true)
@AllArgsConstructor
public class SpecExpressionContext {

    ContainerSpec containerSpec;
    ProxySpec proxySpec;
    Proxy proxy;
    OpenIDAuthenticationBackend.CustomNameOidcUser oidcUser;
    KeycloakPrincipal keycloakUser;
    ResponseAuthenticationConverter.Saml2AuthenticatedPrincipal samlCredential;
    LdapUserDetails ldapUser;
    List<String> groups;
    String userId;

    /**
     * Convert a {@see String} to a list of strings, by splitting according to the provided regex and trimming each result
     */
    public List<String> toList(String attribute, String regex) {
        if (attribute == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(attribute.split(regex)).map(String::trim).collect(Collectors.toList());
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting on `,` and trimming each result
     */
    public List<String> toList(String attribute) {
        return toList(attribute, ",");
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting on according to the provided regex.
     * Each result is trimmed and converted to lowercase.
     */
    public List<String> toLowerCaseList(String attribute, String regex) {
        if (attribute == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(attribute.split(regex)).map(it -> it.trim().toLowerCase()).collect(Collectors.toList());
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting on `,`. Each result is trimmed and converted to lowercase.
     */
    public List<String> toLowerCaseList(String attribute) {
        return toLowerCaseList(attribute, ",");
    }

    /**
     * Returns true when the provided value is in the list of allowed values.
     * Both the attribute and allowed values are trimmed.
     */
    public boolean isOneOf(String attribute, String... allowedValues) {
        if (attribute == null) {
            return false;
        }
        return Arrays.stream(allowedValues).anyMatch(it -> it.trim().equals(attribute.trim()));
    }

    /**
     * Returns true when the provided value is in the list of allowed values.
     * Both the attribute and allowed values are trimmed and the comparison ignores casing of the values.
     */
    public boolean isOneOfIgnoreCase(String attribute, String... allowedValues) {
        if (attribute == null) {
            return false;
        }
        return Arrays.stream(allowedValues).anyMatch(it -> it.trim().equalsIgnoreCase(attribute.trim()));
    }

    public static SpecExpressionContext create(Object... objects) {
        SpecExpressionContextBuilder builder = SpecExpressionContext.builder();
        return create(builder, objects);
    }

    public static SpecExpressionContext create(SpecExpressionContextBuilder builder, Object... objects) {
        for (Object o : objects) {
            if (o instanceof ContainerSpec) {
                builder.containerSpec = (ContainerSpec) o;
            } else if (o instanceof ProxySpec) {
                builder.proxySpec = (ProxySpec) o;
            } else if (o instanceof Proxy) {
                builder.proxy = (Proxy) o;
            } else if (o instanceof OpenIDAuthenticationBackend.CustomNameOidcUser) {
                builder.oidcUser = (OpenIDAuthenticationBackend.CustomNameOidcUser) o;
            } else if (o instanceof KeycloakPrincipal) {
                builder.keycloakUser = (KeycloakPrincipal) o;
            } else if (o instanceof ResponseAuthenticationConverter.Saml2AuthenticatedPrincipal) {
                builder.samlCredential = (ResponseAuthenticationConverter.Saml2AuthenticatedPrincipal) o;
            } else if (o instanceof LdapUserDetails) {
                builder.ldapUser = (LdapUserDetails) o;
            }
            if (o instanceof Authentication) {
                builder.groups = UserService.getGroups((Authentication) o);
                builder.userId = UserService.getUserId(((Authentication) o));
            }
        }
        return builder.build();
    }

    public SpecExpressionContext copy(Object... objects) {
        return create(toBuilder(), objects);
    }

}
