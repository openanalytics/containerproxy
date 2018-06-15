package eu.openanalytics.containerproxy.api;

import javax.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import eu.openanalytics.containerproxy.service.HeartbeatService;

@RestController
public class HeartbeatController extends BaseController {

	@Inject
	private HeartbeatService heartbeatService;
	
	@RequestMapping(value="/api/heartbeat/{proxyId}", method=RequestMethod.POST)
	public ResponseEntity<String> heartbeat(@PathVariable String proxyId) {
		heartbeatService.heartbeatReceived(proxyId);
		return new ResponseEntity<>("Heartbeat registered", HttpStatus.OK);
	}
}
