/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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
package eu.openanalytics.containerproxy.spec.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.spec.RuntimeSettingSpec;
import eu.openanalytics.containerproxy.spec.IProxySpecMergeStrategy;
import eu.openanalytics.containerproxy.spec.IRuntimeSettingType;
import eu.openanalytics.containerproxy.spec.ProxySpecException;

/**
 * This default merge strategy allows any combination of base spec, runtime spec and runtime settings.
 */
public class DefaultSpecMergeStrategy implements IProxySpecMergeStrategy {

	@Autowired(required=false)
	private Map<String, IRuntimeSettingType> typeMap = new HashMap<>();
	
	@Override
	public ProxySpec merge(ProxySpec baseSpec, ProxySpec runtimeSpec, Set<RuntimeSetting> runtimeSettings) throws ProxySpecException {
		if (baseSpec == null && runtimeSpec == null) throw new ProxySpecException("No base or runtime proxy spec provided");
		
		Map<RuntimeSetting, Object> resolvedSettings = resolveSettings(baseSpec, runtimeSettings);

		ProxySpec finalSpec = new ProxySpec();
		copySpec(baseSpec, finalSpec);
		copySpec(runtimeSpec, finalSpec);
		
		for (Entry<RuntimeSetting, Object> entry: resolvedSettings.entrySet()) {
			applySetting(finalSpec, entry.getKey(), entry.getValue());
		}
		
		if (finalSpec.getId() == null) finalSpec.setId(UUID.randomUUID().toString());
		return finalSpec;
	}
	
	protected Map<RuntimeSetting, Object> resolveSettings(ProxySpec baseSpec, Set<RuntimeSetting> settings) throws ProxySpecException {
		Map<RuntimeSetting, Object> resolvedSettings = new HashMap<>();
		if (settings == null) return resolvedSettings;

		for (RuntimeSetting setting: settings) {
			RuntimeSettingSpec spec = baseSpec.getRuntimeSettingSpecs().stream().filter(s -> s.getName().equals(setting.getName())).findAny().orElse(null);
			if (spec == null) {
				// No spec for this setting found: ignore it.
				continue;
			}
			
			IRuntimeSettingType type = typeMap.get(spec.getType());
			if (type == null) throw new ProxySpecException("Unknown setting type: " + spec.getType());
			
			Object resolvedValue = type.resolveValue(setting, spec.getConfig());
			resolvedSettings.put(setting, resolvedValue);
		}
		
		return resolvedSettings;
	}

	protected void applySetting(ProxySpec spec, RuntimeSetting setting, Object resolvedValue) {
		// Default: do nothing.
	}
	
	protected void copySpec(ProxySpec from, ProxySpec to) {
		if (from == null || to == null) return;
		from.copy(to);
	}
}
