package eu.openanalytics.containerproxy;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
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
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.PathMatcher;

@SpringBootApplication
@ComponentScan("eu.openanalytics")
public class ContainerProxyApplication {

	private static final String CONFIG_FILENAME = "application.yml";
	private static final String CONFIG_DEMO_PROFILE = "demo";
	
	@Inject
	private PropertyResolver props;

	@Inject
	private ProxyMappingManager mappingManager;
	
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
		UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
		factory.addDeploymentInfoCustomizers(info -> info.addInitialHandlerChainWrapper(defaultHandler -> {
				PathHandler pathHandler = new ProtectedPathHandler(defaultHandler);
				mappingManager.setPathHandler(pathHandler);
				return pathHandler;
		}));
		factory.setPort(props.getInt("port", 8080));
		return factory;	
	}
	
	private class ProtectedPathHandler extends PathHandler {
		
		public ProtectedPathHandler(HttpHandler defaultHandler) {
			super(defaultHandler);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void handleRequest(HttpServerExchange exchange) throws Exception {
			Field field = PathHandler.class.getDeclaredField("pathMatcher");
			field.setAccessible(true);
			PathMatcher<HttpHandler> pathMatcher = (PathMatcher<HttpHandler>) field.get(this);
			PathMatcher.PathMatch<HttpHandler> match = pathMatcher.match(exchange.getRelativePath());

			if (match.getValue() instanceof ProxyHandler && !mappingManager.requestHasAccess(exchange)) {
				exchange.setStatusCode(403);
				exchange.getResponseChannel().write(ByteBuffer.wrap("Not authorized to access this proxy".getBytes()));
			} else {
				super.handleRequest(exchange);
			}
		}
	}
}
