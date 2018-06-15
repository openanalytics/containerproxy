package eu.openanalytics.containerproxy.api;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;

@RestController
public class ProxyController extends BaseController {

	@Inject
	private ProxyService proxyService;
	
	@RequestMapping(value="/api/proxyspec", method=RequestMethod.GET)
	public List<ProxySpec> listProxySpecs() {
		return proxyService.getProxySpecs(null, false);
	}
	
	@RequestMapping(value="/api/proxyspec/{id}", method=RequestMethod.GET)
	public ResponseEntity<ProxySpec> getProxySpec(@PathVariable String id) {
		ProxySpec spec = proxyService.findProxySpec(s -> s.getId().equals(id), false);
		if (spec == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		return new ResponseEntity<>(spec, HttpStatus.OK);
	}
	
	@RequestMapping(value="/api/proxy", method=RequestMethod.GET)
	public List<Proxy> listProxies() {
		return proxyService.getProxies(null, false);
	}
	
	@RequestMapping(value="/api/proxy/{id}", method=RequestMethod.GET)
	public ResponseEntity<Proxy> getProxy(@PathVariable String id) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(id), false);
		if (proxy == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		return new ResponseEntity<>(proxy, HttpStatus.OK);
	}
	
	@RequestMapping(value="/api/proxy/{baseSpecId}", method=RequestMethod.POST)
	public ResponseEntity<Proxy> startProxy(@PathVariable String baseSpecId, @RequestBody Set<RuntimeSetting> runtimeSettings) {
		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(baseSpecId), false);
		if (baseSpec == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, runtimeSettings);
		Proxy proxy = proxyService.startProxy(spec);
		return new ResponseEntity<>(proxy, HttpStatus.OK);
	}
	
	@RequestMapping(value="/api/proxy", method=RequestMethod.POST)
	public ResponseEntity<Proxy> startProxy(@RequestBody ProxySpec runtimeSpec) {
		ProxySpec spec = proxyService.resolveProxySpec(null, runtimeSpec, null);
		Proxy proxy = proxyService.startProxy(spec);
		return new ResponseEntity<>(proxy, HttpStatus.OK);
	}
	
	@RequestMapping(value="/api/proxy/{id}", method=RequestMethod.DELETE)
	public ResponseEntity<String> stopProxy(@PathVariable String id) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(id), false);
		if (proxy == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		
		proxyService.stopProxy(proxy, true);
		return new ResponseEntity<>("Proxy stopped", HttpStatus.OK);
	}
}
