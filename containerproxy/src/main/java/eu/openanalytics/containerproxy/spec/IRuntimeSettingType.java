package eu.openanalytics.containerproxy.spec;

import java.util.Map;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;

public interface IRuntimeSettingType {

	public Object resolveValue(RuntimeSetting setting, Map<String, Object> config) throws ProxySpecException;

}

/*

Limited merging: define a ProxySpec, plus a set of RuntimeSettings in the yml:

runtimeSettings:
- name: cpuCount
  type: IntRange
  config:
	min: 1
	max: 5

  Supported types: String, StringPattern, StringEnum, Int, IntRange, IntEnum, Float, FloatRange, FloatEnum
  
  Then, in ProxySpec/ContainerSpec/cpu : ${cpuCount}
  This is resolved:
  1. against Spring properties
  2. against RuntimeSettings

 */