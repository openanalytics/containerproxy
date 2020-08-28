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
package eu.openanalytics.containerproxy.spec.setting;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.spec.RuntimeSettingSpec;
import eu.openanalytics.containerproxy.spec.ProxySpecException;

/**
 * A registry of known runtime setting types.
 * <p>
 * This class can apply runtime settings onto ProxySpecs.
 * <ol>
 * <li>First, it will try to resolve a setting type using the RuntimeSettingSpec of the setting.</li>
 * <li>If a matching type was found, an attempt will be made to apply the value of the setting onto the target ProxySpec,
 * using the rules of the setting type.</li>
 * </p>
 */
@Component
public class SettingTypeRegistry {

	@Autowired(required=false)
	private Map<String, IRuntimeSettingType> typeMap = new HashMap<>();
	
	public RuntimeSettingSpec resolveSpec(RuntimeSetting setting, ProxySpec proxySpec) {
		return proxySpec.getRuntimeSettingSpecs().stream().filter(s -> s.getName().equals(setting.getName())).findAny().orElse(null);
	}
	
	public IRuntimeSettingType resolveSpecType(RuntimeSettingSpec settingSpec) {
		String type = settingSpec.getType();
		if (type == null || type.isEmpty()) {
			//TODO try to determine the type via the spec config
			type = "setting.type.string";
		}
		return typeMap.get(type);
	}
	
	public void applySetting(RuntimeSetting setting, ProxySpec targetSpec) throws ProxySpecException {
		RuntimeSettingSpec settingSpec = resolveSpec(setting, targetSpec);
		if (settingSpec == null) return;
		
		IRuntimeSettingType type = resolveSpecType(settingSpec);
		if (type == null) throw new ProxySpecException("Unknown setting type: " + settingSpec.getType());
		
		type.apply(setting, settingSpec, targetSpec);
	}
}
