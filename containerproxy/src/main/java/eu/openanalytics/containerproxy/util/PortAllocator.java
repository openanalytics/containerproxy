package eu.openanalytics.containerproxy.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import eu.openanalytics.containerproxy.ContainerProxyException;

public class PortAllocator {

	private int[] range;
	private Set<Integer> occupiedPorts;
	private Map<Integer, String> occupiedPortOwners;
	
	public PortAllocator(int from, int to) {
		range = new int[] { from, to };
		occupiedPorts = Collections.synchronizedSet(new HashSet<>());
		occupiedPortOwners = Collections.synchronizedMap(new HashMap<>());
	}
	
	public int allocate(String ownerId) {
		int nextPort = range[0];
		while (occupiedPorts.contains(nextPort)) nextPort++;
		
		if (range[1] > 0 && nextPort > range[1]) {
			throw new ContainerProxyException("Cannot create container: all allocated ports are currently in use."
					+ " Please try again later or contact an administrator.");
		}
		
		occupiedPorts.add(nextPort);
		occupiedPortOwners.put(nextPort, ownerId);
		return nextPort;
	}
	
	public void release(int port) {
		occupiedPorts.remove(port);
		occupiedPortOwners.remove(port);
	}
	
	public void release(String ownerId) {
		synchronized (occupiedPortOwners) {
			Set<Integer> portsToRelease = occupiedPortOwners.entrySet().stream()
					.filter(e -> e.getValue().equals(ownerId))
					.map(e -> e.getKey())
					.collect(Collectors.toSet());
			for (Integer port: portsToRelease) {
				occupiedPorts.remove(port);
				occupiedPortOwners.remove(port);
			}
		}
	}
}
