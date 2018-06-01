package eu.openanalytics.containerproxy.spec.impl.type;

import java.util.Map;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.spec.IRuntimeSettingType;
import eu.openanalytics.containerproxy.spec.ProxySpecException;

@Component("Int")
public class IntSettingType implements IRuntimeSettingType {

	@Override
	public Object resolveValue(RuntimeSetting setting, Map<String, Object> config) throws ProxySpecException {
		Object value = setting.getValue();
		if (value == null) return null;
		else if (value instanceof Number) return ((Number) setting.getValue()).intValue();
		else throw new ProxySpecException("Value type is not a Number: " + value);
	}

}