package eu.openanalytics.containerproxy.spec.impl.type;

import java.util.Map;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.spec.IRuntimeSettingType;
import eu.openanalytics.containerproxy.spec.ProxySpecException;

@Component("String")
public class StringSettingType implements IRuntimeSettingType {

	@Override
	public Object resolveValue(RuntimeSetting setting, Map<String, Object> config) throws ProxySpecException {
		return String.valueOf(setting.getValue());
	}

}
