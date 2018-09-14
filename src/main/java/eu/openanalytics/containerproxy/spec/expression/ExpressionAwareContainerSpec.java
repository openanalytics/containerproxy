package eu.openanalytics.containerproxy.spec.expression;

import java.util.HashMap;
import java.util.Map;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;

/**
 * Adds expression support to ContainerSpecs.
 * <p>
 * This means that the settings of a ContainerSpec may contain SpEL expressions, such as:
 * <pre>container-volumes: [ "/tmp/#{proxy.userId}/personalfolder:/var/personalfolder" ]</pre>
 * </p>
 * For more information on the expression language, see:
 * https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#expressions
 */
public class ExpressionAwareContainerSpec extends ContainerSpec {

	private ContainerSpec source;
	private SpecExpressionResolver resolver;
	private SpecExpressionContext context;
	
	public ExpressionAwareContainerSpec(ContainerSpec source, Proxy proxy, SpecExpressionResolver resolver) {
		this.source = source;
		this.resolver = resolver;
		this.context = SpecExpressionContext.create(source, proxy, proxy.getSpec());
	}
	
	public String getImage() {
		return resolve(source.getImage());
	}
	public String[] getCmd() {
		return resolve(source.getCmd());
	}
	public Map<String, String> getEnv() {
		if (source.getEnv() == null) return null;
		Map<String, String> env = new HashMap<>();
		source.getEnv().entrySet().stream().forEach(e -> env.put(e.getKey(), resolve(e.getValue())));
		return env;
	}
	public String getEnvFile() {
		return resolve(source.getEnvFile());
	}
	public String getNetwork() {
		return resolve(source.getNetwork());
	}
	public String[] getNetworkConnections() {
		return resolve(source.getNetworkConnections());
	}
	public String[] getDns() {
		return resolve(source.getDns());
	}
	public String[] getVolumes() {
		return resolve(source.getVolumes());
	}
	public Map<String, Integer> getPortMapping() {
		return source.getPortMapping();
	}
	public String getMemory() {
		return resolve(source.getMemory());
	}
	public boolean isPrivileged() {
		return source.isPrivileged();
	}
	public Map<String, String> getSettings() {
		if (source.getSettings() == null) return null;
		Map<String, String> settings = new HashMap<>();
		source.getSettings().entrySet().stream().forEach(e -> settings.put(e.getKey(), resolve(e.getValue())));
		return settings;
	}
	
	protected String resolve(String expression) {
		if (expression == null) return null;
		return resolver.evaluateToString(expression, context);
	}
	
	protected String[] resolve(String[] expression) {
		if (expression == null) return null;
		String[] unresolved = (String[]) expression;
		String[] resolved = new String[unresolved.length];
		for (int i = 0; i < unresolved.length; i++) {
			resolved[i] = resolver.evaluateToString(unresolved[i], context);
		}
		return resolved;
	}
}
