package eu.openanalytics.containerproxy.model.spec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ContainerSpec {

	private String image;
	private String[] cmd;
	private Map<String, String> env;
	private String network;
	private String[] networkConnections;
	private String[] dns;
	private String[] volumes;
	private Map<String, Integer> portMapping;
	private String memory;
	private boolean privileged;
	
	public String getImage() {
		return image;
	}
	public void setImage(String image) {
		this.image = image;
	}
	public String[] getCmd() {
		return cmd;
	}
	public void setCmd(String[] cmd) {
		this.cmd = cmd;
	}
	public Map<String, String> getEnv() {
		return env;
	}
	public void setEnv(Map<String, String> env) {
		this.env = env;
	}
	public String getNetwork() {
		return network;
	}
	public void setNetwork(String network) {
		this.network = network;
	}
	public String[] getNetworkConnections() {
		return networkConnections;
	}
	public void setNetworkConnections(String[] networkConnections) {
		this.networkConnections = networkConnections;
	}
	public String[] getDns() {
		return dns;
	}
	public void setDns(String[] dns) {
		this.dns = dns;
	}
	public String[] getVolumes() {
		return volumes;
	}
	public void setVolumes(String[] volumes) {
		this.volumes = volumes;
	}
	public Map<String, Integer> getPortMapping() {
		return portMapping;
	}
	public void setPortMapping(Map<String, Integer> portMapping) {
		this.portMapping = portMapping;
	}
	public String getMemory() {
		return memory;
	}
	public void setMemory(String memory) {
		this.memory = memory;
	}
	public boolean isPrivileged() {
		return privileged;
	}
	public void setPrivileged(boolean privileged) {
		this.privileged = privileged;
	}
	
	public void copy(ContainerSpec target) {
		target.setImage(image);
		if (cmd != null) target.setCmd(Arrays.copyOf(cmd, cmd.length));
		if (env != null) {
			if (target.getEnv() == null) target.setEnv(new HashMap<>());
			target.getEnv().putAll(env);
		}
		target.setNetwork(network);
		if (networkConnections != null) target.setNetworkConnections(Arrays.copyOf(networkConnections, networkConnections.length));
		if (dns != null) target.setDns(Arrays.copyOf(dns, dns.length));
		if (volumes != null) target.setVolumes(Arrays.copyOf(volumes, volumes.length));
		if (portMapping != null) {
			if (target.getPortMapping() == null) target.setPortMapping(new HashMap<>());
			target.getPortMapping().putAll(portMapping);
		}
		target.setMemory(memory);
		target.setPrivileged(privileged);
	}
}
