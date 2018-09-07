package eu.openanalytics.containerproxy.spec.expression;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;

public class SpecExpressionContext {

	private ContainerSpec containerSpec;
	private ProxySpec proxySpec;
	private Proxy proxy;
	
	public ContainerSpec getContainerSpec() {
		return containerSpec;
	}

	public ProxySpec getProxySpec() {
		return proxySpec;
	}

	public Proxy getProxy() {
		return proxy;
	}
	
	public static SpecExpressionContext create(Object...objects) {
		SpecExpressionContext ctx = new SpecExpressionContext();
		for (Object o: objects) {
			if (o instanceof ContainerSpec) {
				ctx.containerSpec = (ContainerSpec) o;
			} else if (o instanceof ProxySpec) {
				ctx.proxySpec = (ProxySpec) o;
			} else if (o instanceof Proxy) {
				ctx.proxy = (Proxy) o;
			}
		}
		return ctx;
	}
}
