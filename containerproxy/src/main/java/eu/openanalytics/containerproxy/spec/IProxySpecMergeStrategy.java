package eu.openanalytics.containerproxy.spec;

import java.util.Set;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;

/**
 * This interface defines the strategy that should be used to obtain a final, ready-to-use ProxySpec
 * from a specific input.
 * <p>
 * The input can be divided into three components:
 * <ul>
 * <li>The base spec, which is retrieved from the application configuration file based on its unique id.</li>
 * <li>A runtime spec, which can be provided by the caller to the API when requesting the launch of a Proxy.</li>
 * <li>A set of runtime settings, which can be provided by the caller to the API when requesting the launch of a Proxy.</li>
 * </ul>
 * </p><p>
 * The strategy may choose to allow or prohibit certain combinations of input. For example:
 * <ul>
 * <li>The strategy may allow only a base spec, with no runtime spec or settings to merge.</li>
 * <li>The strategy may allow only a runtime spec, and no base specs are defined anywhere.</li>
 * <li>The strategy may allow a base spec, with an optional runtime spec OR an optional set of runtime settings.</li>
 * </ul>
 * </p>
 */
public interface IProxySpecMergeStrategy {

	/**
	 * Create a final ProxySpec from a set of input (see class description for more info).
	 * 
	 * @param baseSpec The base spec, or null if the strategy allows it.
	 * @param runtimeSpec The runtime spec, or null if the strategy allows it.
	 * @param runtimeSettings The runtime settings, or null if the strategy allows it.
	 * @return A ProxySpec that is fully configured and can be used to instantiate a new Proxy.
	 * @throws ProxySpecException If the spec cannot be created for any reason.
	 */
	public ProxySpec merge(ProxySpec baseSpec, ProxySpec runtimeSpec, Set<RuntimeSetting> runtimeSettings) throws ProxySpecException;
	
}
