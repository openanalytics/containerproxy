/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import javax.inject.Inject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.app.VelocityEngine;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.metadata.provider.HTTPMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.util.resource.ResourceException;
import org.opensaml.xml.parse.StaticBasicParserPool;
import org.opensaml.xml.parse.XMLParserException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLBootstrap;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.security.saml.SAMLProcessingFilter;
import org.springframework.security.saml.context.SAMLContextProvider;
import org.springframework.security.saml.context.SAMLContextProviderImpl;
import org.springframework.security.saml.key.EmptyKeyManager;
import org.springframework.security.saml.key.JKSKeyManager;
import org.springframework.security.saml.key.KeyManager;
import org.springframework.security.saml.log.SAMLDefaultLogger;
import org.springframework.security.saml.metadata.*;
import org.springframework.security.saml.parser.ParserPoolHolder;
import org.springframework.security.saml.processor.HTTPPostBinding;
import org.springframework.security.saml.processor.HTTPRedirectDeflateBinding;
import org.springframework.security.saml.processor.SAMLBinding;
import org.springframework.security.saml.processor.SAMLProcessorImpl;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;
import org.springframework.security.saml.util.VelocityFactory;
import org.springframework.security.saml.websso.WebSSOProfile;
import org.springframework.security.saml.websso.WebSSOProfileConsumer;
import org.springframework.security.saml.websso.WebSSOProfileConsumerHoKImpl;
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl;
import org.springframework.security.saml.websso.WebSSOProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfileOptions;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@ConditionalOnProperty(name="proxy.authentication", havingValue="saml")
public class SAMLConfiguration {

	private static final String DEFAULT_NAME_ATTRIBUTE = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";
	
	@Inject
	private Environment environment;
	
	@Inject
	@Lazy
	private AuthenticationManager authenticationManager;
	
	@Bean
	public SAMLEntryPoint samlEntryPoint() {
		SAMLEntryPoint samlEntryPoint = new SAMLEntryPoint();
		samlEntryPoint.setDefaultProfileOptions(defaultWebSSOProfileOptions());
		return samlEntryPoint;
	}
	
	@Bean
	public WebSSOProfileOptions defaultWebSSOProfileOptions() {
		WebSSOProfileOptions webSSOProfileOptions = new WebSSOProfileOptions();
		webSSOProfileOptions.setIncludeScoping(false);
		webSSOProfileOptions.setForceAuthN(Boolean.valueOf(environment.getProperty("proxy.saml.force-authn", "false")));
		return webSSOProfileOptions;
	}

	@Bean
	public static SAMLBootstrap samlBootstrap() {
		return new SAMLBootstrap();
	}

	@Bean
	public WebSSOProfile webSSOprofile() {
		return new WebSSOProfileImpl();
	}

	@Bean
	public KeyManager keyManager() {
		String keystore = environment.getProperty("proxy.saml.keystore");
		if (keystore == null || keystore.isEmpty()) {
			return new EmptyKeyManager();
		} else {
			String certName = environment.getProperty("proxy.saml.encryption-cert-name");
			String certPW = environment.getProperty("proxy.saml.encryption-cert-password");
			String keystorePW = environment.getProperty("proxy.saml.keystore-password", certPW);
			
			Resource keystoreFile = new FileSystemResource(keystore);
			Map<String, String> passwords = new HashMap<>();
			passwords.put(certName, certPW);
			return new JKSKeyManager(keystoreFile, keystorePW, passwords, certName);
		}
	}

	@Bean
	public StaticBasicParserPool parserPool() {
		StaticBasicParserPool pool = new StaticBasicParserPool();
		try {
			pool.initialize();
		} catch (XMLParserException e) {
			e.printStackTrace();
		}
		return pool;
	}

	@Bean(name="parserPoolHolder")
	public ParserPoolHolder parserPoolHolder() {
		return new ParserPoolHolder();
	}

	@Bean
	public VelocityEngine velocityEngine() {
		return VelocityFactory.getEngine();
	}

	@Bean
	public HTTPPostBinding httpPostBinding() {
		return new HTTPPostBinding(parserPool(), velocityEngine());
	}

	@Bean
	public HTTPRedirectDeflateBinding httpRedirectDeflateBinding() {
		return new HTTPRedirectDeflateBinding(parserPool());
	}

	@Bean
	public SAMLProcessorImpl processor() {
		Collection<SAMLBinding> bindings = new ArrayList<>();
		bindings.add(httpRedirectDeflateBinding());
		bindings.add(httpPostBinding());
		return new SAMLProcessorImpl(bindings);
	}

	@Bean
	public MetadataGeneratorFilter metadataGeneratorFilter() {
		return new MetadataGeneratorFilter(metadataGenerator());
	}

	@Bean
	public MetadataDisplayFilter metadataDisplayFilter() throws MetadataProviderException, ResourceException {
		MetadataDisplayFilter metadataDisplayFilter = new MetadataDisplayFilter();
		metadataDisplayFilter.setContextProvider(contextProvider());
		metadataDisplayFilter.setKeyManager(keyManager());
		metadataDisplayFilter.setManager(metadata());
		return metadataDisplayFilter;
	}

	@Bean
	public MetadataGenerator metadataGenerator() {
		String appEntityId = environment.getProperty("proxy.saml.app-entity-id");
		String appBaseURL = environment.getProperty("proxy.saml.app-base-url");
		
		MetadataGenerator metadataGenerator = new MetadataGenerator();
		metadataGenerator.setEntityId(appEntityId);
		metadataGenerator.setEntityBaseURL(appBaseURL);
		metadataGenerator.setExtendedMetadata(extendedMetadata());
		metadataGenerator.setIncludeDiscoveryExtension(false);
		metadataGenerator.setRequestSigned(false);
		return metadataGenerator;
	}

	@Bean
	public ExtendedMetadata extendedMetadata() {
		ExtendedMetadata extendedMetadata = new ExtendedMetadata();
		extendedMetadata.setIdpDiscoveryEnabled(false);
		extendedMetadata.setSignMetadata(false);
		return extendedMetadata;
	}

	@Bean
	public ExtendedMetadataDelegate idpMetadata() throws MetadataProviderException, ResourceException {
		String metadataURL = environment.getProperty("proxy.saml.idp-metadata-url");
		
		Timer backgroundTaskTimer = new Timer(true);
		HTTPMetadataProvider httpMetadataProvider = new HTTPMetadataProvider(backgroundTaskTimer, new HttpClient(), metadataURL);   httpMetadataProvider.setParserPool(parserPool());
		ExtendedMetadataDelegate extendedMetadataDelegate = new ExtendedMetadataDelegate(httpMetadataProvider , extendedMetadata());
		extendedMetadataDelegate.setMetadataTrustCheck(false);
		extendedMetadataDelegate.setMetadataRequireSignature(false);
		return extendedMetadataDelegate;
	}

	@Bean
	@Qualifier("metadata")
	public CachingMetadataManager metadata() throws MetadataProviderException, ResourceException {
		List<MetadataProvider> providers = new ArrayList<>();
		providers.add(idpMetadata());
		return new CachingMetadataManager(providers);
	}
	
	@Bean
	public SAMLDefaultLogger samlLogger() {
		return new SAMLDefaultLogger();
	}

	@Bean
	public SAMLContextProviderImpl contextProvider() {
		return new SAMLContextProviderImpl();
	}

	@Bean
	public SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler() {
		SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler = new SavedRequestAwareAuthenticationSuccessHandler();
		successRedirectHandler.setDefaultTargetUrl("/");
		return successRedirectHandler;
	}

	@Bean
	public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
		SimpleUrlAuthenticationFailureHandler failureHandler = new SimpleUrlAuthenticationFailureHandler();
		failureHandler.setUseForward(true);
		failureHandler.setDefaultFailureUrl("/error");
		return failureHandler;
	}

	@Bean
	public SAMLProcessingFilter samlWebSSOProcessingFilter() throws Exception {
		SAMLProcessingFilter samlWebSSOProcessingFilter = new SAMLProcessingFilter();
		samlWebSSOProcessingFilter.setAuthenticationManager(authenticationManager);
		samlWebSSOProcessingFilter.setAuthenticationSuccessHandler(successRedirectHandler());
		samlWebSSOProcessingFilter.setAuthenticationFailureHandler(authenticationFailureHandler());
		return samlWebSSOProcessingFilter;
	}

	@Bean
	public WebSSOProfileConsumer webSSOprofileConsumer() {
		return new WebSSOProfileConsumerImpl();
	}

	@Bean
	public WebSSOProfileConsumerHoKImpl hokWebSSOprofileConsumer() {
		return new WebSSOProfileConsumerHoKImpl();
	}

	@Bean
	public SAMLFilterSet samlFilter() throws Exception {
		List<SecurityFilterChain> chains = new ArrayList<SecurityFilterChain>();
		chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/login/**"), samlEntryPoint()));
		chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/SSO/**"), samlWebSSOProcessingFilter()));
		return new SAMLFilterSet(chains);
	}

	private final Logger log = LogManager.getLogger(getClass());

	@Bean
	public SAMLAuthenticationProvider samlAuthenticationProvider() {
		SAMLAuthenticationProvider samlAuthenticationProvider = new SAMLAuthenticationProvider();
	    samlAuthenticationProvider.setUserDetails(new SAMLUserDetailsService() {
	    	@Override
	    	public Object loadUserBySAML(SAMLCredential credential) throws UsernameNotFoundException {
				List<Attribute> attributes = credential.getAttributes();

				String userID = credential.getNameID().getValue();
				attributes.forEach((attribute) -> {
					log.info(String.format("[SAML] User: %s => found Attribute with name : %s (%s) and value %s - %s",
							userID,
							attribute.getName(),
							attribute.getFriendlyName(),
							credential.getAttributeAsString(attribute.getName()),
							String.join(", ", credential.getAttributeAsStringArray(attribute.getName()))));
				});

				String nameAttribute = environment.getProperty("proxy.saml.name-attribute", DEFAULT_NAME_ATTRIBUTE);
				String nameValue = credential.getAttributeAsString(nameAttribute);
				if (nameValue == null) throw new UsernameNotFoundException("Name attribute missing from SAML assertion: " + nameAttribute);

				List<GrantedAuthority> auth = new ArrayList<>();
	    		String rolesAttribute = environment.getProperty("proxy.saml.roles-attribute");
	    		if (rolesAttribute != null  && !rolesAttribute.trim().isEmpty()) {
	    			String[] roles = credential.getAttributeAsStringArray(rolesAttribute);
	    			if (roles != null && roles.length > 0) {
	    				Arrays.stream(roles)
	    					.map(r -> "ROLE_" + r.toUpperCase())
	    					.forEach(a -> auth.add(new SimpleGrantedAuthority(a)));
	    			}
	    		}
	    		
	    		return new User(nameValue, "", auth);
	    	}
	    });
	    samlAuthenticationProvider.setForcePrincipalAsString(false);
	    return samlAuthenticationProvider;
	}
	
	// This subclass adds no functionality, but is used so Spring can inject it into SAMLAuthenticationBackend
	public static class SAMLFilterSet extends FilterChainProxy {
		public SAMLFilterSet(List<SecurityFilterChain> chain) {
			super(chain);
		}
	}
}
