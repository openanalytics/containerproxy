package eu.openanalytics.containerproxy.backend.strategy;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

/**
 * Defines a strategy for testing the responsiveness of a newly launched proxy.
 * If a proxy is not responsive, the launch will be aborted and an error will be generated.
 */
public interface IProxyTestStrategy {

	public boolean testProxy(Proxy proxy);

}
