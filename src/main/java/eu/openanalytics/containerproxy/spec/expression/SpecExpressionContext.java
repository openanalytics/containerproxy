/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.UserService;
import org.keycloak.KeycloakPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.security.saml.SAMLCredential;

import java.util.Arrays;
import java.util.List;

public class SpecExpressionContext {

    private ContainerSpec containerSpec;
    private ProxySpec proxySpec;
    private Proxy proxy;
    private OpenIDAuthenticationBackend.CustomNameOidcUser oicdUser;
    private KeycloakPrincipal keycloakUser;
    private SAMLCredential samlCredential;
    private LdapUserDetails ldapUser;
    private List<String> groups;

    public ContainerSpec getContainerSpec() {
        return containerSpec;
    }

    public ProxySpec getProxySpec() {
        return proxySpec;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public OpenIDAuthenticationBackend.CustomNameOidcUser getOidcUser() {
        return oicdUser;
    }

    public KeycloakPrincipal getKeycloakUser() {
        return keycloakUser;
    }

    public SAMLCredential getSamlCredential() {
        return samlCredential;
    }

    public LdapUserDetails getLdapUser() {
        return ldapUser;
    }

    public List<String> getGroups() {
        return groups;
    }

    public static SpecExpressionContext create(Object... objects) {
        SpecExpressionContext ctx = new SpecExpressionContext();
        for (Object o : objects) {
            if (o instanceof ContainerSpec) {
                ctx.containerSpec = (ContainerSpec) o;
            } else if (o instanceof ProxySpec) {
                ctx.proxySpec = (ProxySpec) o;
            } else if (o instanceof Proxy) {
                ctx.proxy = (Proxy) o;
            } else if (o instanceof OpenIDAuthenticationBackend.CustomNameOidcUser) {
                ctx.oicdUser = (OpenIDAuthenticationBackend.CustomNameOidcUser) o;
            } else if (o instanceof KeycloakPrincipal) {
                ctx.keycloakUser = (KeycloakPrincipal) o;
            } else if (o instanceof SAMLCredential) {
                ctx.samlCredential = (SAMLCredential) o;
            } else if (o instanceof LdapUserDetails) {
                ctx.ldapUser = (LdapUserDetails) o;
            }
            if (o instanceof Authentication) {
				ctx.groups = Arrays.asList(UserService.getGroups((Authentication) o));
            }
        }
        return ctx;
    }

}
