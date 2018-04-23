package eu.openanalytics.containerproxy.backend.ext;

import eu.openanalytics.containerproxy.model.Proxy;

public interface ITargetTestStrategy {

	public boolean testProxy(Proxy proxy);

}
