package eu.openanalytics.containerproxy.api;

import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import eu.openanalytics.containerproxy.model.App;
import eu.openanalytics.containerproxy.model.Proxy;
import eu.openanalytics.containerproxy.service.ProxyService;

@RestController
@RequestMapping("/proxy")
public class ProxyController extends BaseController {

	@Inject
	private ProxyService proxyService;
	
	@RequestMapping(value="/list", method=RequestMethod.GET)
	public List<Proxy> list() {
		return proxyService.listProxies();
	}
	
	@RequestMapping(value="/start/{appName}", method=RequestMethod.POST)
	public Proxy startProxy(@PathVariable String appName, @RequestBody(required=false) App app, HttpServletRequest request) {
		String userName = getUserName(request);
		Proxy proxy = null;
		if (app == null) proxy = proxyService.startProxy(userName, appName);
		else proxy = proxyService.startProxy(userName, app);
		return proxy;
	}
	
	@RequestMapping(value="/stop/{appName}", method=RequestMethod.DELETE)
	public ResponseEntity<String> stopProxy(@PathVariable String appName, HttpServletRequest request) {
		proxyService.releaseProxy(getUserName(request), appName);
		return new ResponseEntity<>("Proxy stopped", HttpStatus.OK);
	}
}
