package eu.openanalytics.containerproxy.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

@Configuration
public class FaviconConfig {

	private static final String CONTENT_TYPE_ICO = "image/x-icon";
	
	@Inject
    private Environment environment;
	
	@Bean
	@ConditionalOnProperty(name="proxy.favicon-path")
	public SimpleUrlHandlerMapping customFaviconHandlerMapping() {
		byte[] cachedIco = null;
		 
		Path icoPath = Paths.get(environment.getProperty("proxy.favicon-path"));
		if (Files.isDirectory(icoPath)) icoPath = Paths.get(icoPath.toString(), "favicon.ico");
		if (Files.isRegularFile(icoPath)) {
			try (InputStream input = Files.newInputStream(icoPath)) {
				cachedIco = FileCopyUtils.copyToByteArray(input);
			} catch (IOException e) {
				throw new IllegalArgumentException("Cannot read favicon: " + icoPath, e);
			} 
		}
		
		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
		mapping.setUrlMap(Collections.singletonMap("**/favicon.ico", new CachedFaviconHttpRequestHandler(cachedIco)));
		return mapping;
	}

	private static class CachedFaviconHttpRequestHandler implements HttpRequestHandler {
		
		private byte[] cachedIco;
		
		public CachedFaviconHttpRequestHandler(byte[] cachedIco) {
			this.cachedIco = cachedIco;
		}
		
		@Override
		public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			response.setHeader("Content-Type", CONTENT_TYPE_ICO);
			response.setHeader("Content-Length", String.valueOf(cachedIco.length));
			response.getOutputStream().write(cachedIco);
			response.getOutputStream().flush();
			response.setStatus(200);
		}
	}

}