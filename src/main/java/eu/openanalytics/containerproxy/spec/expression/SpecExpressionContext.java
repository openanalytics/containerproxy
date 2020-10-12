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
