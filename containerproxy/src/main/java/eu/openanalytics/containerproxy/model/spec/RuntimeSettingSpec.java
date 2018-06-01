package eu.openanalytics.containerproxy.model.spec;

import java.util.HashMap;
import java.util.Map;

public class RuntimeSettingSpec {

	private String name;
	private String type;
	private Map<String, Object> config;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Map<String, Object> getConfig() {
		return config;
	}
	public void setConfig(Map<String, Object> config) {
		this.config = config;
	}
	
	public void copy(RuntimeSettingSpec target) {
		target.setName(name);
		target.setType(type);
		if (config != null) {
			if (target.getConfig() == null) target.setConfig(new HashMap<>());
			target.getConfig().putAll(config);
		}
	}
}
