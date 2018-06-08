package eu.openanalytics.containerproxy.stat;

import java.io.IOException;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.service.EventService;
import eu.openanalytics.containerproxy.service.EventService.Event;
import eu.openanalytics.containerproxy.stat.impl.InfluxDBCollector;
import eu.openanalytics.containerproxy.stat.impl.MonetDBCollector;

@Service
public class StatCollectorRegistry implements Consumer<Event> {
	
	private Logger log = LogManager.getLogger(StatCollectorRegistry.class);
	
	@Inject
	Environment environment;
	
	@Inject
	EventService eventService;
	
	private IStatCollector collector;
	
	@PostConstruct
	public void init() {
		String baseURL = environment.getProperty("proxy.usage-stats-url");
		collector = findCollector(baseURL);
		if (collector == null) {
			log.info("Disabled. Usage statistics will not be processed.");
		} else {
			eventService.addListener(this);
			log.info(String.format("Enabled. Sending usage statistics to %s", baseURL));
		}
	}
	
	@Override
	public void accept(Event event) {
		if (collector != null) {
			try {
				collector.accept(event, environment);
			} catch (IOException e) {
				log.error("Failed to submit usage statistic event", e);
			}
		}
	}
	
	private IStatCollector findCollector(String baseURL) {
		if (baseURL == null || baseURL.isEmpty()) return null;
		if (baseURL.toLowerCase().contains("/write?db=")) {
			return new InfluxDBCollector();
		} else if (baseURL.toLowerCase().startsWith("jdbc:monetdb")) {
			return new MonetDBCollector();
		}
		return null;
	}
	
}
