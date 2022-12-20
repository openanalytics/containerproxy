/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.api;

import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.AsyncProxyService;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
public class ProxyController extends BaseController {

	@Inject
	private ProxyService proxyService;

	@Inject
	private AsyncProxyService asyncProxyService;
	
	@RequestMapping(value="/api/proxyspec", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public List<ProxySpec> listProxySpecs() {
		return proxyService.getProxySpecs(null, false);
	}
	
	@RequestMapping(value="/api/proxyspec/{proxySpecId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ProxySpec> getProxySpec(@PathVariable String proxySpecId) {
		ProxySpec spec = proxyService.findProxySpec(s -> s.getId().equals(proxySpecId), false);
		if (spec == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		return new ResponseEntity<>(spec, HttpStatus.OK);
	}

	@JsonView(Views.UserApi.class)
	@RequestMapping(value="/api/proxy", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public List<Proxy> listProxies(@RequestParam(value = "only_owned_proxies", required = false, defaultValue = "false") boolean onlyOwnedProxies) {
		if (onlyOwnedProxies) {
			// even if the user is an admin this will only return proxies that the user owns
			return proxyService.getProxiesOfCurrentUser(null);
		}
		// if the user is an admin this will return all proxies
		return proxyService.getProxies(null, false);
	}

	@JsonView(Views.UserApi.class)
	@RequestMapping(value="/api/proxy/{proxyId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Proxy> getProxy(@PathVariable String proxyId) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(proxyId), false);
		if (proxy == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		return new ResponseEntity<>(proxy, HttpStatus.OK);
	}
	
	@RequestMapping(value="/api/proxy/{proxySpecId}", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Proxy> startProxy(@PathVariable String proxySpecId) throws InvalidParametersException {
		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(proxySpecId), false);
		if (baseSpec == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		
		Proxy proxy = proxyService.startProxy(baseSpec);
		return new ResponseEntity<>(proxy, HttpStatus.CREATED);
	}

    // TODO disable this by default
//	@RequestMapping(value="/api/proxy", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
//	public ResponseEntity<Proxy> startProxy(@RequestBody ProxySpec proxySpec) {
//		Proxy proxy = proxyService.startProxy(proxySpec, false);
//		return new ResponseEntity<>(proxy, HttpStatus.CREATED);
//	}
	
	@RequestMapping(value="/api/proxy/{proxyId}", method=RequestMethod.DELETE, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, String>> stopProxy(@PathVariable String proxyId) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(proxyId), false);
		if (proxy == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		
		asyncProxyService.stopProxy(proxy, false);
		return ResponseEntity.ok(new HashMap<String, String>() {{
			put("status", "success");
			put("message", "proxy_stopped");
		}});
	}

}
