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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;


@RestController
public class ProxyController extends BaseController {

	@Inject
	private ProxyService proxyService;
	
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
	
	@RequestMapping(value="/api/proxy", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public List<Proxy> listProxies(@RequestParam(value = "only_owned_proxies", required = false, defaultValue = "false") boolean onlyOwnedProxies) {
		if (onlyOwnedProxies) {
			// even if the user is an admin this will only return proxies that the user owns
			return proxyService.getProxiesOfCurrentUser(null);
		}
		// if the user is an admin this will return all proxies
		return proxyService.getProxies(null, false);
	}
	
	@RequestMapping(value="/api/proxy/{proxyId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Proxy> getProxy(@PathVariable String proxyId) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(proxyId), false);
		if (proxy == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		return new ResponseEntity<>(proxy, HttpStatus.OK);
	}
	
	@RequestMapping(value="/api/proxy/{proxySpecId}", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Proxy> startProxy(@PathVariable String proxySpecId, @RequestBody(required=false) Set<RuntimeSetting> runtimeSettings) {
		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(proxySpecId), false);
		if (baseSpec == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, runtimeSettings);
		Proxy proxy = proxyService.startProxy(spec, false);
		return new ResponseEntity<>(proxy, HttpStatus.CREATED);
	}
	
	@RequestMapping(value="/api/proxy", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Proxy> startProxy(@RequestBody ProxySpec proxySpec) {
		ProxySpec spec = proxyService.resolveProxySpec(null, proxySpec, null);
		Proxy proxy = proxyService.startProxy(spec, false);
		return new ResponseEntity<>(proxy, HttpStatus.CREATED);
	}
	
	@RequestMapping(value="/api/proxy/{proxyId}", method=RequestMethod.DELETE, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, String>> stopProxy(@PathVariable String proxyId) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(proxyId), false);
		if (proxy == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		
		proxyService.stopProxy(proxy, true, false);
		return ResponseEntity.ok(new HashMap<String, String>() {{
			put("status", "success");
			put("message", "proxy_stopped");
		}});
	}
}
