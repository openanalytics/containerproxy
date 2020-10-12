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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.spec.RuntimeSettingSpec;
import eu.openanalytics.containerproxy.spec.ProxySpecException;

/**
 * Maps the name of a RuntimeSettingSpec onto a field of the target ProxySpec.
 * E.g.
 * <ul>
 * <li>container.memory -> ProxySpec.getContainerSpecs.get(0).setMemory()</li>
 * <li>container[1].memory -> ProxySpec.getContainerSpecs.get(1).setMemory()</li>
 * </ul>
 * TODO Support more value types, such as maps.
 */
@Component
public class SettingSpecMapper {

	private static final String PREFIX_CONTAINER = "container";
	private static final String PATTERN_CONTAINER_INDEXED = PREFIX_CONTAINER + "\\[(\\d+)\\]";
	
	public void mapValue(Object value, RuntimeSettingSpec spec, ProxySpec target) {
		String[] nameParts = spec.getName().split("\\.");
		if (nameParts.length == 0) doFail(spec, "cannot determing mapping for name");
		
		Object targetObject = target;
		String fieldName = nameParts[0];
		
		if (nameParts[0].equals(PREFIX_CONTAINER)) {
			if (target.getContainerSpecs().isEmpty()) doFail(spec, "proxy spec has no container specs");
			targetObject = target.getContainerSpecs().get(0);
			if (nameParts.length < 2) doFail(spec, "no container field specified");
			fieldName = nameParts[1];
		} else if (Pattern.matches(PATTERN_CONTAINER_INDEXED, nameParts[0])) {
			Matcher matcher = Pattern.compile(PATTERN_CONTAINER_INDEXED).matcher(nameParts[0]);
			int index = Integer.valueOf(matcher.group(1));
			if (index >= target.getContainerSpecs().size()) doFail(spec, "container index too high");
			targetObject = target.getContainerSpecs().get(index);
			if (nameParts.length < 2) doFail(spec, "no container field specified");
			fieldName = nameParts[1];
		}

		BeanWrapper wrapper = new BeanWrapperImpl(targetObject);
		wrapper.setPropertyValue(fieldName, value);
	}
	
	private void doFail(RuntimeSettingSpec spec, String msg) {
		throw new ProxySpecException("Cannot map setting " + spec.getName() + ":" + msg);
	}
}
