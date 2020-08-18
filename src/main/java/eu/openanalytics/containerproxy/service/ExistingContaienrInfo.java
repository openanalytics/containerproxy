package eu.openanalytics.containerproxy.service;

public class ExistingContaienrInfo {
		
	public ExistingContaienrInfo(String containerId, String proxyId, String proxySpecId, String image, String userId) {
		this.containerId = containerId;
		this.proxyId = proxyId;
		this.proxySpecId = proxySpecId;
		this.image = image;
		this.userId = userId;
	}
	
	private String containerId;
	private String proxyId;
	private String proxySpecId;
	private String image;
	private String userId;
	
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
	
	// TODO copy?
}