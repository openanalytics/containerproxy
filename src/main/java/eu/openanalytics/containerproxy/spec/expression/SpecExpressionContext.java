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
package eu.openanalytics.containerproxy.spec.expression;

import com.fasterxml.jackson.databind.JsonNode;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.WebServiceAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.saml.ResponseAuthenticationConverter;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Value
@EqualsAndHashCode(doNotUseGetters = true)
@Builder(toBuilder = true)
@AllArgsConstructor
public class SpecExpressionContext {

    ContainerSpec containerSpec;
    ProxySpec proxySpec;
    Proxy proxy;
    OpenIDAuthenticationBackend.CustomNameOidcUser oidcUser;
    ResponseAuthenticationConverter.Saml2AuthenticatedPrincipal samlCredential;
    LdapUserDetails ldapUser;
    WebServiceAuthenticationBackend.WebServiceUser webServiceUser;
    List<String> groups;
    String userId;
    JsonNode json;

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
            } else if (o instanceof ResponseAuthenticationConverter.Saml2AuthenticatedPrincipal) {
                builder.samlCredential = (ResponseAuthenticationConverter.Saml2AuthenticatedPrincipal) o;
            } else if (o instanceof LdapUserDetails) {
                builder.ldapUser = (LdapUserDetails) o;
            } else if (o instanceof WebServiceAuthenticationBackend.WebServiceUser) {
                builder.webServiceUser = (WebServiceAuthenticationBackend.WebServiceUser) o;
            } else if (o instanceof JsonNode) {
                builder.json = (JsonNode) o;
            }
            if (o instanceof Authentication) {
                builder.groups = UserService.getGroups((Authentication) o);
                builder.userId = ((Authentication) o).getName();
            }
        }
        return builder.build();
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting according to the provided regex and trimming each result
     */
    public List<String> toList(String attribute, String regex) {
        if (attribute == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(attribute.split(regex)).map(String::trim).toList();
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
        return Arrays.stream(attribute.split(regex)).map(it -> it.trim().toLowerCase()).toList();
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

    public SpecExpressionContext copy(Object... objects) {
        return create(toBuilder(), objects);
    }

    public ProxySpec getProxySpec() {
        if (proxySpec == null) {
            throw new SpelContextObjectNotAvailableException("proxySpec");
        }
        return proxySpec;
    }

    public ContainerSpec getContainerSpec() {
        if (containerSpec == null) {
            throw new SpelContextObjectNotAvailableException("containerSpec");
        }
        return containerSpec;
    }

    public Proxy getProxy() {
        if (proxy == null) {
            throw new SpelContextObjectNotAvailableException("proxy");
        }
        return proxy;
    }

    public OpenIDAuthenticationBackend.CustomNameOidcUser getOidcUser() {
        if (oidcUser == null) {
            throw new SpelContextObjectNotAvailableException("oidcUser");
        }
        return oidcUser;
    }

    public ResponseAuthenticationConverter.Saml2AuthenticatedPrincipal getSamlCredential() {
        if (samlCredential == null) {
            throw new SpelContextObjectNotAvailableException("samlCredential");
        }
        return samlCredential;
    }

    public LdapUserDetails getLdapUser() {
        if (ldapUser == null) {
            throw new SpelContextObjectNotAvailableException("ldapUser");
        }
        return ldapUser;
    }

    public WebServiceAuthenticationBackend.WebServiceUser getWebServiceUser() {
        if (oidcUser == null) {
            throw new SpelContextObjectNotAvailableException("webServiceUser");
        }
        return webServiceUser;
    }

    public List<String> getGroups() {
        if (groups == null) {
            throw new SpelContextObjectNotAvailableException("groups");
        }
        return groups;
    }

    public String getUserId() {
        if (userId == null) {
            throw new SpelContextObjectNotAvailableException("userId");
        }
        return userId;
    }

    public JsonNode getJson() {
        if (json == null) {
            throw new SpelContextObjectNotAvailableException("json");
        }
        return json;
    }
}
