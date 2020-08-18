package eu.openanalytics.containerproxy.service;

import java.util.Map;

import com.spotify.docker.client.messages.PortBinding;

public class ExistingContaienrInfo {
		
	public ExistingContaienrInfo(String containerId, String proxyId, String proxySpecId, String image, String userId, Map<Integer, Integer>  portBindings, long startupTimestamp, boolean running) {
		this.containerId = containerId;
		this.proxyId = proxyId;
		this.proxySpecId = proxySpecId;
		this.image = image;
		this.userId = userId;
		this.portBindings = portBindings;
		this.startupTimestamp = startupTimestamp;
		this.running = running;
	}
	
	private String containerId;
	private String proxyId;
	private String proxySpecId;
	private String image;
	private String userId;
	private Map<Integer, Integer> portBindings;
	private long startupTimestamp;
	private boolean running;
	
	public String getContainerId() {
		return containerId;
	}
	
	public String getProxyId() {
		return proxyId;
	}

	public String getProxySpecId() {
		return proxySpecId;
	}

	public String getImage() {
		return image;
	}

	public String getUserId() {
		return userId;
	}
	
	public Map<Integer, Integer> getPortBindings() {
		return portBindings;
	}
	
	public long getStartupTimestamp() {
		return startupTimestamp;
	}
	
	public boolean getRunning() {
		return running;
	}
	
	// TODO copy?
}