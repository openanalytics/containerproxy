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
package eu.openanalytics.containerproxy.spec.impl;

import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecMergeStrategy;
import eu.openanalytics.containerproxy.spec.ProxySpecException;
import eu.openanalytics.containerproxy.spec.setting.SettingTypeRegistry;

/**
 * This default merge strategy allows any combination of base spec, runtime spec and runtime settings.
 */
@Component
public class DefaultSpecMergeStrategy implements IProxySpecMergeStrategy {

	@Inject
	private SettingTypeRegistry settingTypeRegistry;
	
	@Override
	public ProxySpec merge(ProxySpec baseSpec, ProxySpec runtimeSpec, Set<RuntimeSetting> runtimeSettings) throws ProxySpecException {
		if (baseSpec == null && runtimeSpec == null) throw new ProxySpecException("No base or runtime proxy spec provided");
		
		ProxySpec finalSpec = new ProxySpec();
		copySpec(baseSpec, finalSpec);
		copySpec(runtimeSpec, finalSpec);
		
		if (runtimeSettings != null) {
			for (RuntimeSetting setting: runtimeSettings) {
				settingTypeRegistry.applySetting(setting, finalSpec);
			}
		}
		
		if (finalSpec.getId() == null) finalSpec.setId(UUID.randomUUID().toString());
		return finalSpec;
	}
	
	protected void copySpec(ProxySpec from, ProxySpec to) {
		if (from == null || to == null) return;
		from.copy(to);
	}
}
