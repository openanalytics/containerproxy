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
package eu.openanalytics.containerproxy.auth.impl.saml;

import eu.openanalytics.containerproxy.auth.UserLogoutHandler;
import eu.openanalytics.containerproxy.auth.impl.SAMLAuthenticationBackend;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.app.VelocityEngine;
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
import org.springframework.security.saml.*;
import org.springframework.security.saml.context.SAMLContextProviderImpl;
import org.springframework.security.saml.context.SAMLContextProviderLB;
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
import org.springframework.security.saml.websso.*;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.inject.Inject;
import java.util.*;

@Configuration
@ConditionalOnProperty(name="proxy.authentication", havingValue="saml")
public class SAMLConfiguration {

	private static final String DEFAULT_NAME_ATTRIBUTE = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

	private static final String PROP_LOG_ATTRIBUTES = "proxy.saml.log-attributes";
	private static final String PROP_FORCE_AUTHN = "proxy.saml.force-authn";
	private static final String PROP_MAX_AUTHENTICATION_AGE = "proxy.saml.max-authentication-age";
	private static final String PROP_KEYSTORE = "proxy.saml.keystore";
	private static final String PROP_ENCRYPTION_CERT_NAME = "proxy.saml.encryption-cert-name";
	private static final String PROP_ENCRYPTION_CERT_PASSWORD = "proxy.saml.encryption-cert-password";
	private static final String PROP_ENCRYPTION_KEYSTORE_PASSWORD = "proxy.saml.keystore-password";
	private static final String PROP_APP_ENTITY_ID = "proxy.saml.app-entity-id";
	private static final String PROP_BASE_URL = "proxy.saml.app-base-url";
	private static final String PROP_METADATA_URL = "proxy.saml.idp-metadata-url";
	private static final String PROP_LB_SERVER_NAME = "proxy.saml.lb-server-name";
	private static final String PROP_LB_CONTEXT_PATH = "proxy.saml.lb-context-path";
	private static final String PROP_LB_PORT_IN_URL = "proxy.saml.lb-port-in-url";
	private static final String PROP_LB_SCHEME = "proxy.saml.lb-scheme";
	private static final String PROP_LB_SERVER_PORT = "proxy.saml.lb-server-port";

	@Inject
	private Environment environment;
	
	@Inject
	@Lazy
	private AuthenticationManager authenticationManager;

	@Inject
	private UserLogoutHandler userLogoutHandler;

	@Bean
	public SAMLEntryPoint samlEntryPoint() {
		SAMLEntryPoint samlEntryPoint = new SAMLEntryPoint();
		samlEntryPoint.setDefaultProfileOptions(defaultWebSSOProfileOptions());
		return samlEntryPoint;
	}

	@Bean
	public SingleLogoutProfile logoutProfile() {
		return new SingleLogoutProfileImpl();
	}

	@Bean
	public SAMLLogoutFilter samlLogoutFilter() {
		return new SAMLLogoutFilter(successLogoutHandler(),
				new LogoutHandler[]{userLogoutHandler, securityContextLogoutHandler()},
				new LogoutHandler[]{userLogoutHandler, securityContextLogoutHandler()});
	}

	/**
	 * Filter responsible for the `/saml/SingleLogout` endpoint. This makes it possible for users to logout in the IDP
	 * or any other application and get automatically logged out in ShinyProxy as well.
	 */
	@Bean
	public SAMLLogoutProcessingFilter samlLogoutProcessingFilter() {
		return new SAMLLogoutProcessingFilter(successLogoutHandler(),
				securityContextLogoutHandler());
	}

	@Bean
	public SecurityContextLogoutHandler securityContextLogoutHandler() {
		SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
		logoutHandler.setInvalidateHttpSession(true);
		logoutHandler.setClearAuthentication(true);
		return logoutHandler;
	}

	@Bean
	public SimpleUrlLogoutSuccessHandler successLogoutHandler() {
		SimpleUrlLogoutSuccessHandler successLogoutHandler = new SimpleUrlLogoutSuccessHandler();
		successLogoutHandler.setDefaultTargetUrl(SAMLAuthenticationBackend.determineLogoutSuccessURL(environment));
		return successLogoutHandler;
	}
	
	@Bean
	public WebSSOProfileOptions defaultWebSSOProfileOptions() {
		WebSSOProfileOptions webSSOProfileOptions = new WebSSOProfileOptions();
		webSSOProfileOptions.setIncludeScoping(false);
		webSSOProfileOptions.setForceAuthN(Boolean.valueOf(environment.getProperty(PROP_FORCE_AUTHN, "false")));
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
		String keystore = environment.getProperty(PROP_KEYSTORE);
		if (keystore == null || keystore.isEmpty()) {
			return new EmptyKeyManager();
		} else {
			String certName = environment.getProperty(PROP_ENCRYPTION_CERT_NAME);
			String certPW = environment.getProperty(PROP_ENCRYPTION_CERT_PASSWORD);
			String keystorePW = environment.getProperty(PROP_ENCRYPTION_KEYSTORE_PASSWORD, certPW);
			
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
		String appEntityId = environment.getProperty(PROP_APP_ENTITY_ID);
		String appBaseURL = environment.getProperty(PROP_BASE_URL);
		
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
	public ExtendedMetadataDelegate idpMetadata() throws MetadataProviderException {
		String metadataURL = environment.getProperty(PROP_METADATA_URL);
		
		Timer backgroundTaskTimer = new Timer(true);
		HTTPMetadataProvider httpMetadataProvider = new HTTPMetadataProvider(backgroundTaskTimer, getHttpClient(), metadataURL);
		httpMetadataProvider.setParserPool(parserPool());
		ExtendedMetadataDelegate extendedMetadataDelegate = new ExtendedMetadataDelegate(httpMetadataProvider , extendedMetadata());
		extendedMetadataDelegate.setMetadataTrustCheck(false);
		extendedMetadataDelegate.setMetadataRequireSignature(false);
		return extendedMetadataDelegate;
	}

	private HttpClient getHttpClient() {
		HttpClient httpClient = new HttpClient();
		String proxyHost = System.getProperty("http.proxyHost");
		if (proxyHost != null) {
			HostConfiguration hostConfiguration = new HostConfiguration();
			hostConfiguration.setProxy(proxyHost, Integer.parseInt(System.getProperty("http.proxyPort","80")));
			httpClient.setHostConfiguration(hostConfiguration);
		}
		return httpClient;
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
		String serverName = environment.getProperty(PROP_LB_SERVER_NAME);
		
		if (serverName != null && !serverName.isEmpty()) {
			SAMLContextProviderLB lbProvider = new SAMLContextProviderLB();

			lbProvider.setServerName(serverName);
			lbProvider.setContextPath(environment.getProperty(PROP_LB_CONTEXT_PATH, "/"));
			lbProvider.setIncludeServerPortInRequestURL(environment.getProperty(PROP_LB_PORT_IN_URL, Boolean.class, false));
			lbProvider.setScheme(environment.getProperty(PROP_LB_SCHEME, "https"));
			lbProvider.setServerPort(environment.getProperty(PROP_LB_SERVER_PORT, Integer.class, 443));

			return lbProvider;
		}
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
		AuthenticationFailureHandler failureHandler = new AuthenticationFailureHandler();
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
		samlWebSSOProcessingFilter.setSessionAuthenticationStrategy(new ChangeSessionIdAuthenticationStrategy());
		return samlWebSSOProcessingFilter;
	}

	@Bean
    public AlreadyLoggedInFilter alreadyLoggedInFilter() {
		return new AlreadyLoggedInFilter();
	}

	@Bean
	public WebSSOProfileConsumer webSSOprofileConsumer() {
		WebSSOProfileConsumerImpl res = new WebSSOProfileConsumerImpl();
		Integer maxAuthenticationAge = environment.getProperty(PROP_MAX_AUTHENTICATION_AGE, Integer.class);
		if (maxAuthenticationAge != null) {
			res.setMaxAuthenticationAge(maxAuthenticationAge);
		}
		return res;
	}

	@Bean
	public WebSSOProfileConsumerHoKImpl hokWebSSOprofileConsumer() {
		return new WebSSOProfileConsumerHoKImpl();
	}

	@Bean
	public SAMLFilterSet samlFilter() throws Exception {
		List<SecurityFilterChain> chains = new ArrayList<SecurityFilterChain>();
		chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/login/**"), samlEntryPoint()));
		chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/logout/**"), samlLogoutFilter()));
		chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/SingleLogout/**"), samlLogoutProcessingFilter()));
		chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/SSO/**"), alreadyLoggedInFilter(), samlWebSSOProcessingFilter()));
		return new SAMLFilterSet(chains);
	}

	private final Logger log = LogManager.getLogger(getClass());



	@Bean
	public SAMLAuthenticationProvider samlAuthenticationProvider() {
		SAMLAuthenticationProvider samlAuthenticationProvider = new SAMLAuthenticationProvider();
	    samlAuthenticationProvider.setUserDetails(new SAMLUserDetailsService() {
	    	@Override
	    	public Object loadUserBySAML(SAMLCredential credential) throws UsernameNotFoundException {

				if (Boolean.parseBoolean(environment.getProperty(PROP_LOG_ATTRIBUTES, "false"))) {
                    AttributeUtils.logAttributes(log, credential);
				}

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
