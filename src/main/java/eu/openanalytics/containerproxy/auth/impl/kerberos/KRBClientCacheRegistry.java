/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.auth.impl.kerberos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class KRBClientCacheRegistry {

	private Map<String, String> registry;
	
	private String baseClientCachePath;
	
	public KRBClientCacheRegistry(String baseClientCachePath) {
		this.baseClientCachePath = baseClientCachePath;
		this.registry = new HashMap<>();
	}
	
	public String getBaseClientCachePath() {
		return baseClientCachePath;
	}
	
	public synchronized String create(String principal) throws IOException {
		String escapedPName = principal.replace('@', '.');
		Files.createDirectories(Paths.get(baseClientCachePath, escapedPName));
		String path = Paths.get(baseClientCachePath, escapedPName, "ccache").toString();
		registry.put(principal, path);
		return path;
	}
	
	public synchronized String get(String principal) {
		return registry.get(principal);
	}
	
	public Set<String> getAllPrincipals() {
		return new HashSet<>(registry.keySet());
	}
	
	public synchronized void remove(String principal) throws IOException {
		if (!registry.containsKey(principal)) return;
		
		String path = registry.remove(principal);
		if (path == null || path.isEmpty()) return;
		
		// Delete both the ccache file and its user-principal parent folder
		Path p = Paths.get(path);
		Files.deleteIfExists(p);
		Files.deleteIfExists(p.getParent());
	}
}
