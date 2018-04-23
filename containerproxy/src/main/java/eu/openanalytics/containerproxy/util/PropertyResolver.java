package eu.openanalytics.containerproxy.util;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

//TODO prefix according to application name (shinyproxy, rconsoleproxy etc)
@Service
public class PropertyResolver {

	@Inject
	Environment environment;
	
	public String get(String key, String defaultValue) {
		return environment.getProperty(key, defaultValue);
	}
	
	public int getInt(String key, int defaultValue) {
		return Integer.parseInt(environment.getProperty(key, String.valueOf(defaultValue)));
	}
}
