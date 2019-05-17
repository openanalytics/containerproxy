package eu.openanalytics.containerproxy.auth.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.security.saml.metadata.MetadataGeneratorFilter;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.SAMLFilterSet;

@Component
public class SAMLAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "saml";
	
	@Autowired(required = false)
	private SAMLEntryPoint samlEntryPoint;
	
	@Autowired(required = false)
	private MetadataGeneratorFilter metadataGeneratorFilter;
	
	@Autowired(required = false)
	private SAMLFilterSet samlFilter;
	
	@Autowired(required = false)
	private SAMLAuthenticationProvider samlAuthenticationProvider;
	
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public boolean hasAuthorization() {
		return true;
	}

	@Override
	public void configureHttpSecurity(HttpSecurity http) throws Exception {
		http
			.exceptionHandling().authenticationEntryPoint(samlEntryPoint)
		.and()
			.addFilterBefore(metadataGeneratorFilter, ChannelProcessingFilter.class)
			.addFilterAfter(samlFilter, BasicAuthenticationFilter.class);
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(samlAuthenticationProvider);
	}

	@Override
	public String getLogoutSuccessURL() {
		return "/";
	}
}
