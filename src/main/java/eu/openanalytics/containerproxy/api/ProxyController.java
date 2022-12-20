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
import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ProxyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;
import java.util.List;


@RestController
public class ProxyController extends BaseController {

	@Inject
	private ProxyService proxyService;

	@Inject
	private ApiSecurityService apiSecurityService;

    @RequestMapping(value="/api/proxyspec", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MappingJacksonValue> listProxySpecs() {
	   List<ProxySpec> specs = proxyService.getProxySpecs(null, false);
	   return ApiResponse.success(apiSecurityService.protectSpecs(specs));
	}
	
	@RequestMapping(value="/api/proxyspec/{proxySpecId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MappingJacksonValue> getProxySpec(@PathVariable String proxySpecId) {
		ProxySpec spec = proxyService.findProxySpec(s -> s.getId().equals(proxySpecId), false);
		if (spec == null) {
			return ResponseEntity.status(403).body(new MappingJacksonValue(new ApiResponse<>("fail", "forbidden")));
		}
		return ApiResponse.success(apiSecurityService.protectSpecs(spec));
	}

	@JsonView(Views.UserApi.class)
	@RequestMapping(value="/api/proxy", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<List<Proxy>>> listProxies() {
		return ApiResponse.success(proxyService.getProxiesOfCurrentUser(null));
	}

	@JsonView(Views.UserApi.class)
	@RequestMapping(value="/api/proxy/{proxyId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<Proxy>> getProxy(@PathVariable String proxyId) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(proxyId), false);
		if (proxy == null) {
			return ApiResponse.failForbidden();
		}
		return ApiResponse.success(proxy);
	}
	
	@RequestMapping(value="/api/proxy/{proxySpecId}", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<Proxy>> startProxy(@PathVariable String proxySpecId) throws InvalidParametersException {
		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(proxySpecId), false);
		if (baseSpec == null) {
			return ApiResponse.failForbidden();
		}

		Proxy proxy = proxyService.startProxy(baseSpec);
		return ApiResponse.created(proxy);
	}

    // TODO disable this by default
//	@RequestMapping(value="/api/proxy", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
//	public ResponseEntity<Proxy> startProxy(@RequestBody ProxySpec proxySpec) {
//		Proxy proxy = proxyService.startProxy(proxySpec, false);
//		return new ResponseEntity<>(proxy, HttpStatus.CREATED);
//	}

}
