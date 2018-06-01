package eu.openanalytics.containerproxy.model.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;

public class Proxy {

	private String id;
	
	private ProxySpec spec;

	private ProxyStatus status;

	private long startupTimestamp;
	private String userId;
	
	private List<Container> containers;
	private Map<String,URI> targets;
	
	public Proxy() {
		containers = new ArrayList<>();
		targets = new HashMap<>();
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ProxySpec getSpec() {
		return spec;
	}

	public void setSpec(ProxySpec spec) {
		this.spec = spec;
	}

	public ProxyStatus getStatus() {
		return status;
	}

	public void setStatus(ProxyStatus status) {
		this.status = status;
	}

	public long getStartupTimestamp() {
		return startupTimestamp;
	}

	public void setStartupTimestamp(long startupTimestamp) {
		this.startupTimestamp = startupTimestamp;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public List<Container> getContainers() {
		return containers;
	}
	
	public void setContainers(List<Container> containers) {
		this.containers = containers;
	}
	
	public Map<String, URI> getTargets() {
		return targets;
	}
	
	public void setTargets(Map<String, URI> targets) {
		this.targets = targets;
	}
}
