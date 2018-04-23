package eu.openanalytics.containerproxy.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import eu.openanalytics.containerproxy.ContainerProxyException;

public class PortAllocator {

	private int[] range;
	private Set<Integer> occupiedPorts;
	
	public PortAllocator(int from, int to) {
		range = new int[] { from, to };
		occupiedPorts = Collections.synchronizedSet(new HashSet<>());
	}
	
	public int allocate() {
		int nextPort = range[0];
		while (occupiedPorts.contains(nextPort)) nextPort++;
		
		if (range[1] > 0 && nextPort > range[1]) {
			throw new ContainerProxyException("Cannot create container: all allocated ports are currently in use."
					+ " Please try again later or contact an administrator.");
		}
		
		occupiedPorts.add(nextPort);
		return nextPort;
	}
	
	public void release(int port) {
		occupiedPorts.remove(port);
	}
}
