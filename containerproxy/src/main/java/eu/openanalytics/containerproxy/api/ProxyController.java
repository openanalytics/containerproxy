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
import eu.openanalytics.containerproxy.service.ProxySpecService;

@RestController
public class ProxyController extends BaseController {

	@Inject
	private ProxySpecService proxySpecService;
	
	@Inject
	private ProxyService proxyService;
	
	@RequestMapping(value="/api/proxyspec", method=RequestMethod.GET)
	public Set<ProxySpec> listProxySpecs() {
		return proxySpecService.getSpecs();
	}
	
	@RequestMapping(value="/api/proxyspec/{id}", method=RequestMethod.GET)
	public ProxySpec getProxySpec(@PathVariable String id) {
		ProxySpec spec = proxySpecService.getSpec(id);
		if (spec == null) throw new NotFoundException("No spec found with id " + id);
		return spec;
	}
	
	@RequestMapping(value="/api/proxy", method=RequestMethod.GET)
	public List<Proxy> listProxies() {
		return proxyService.listActiveProxies();
	}
	
	@RequestMapping(value="/api/proxy/{id}", method=RequestMethod.GET)
	public Proxy getProxy(@PathVariable String id) {
		return proxyService.getProxy(id);
	}
	
	@RequestMapping(value="/api/proxy/{baseSpecId}", method=RequestMethod.POST)
	public Proxy startProxy(@PathVariable String baseSpecId, @RequestBody Set<RuntimeSetting> runtimeSettings) {
		ProxySpec spec = proxySpecService.resolveSpec(baseSpecId, null, runtimeSettings);
		return proxyService.startProxy(spec);
	}
	
	@RequestMapping(value="/api/proxy", method=RequestMethod.POST)
	public Proxy startProxy(@RequestBody ProxySpec runtimeSpec) {
		ProxySpec spec = proxySpecService.resolveSpec(null, runtimeSpec, null);
		return proxyService.startProxy(spec);
	}
	
	@RequestMapping(value="/api/proxy/{id}", method=RequestMethod.DELETE)
	public ResponseEntity<String> stopProxy(@PathVariable String id) {
		proxyService.stopProxy(id);
		return new ResponseEntity<>("Proxy stopped", HttpStatus.OK);
	}
}
