package eu.openanalytics.containerproxy.api;

import java.util.List;

import javax.inject.Inject;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import eu.openanalytics.containerproxy.model.App;
import eu.openanalytics.containerproxy.service.AppService;

@RestController
@RequestMapping("/app")
public class AppController extends BaseController {

	@Inject
	private AppService appService;
	
	@RequestMapping(value="/list", method=RequestMethod.GET)
	public List<App> list() {
		return appService.getApps();
	}

	@RequestMapping(value="/details/{name}", method=RequestMethod.GET)
	public App details(@PathVariable String name) {
		return appService.getApp(name);
	}
}
