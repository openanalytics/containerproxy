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
package eu.openanalytics.containerproxy.auth;

import javax.inject.Inject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.auth.impl.KeycloakAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.LDAPAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.NoAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.SAMLAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.SimpleAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.SocialAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.WebServiceAuthenticationBackend;

/**
 * Instantiates an appropriate authentication backend depending on the application configuration.
 */
@Service(value="authenticationBackend")
@Primary
public class AuthenticationBackendFactory extends AbstractFactoryBean<IAuthenticationBackend> {

	@Inject
	private Environment environment;
	
	@Inject
	private ApplicationContext applicationContext;
	
	// These backends register some beans of their own, so must be instantiated here.
	
	@Inject
	private KeycloakAuthenticationBackend keycloakBackend;
	
	@Autowired(required = false)
	private SAMLAuthenticationBackend samlBackend;
	
	@Override
	public Class<?> getObjectType() {
		return IAuthenticationBackend.class;
	}

	@Override
	protected IAuthenticationBackend createInstance() throws Exception {
		IAuthenticationBackend backend = null;

		String type = environment.getProperty("proxy.authentication", "none");
		switch (type) {
		case NoAuthenticationBackend.NAME:
			backend = new NoAuthenticationBackend();
			break;
		case SimpleAuthenticationBackend.NAME:
			backend = new SimpleAuthenticationBackend();
			break;
		case LDAPAuthenticationBackend.NAME:
			backend = new LDAPAuthenticationBackend();
			break;
		case OpenIDAuthenticationBackend.NAME:
			backend = new OpenIDAuthenticationBackend();
			break;
		case SocialAuthenticationBackend.NAME:
			backend = new SocialAuthenticationBackend();
			break;
		case KeycloakAuthenticationBackend.NAME:
			return keycloakBackend;			
		case WebServiceAuthenticationBackend.NAME:
			backend = new WebServiceAuthenticationBackend();
			break;
		case SAMLAuthenticationBackend.NAME:
			return samlBackend;
		}
		if (backend == null) throw new RuntimeException("Unknown authentication type:" + type);
		
		applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
		return backend;
	}

}
