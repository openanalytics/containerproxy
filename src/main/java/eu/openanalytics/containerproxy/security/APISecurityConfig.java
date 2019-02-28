package eu.openanalytics.containerproxy.security;

import javax.inject.Inject;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;

@Configuration
@ConditionalOnProperty(name="proxy.oauth2.resource-id")
@EnableResourceServer
public class APISecurityConfig extends ResourceServerConfigurerAdapter {

	@Inject
	private Environment environment;
	
	@Override
	public void configure(HttpSecurity http) throws Exception {
		http.antMatcher("/api/**").authorizeRequests().anyRequest().authenticated().and().httpBasic();
	}
	
	@Override
	public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
		resources.resourceId(environment.getProperty("proxy.oauth2.resource-id"));
	}
	
	@Bean
	@ConditionalOnMissingBean(TokenStore.class)
	public TokenStore jwkTokenStore() {
		return new JwkTokenStore(environment.getProperty("proxy.oauth2.jwks-url"));
	}
	
	@Bean
	@ConditionalOnMissingBean(ResourceServerTokenServices.class)
	public DefaultTokenServices jwkTokenServices(TokenStore jwkTokenStore) {
		DefaultTokenServices services = new DefaultTokenServices();
		services.setTokenStore(jwkTokenStore);
		return services;
	}
}
