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
package eu.openanalytics.containerproxy.backend.kubernetes;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.json.JsonPatch;
import javax.json.JsonStructure;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.internal.SerializationUtils;

@Component
public class PodPatcher {

	private static final String DEBUG_PROPERTY = "proxy.kubernetes.debug-patches";

	@Inject
	private Environment environment;

	private final ObjectMapper mapper = new ObjectMapper();

	private boolean loggingEnabled = false;

	private final Logger log = LogManager.getLogger(getClass());

	@PostConstruct
	public void init() {
		mapper.registerModule(new JSR353Module());
		loggingEnabled = Boolean.valueOf(environment.getProperty(DEBUG_PROPERTY, "false"));
	}

	/**
	 * Applies a JsonPatch to the given Pod.
	 */
	public Pod patch(Pod pod, JsonPatch patch) {
		if (patch == null) {
			return pod;
		}
		// 1. convert Pod to javax.json.JsonValue object.
		// This conversion does not actually convert to a string, but some internal
		// representation of Jackson.
		JsonStructure podAsJsonValue = mapper.convertValue(pod, JsonStructure.class);
		// 2. apply patch
		JsonStructure patchedPodAsJsonValue = patch.apply(podAsJsonValue);
		// 3. convert back to a pod
		return mapper.convertValue(patchedPodAsJsonValue, Pod.class);
	}

	/**
	 * Applies a JsonPatch to the given Pod. When proxy.kubernetes.debug-patches is
	 * enabled the original and patched specification will be logged as YAML.
	 */
	public Pod patchWithDebug(Pod pod, JsonPatch patch) throws JsonProcessingException {
		if (loggingEnabled) {
			log.info("Original Pod: " + SerializationUtils.dumpAsYaml(pod));
		}
		Pod patchedPod = patch(pod, patch);
		if (loggingEnabled) {
			log.info("Patched Pod: " + SerializationUtils.dumpAsYaml(patchedPod));
		}
		return patchedPod;
	}

}
