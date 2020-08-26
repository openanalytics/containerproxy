package eu.openanalytics.containerproxy.test.proxy;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class PropertyOverrideContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
				"proxy.kubernetes.namespace=" + TestIntegrationOnKube.session.getNamespace());
	}
}