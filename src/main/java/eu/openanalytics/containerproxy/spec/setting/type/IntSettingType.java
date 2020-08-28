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
package eu.openanalytics.containerproxy.spec.setting.type;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.RuntimeSettingSpec;
import eu.openanalytics.containerproxy.spec.ProxySpecException;

@Component("setting.type.int")
public class IntSettingType extends AbstractSettingType {

	@Override
	protected Object getValue(RuntimeSetting setting, RuntimeSettingSpec settingSpec) {
		if (setting.getValue() == null) return null;
		else if (setting.getValue() instanceof Number) return ((Number) setting.getValue()).intValue();
		else throw new ProxySpecException("Setting value is not an integer: " + setting.getName() + ": " + setting.getValue());
	}	

}