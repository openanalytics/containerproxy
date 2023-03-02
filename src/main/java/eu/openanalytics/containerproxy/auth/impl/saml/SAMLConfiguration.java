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

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.impl.AuthnRequestMarshaller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.authentication.OpenSamlAuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationTokenConverter;
import org.springframework.security.saml2.provider.service.web.authentication.logout.OpenSaml3LogoutRequestResolver;
import org.springframework.security.saml2.provider.service.web.authentication.logout.Saml2LogoutRequestResolver;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

@Configuration
@ConditionalOnProperty(name = "proxy.authentication", havingValue = "saml")
public class SAMLConfiguration {

    public static final String DEFAULT_NAME_ATTRIBUTE = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

    public static final String PROP_LOG_ATTRIBUTES = "proxy.saml.log-attributes";
    public static final String PROP_FORCE_AUTHN = "proxy.saml.force-authn";
    public static final String PROP_NAME_ATTRIBUTE = "proxy.saml.name-attribute";
    public static final String PROP_ROLES_ATTRIBUTE = "proxy.saml.roles-attribute";
    public static final String PROP_KEYSTORE = "proxy.saml.keystore";
    public static final String PROP_ENCRYPTION_CERT_NAME = "proxy.saml.encryption-cert-name";
    public static final String PROP_ENCRYPTION_CERT_PASSWORD = "proxy.saml.encryption-cert-password";
    public static final String PROP_ENCRYPTION_KEYSTORE_PASSWORD = "proxy.saml.keystore-password";
    public static final String PROP_APP_ENTITY_ID = "proxy.saml.app-entity-id";
    public static final String PROP_BASE_URL = "proxy.saml.app-base-url";
    public static final String PROP_METADATA_URL = "proxy.saml.idp-metadata-url";
    public static final String PROP_SUCCESS_LOGOUT_URL = "proxy.saml.logout-url";
    public static final String PROP_SAML_LOGOUT_METHOD = "proxy.saml.logout-method";

    public static final String REG_ID = "shinyproxy";

    public static final String SAML_SERVICE_LOCATION_PATH = "/saml/SSO";
    public static final String SAML_LOGOUT_SERVICE_LOCATION_PATH = "/saml/logout";
    public static final String SAML_LOGOUT_SERVICE_RESPONSE_LOCATION_PATH = "/saml/SingleLogout";
    public static final String SAML_METADATA_PATH = "/saml/metadata";

    @Inject
    private Environment environment;

    @PostConstruct
    public void init() {
        // configure ForceAuthn setting
        boolean forceAuthn = environment.getProperty(PROP_FORCE_AUTHN, Boolean.class, false);
        if (forceAuthn) {
            OpenSamlInitializationService.requireInitialize(factory -> {
                AuthnRequestMarshaller marshaller = new AuthnRequestMarshaller() {

                    @Nonnull
                    @Override
                    public Element marshall(XMLObject object, Element element) throws MarshallingException {
                        configureAuthnRequest((AuthnRequest) object);
                        return super.marshall(object, element);
                    }

                    @Nonnull
                    @Override
                    public Element marshall(XMLObject object, Document document) throws MarshallingException {
                        configureAuthnRequest((AuthnRequest) object);
                        return super.marshall(object, document);
                    }

                    private void configureAuthnRequest(AuthnRequest authnRequest) {
                        authnRequest.setForceAuthn(true);
                    }
                };

                factory.getMarshallerFactory().registerMarshaller(AuthnRequest.DEFAULT_ELEMENT_NAME, marshaller);
            });
        }
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistration() throws IOException, GeneralSecurityException {
        String baseUrl = environment.getProperty(PROP_BASE_URL);
        String metadataUrl = environment.getProperty(PROP_METADATA_URL);
        String entityId = environment.getProperty(PROP_APP_ENTITY_ID);

        if (baseUrl == null) {
            throw new IllegalArgumentException("[SAML] Configuration error, missing property " + PROP_BASE_URL);
        }

        if (metadataUrl == null) {
            throw new IllegalArgumentException("[SAML] Configuration error, missing property " + PROP_METADATA_URL);
        }

        if (entityId == null) {
            throw new IllegalArgumentException("[SAML] Configuration error, missing property " + PROP_APP_ENTITY_ID);
        }

        RelyingPartyRegistration.Builder registration = RelyingPartyRegistrations
                .fromMetadataLocation(metadataUrl)
                .assertionConsumerServiceLocation(ServletUriComponentsBuilder
                        .fromHttpUrl(baseUrl).path(SAML_SERVICE_LOCATION_PATH).toUriString())
                .registrationId(REG_ID)
                .entityId(entityId)
                .singleLogoutServiceBinding(Saml2MessageBinding.POST)
                .singleLogoutServiceLocation(ServletUriComponentsBuilder
                        .fromHttpUrl(baseUrl).path(SAML_LOGOUT_SERVICE_LOCATION_PATH).toUriString())
                .singleLogoutServiceResponseLocation(ServletUriComponentsBuilder
                        .fromHttpUrl(baseUrl).path(SAML_LOGOUT_SERVICE_RESPONSE_LOCATION_PATH).toUriString());

        Saml2X509Credential signingCredential = getSingingCredential();
        if (signingCredential != null) {
            registration.signingX509Credentials(c -> c.add(signingCredential));
        }

        return new InMemoryRelyingPartyRegistrationRepository(registration.build());
    }

    /**
     * Saml2AuthenticationTokenConverter that always returns the ShinyProxy registration.
     * This is required because the registrationId is not part of the URL.
     */
    @Bean
    public Saml2AuthenticationTokenConverter saml2AuthenticationTokenConverter(RelyingPartyRegistrationRepository relyingPartyRegistrationRepository) {
        return new Saml2AuthenticationTokenConverter(
                (request, relyingPartyRegistrationId) -> relyingPartyRegistrationRepository.findByRegistrationId(REG_ID)
        );
    }

    /**
     * Saml2LogoutRequestResolver that always returns the ShinyProxy registration.
     * This is required because the registrationId is not part of the URL.
     */
    @SuppressWarnings("deprecation")
    @Bean
    public Saml2LogoutRequestResolver saml2LogoutRequestResolver(RelyingPartyRegistrationRepository relyingPartyRegistrationRepository) {
        return new OpenSaml3LogoutRequestResolver(
                (request, relyingPartyRegistrationId) -> relyingPartyRegistrationRepository.findByRegistrationId(REG_ID)
        );
    }

    @SuppressWarnings("deprecation")
    @Bean
    public OpenSamlAuthenticationProvider openSamlAuthenticationProvider() {
        OpenSamlAuthenticationProvider authenticationProvider = new OpenSamlAuthenticationProvider();
        authenticationProvider.setResponseAuthenticationConverter(new ResponseAuthenticationConverter(environment));
        return authenticationProvider;
    }

    private Saml2X509Credential getSingingCredential() throws GeneralSecurityException, IOException {
        String certName = environment.getProperty(PROP_ENCRYPTION_CERT_NAME);
        String certPW = environment.getProperty(PROP_ENCRYPTION_CERT_PASSWORD);
        String keyStorePath = environment.getProperty(PROP_KEYSTORE);

        if (certName == null || certPW == null || keyStorePath == null) {
            return null;
        }

        String keystorePW = environment.getProperty(PROP_ENCRYPTION_KEYSTORE_PASSWORD, certPW);

        KeyStore ks = KeyStore.getInstance("pkcs12");
        ks.load(Files.newInputStream(Paths.get(keyStorePath)), keystorePW.toCharArray());

        Key key = ks.getKey(certName, certPW.toCharArray());
        Certificate certificate = ks.getCertificate(certName);

        return Saml2X509Credential.signing((PrivateKey) key, (X509Certificate) certificate);
    }

}
