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
package eu.openanalytics.containerproxy.auth.impl;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.saml.AuthenticationFailureHandler;
import eu.openanalytics.containerproxy.auth.impl.saml.Saml2MetadataFilter;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.OpenSamlAuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.servlet.filter.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.saml2.provider.service.web.authentication.logout.Saml2LogoutRequestResolver;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.PROP_SAML_LOGOUT_METHOD;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.PROP_SUCCESS_LOGOUT_URL;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.REG_ID;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.SAML_LOGOUT_SERVICE_LOCATION_PATH;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.SAML_LOGOUT_SERVICE_RESPONSE_LOCATION_PATH;
import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.SAML_SERVICE_LOCATION_PATH;
import static eu.openanalytics.containerproxy.ui.AuthController.AUTH_SUCCESS_URL;

@Component
@ConditionalOnProperty(name = "proxy.authentication", havingValue = "saml")
public class SAMLAuthenticationBackend implements IAuthenticationBackend {

    public static final String NAME = "saml";

    @Inject
    private Environment environment;

    @Inject
    @SuppressWarnings("deprecation")
    private OpenSamlAuthenticationProvider samlAuthenticationProvider;

    @Autowired
    private RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @Inject
    private Saml2LogoutRequestResolver saml2LogoutRequestResolver;

    @Inject
    @Lazy
    private SavedRequestAwareAuthenticationSuccessHandler successHandler;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasAuthorization() {
        return true;
    }

    @Override
    public void configureHttpSecurity(HttpSecurity http, AuthorizedUrl anyRequestConfigurer) throws Exception {
        Saml2MetadataFilter metadataFilter = new Saml2MetadataFilter(relyingPartyRegistrationRepository.findByRegistrationId(REG_ID), new OpenSamlMetadataResolver());

        AuthenticationFailureHandler failureHandler = new AuthenticationFailureHandler();

        http
                .saml2Login(saml -> saml
                        .loginPage("/login")
                        .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
                        .loginProcessingUrl(SAML_SERVICE_LOCATION_PATH)
                        .authenticationManager(new ProviderManager(samlAuthenticationProvider))
                        .failureHandler(failureHandler)
                        .successHandler(successHandler))
                .saml2Logout(saml -> saml
                        .logoutUrl(SAML_LOGOUT_SERVICE_LOCATION_PATH)
                        .logoutResponse(r -> r.logoutUrl(SAML_LOGOUT_SERVICE_RESPONSE_LOCATION_PATH))
                        .logoutRequest(r -> r.logoutRequestResolver(saml2LogoutRequestResolver))
                        .addObjectPostProcessor(
                                new ObjectPostProcessor<LogoutFilter>() {
                                    @Override
                                    public <O extends LogoutFilter> O postProcess(O object) {
                                        // override method from POST to GET
                                        RequestMatcher logout = new AntPathRequestMatcher(SAML_LOGOUT_SERVICE_LOCATION_PATH, "GET");
                                        RequestMatcher samlRequestMatcher = new Saml2RequestMatcher();
                                        object.setLogoutRequestMatcher(new AndRequestMatcher(logout, samlRequestMatcher));
                                        return object;
                                    }
                                }
                        ))
                .addFilterBefore(metadataFilter, Saml2WebSsoAuthenticationFilter.class);
    }

    private static class Saml2RequestMatcher implements RequestMatcher {

        @Override
        public boolean matches(HttpServletRequest request) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return false;
            }
            return authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal;
        }

    }

    @Override
    public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
    }

    @Override
    public String getLogoutURL() {
        LogoutMethod logoutMethod = environment.getProperty(PROP_SAML_LOGOUT_METHOD, LogoutMethod.class, LogoutMethod.LOCAL);
        if (logoutMethod == LogoutMethod.LOCAL) {
            return "/logout";
        }
        return SAML_LOGOUT_SERVICE_LOCATION_PATH; // LogoutMethod.SAML
    }

    @Override
    public String getLogoutSuccessURL() {
        return determineLogoutSuccessURL(environment);
    }

    public static String determineLogoutSuccessURL(Environment environment) {
        String logoutURL = environment.getProperty(PROP_SUCCESS_LOGOUT_URL);
        if (logoutURL == null || logoutURL.trim().isEmpty()) {
            logoutURL = "/";
        }
        return logoutURL;
    }

    public String getLoginRedirectURI() {
        return ContextPathHelper.withoutEndingSlash()
                + "/saml2/authenticate/"
                + REG_ID;
    }

    private enum LogoutMethod {
        LOCAL,
        SAML
    }

}
