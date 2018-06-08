package eu.openanalytics.containerproxy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import eu.openanalytics.containerproxy.util.PropertyResolver;
import io.undertow.Handlers;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;

@SpringBootApplication
@ComponentScan("eu.openanalytics")
public class ContainerProxyApplication {

	private static final String CONFIG_FILENAME = "application.yml";
	private static final String CONFIG_DEMO_PROFILE = "demo";
	
	@Inject
	PropertyResolver props;
	
	private PathHandler pathHandler;
	
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ContainerProxyApplication.class);

		boolean hasExternalConfig = Files.exists(Paths.get(CONFIG_FILENAME));
		if (!hasExternalConfig) app.setAdditionalProfiles(CONFIG_DEMO_PROFILE);

		try {
			app.run(args);
		} catch (Exception e) {
			// Workaround for bug in UndertowEmbeddedServletContainer.start():
			// If undertow.start() fails, started remains false which prevents undertow.stop() from ever being called.
			// Undertow's (non-daemon) XNIO worker threads will then prevent the JVM from exiting.
			if (e instanceof PortInUseException) System.exit(-1);
		}
	}
	
	@Bean
	public UndertowServletWebServerFactory servletContainer() {
		//TODO Secure the proxy path (it bypasses spring security filters)
		UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
		factory.addDeploymentInfoCustomizers(info -> info.addInitialHandlerChainWrapper(defaultHandler -> {
				pathHandler = Handlers.path(defaultHandler);
				return pathHandler;
		}));
		factory.setPort(props.getInt("port", 8080));
		return factory;	
	}

	@Bean
	public MappingManager mappingManager() {
		return new MappingManager() {
			@SuppressWarnings("deprecation")
			@Override
			public synchronized void addMapping(String path, URI target) {
				if (pathHandler == null) throw new IllegalStateException("Cannot change mappings: web server is not yet running.");
				LoadBalancingProxyClient proxyClient = new LoadBalancingProxyClient();
				proxyClient.addHost(target);
				pathHandler.addPrefixPath(path, new ProxyHandler(proxyClient, ResponseCodeHandler.HANDLE_404));
			}
			@Override
			public synchronized void removeMapping(String path) {
				if (pathHandler == null) throw new IllegalStateException("Cannot change mappings: web server is not yet running.");
				pathHandler.removePrefixPath(path);
			}
		};
	}
	
	public static interface MappingManager {
		public void addMapping(String path, URI target);
		public void removeMapping(String path);
	}
	
}
