/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.model.spec;

import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProxySpec {

	private String id;
	private String displayName;
	private String description;
	private String logoURL;

	private AccessControl accessControl;
	private List<ContainerSpec> containerSpecs;

    private Parameters parameters;

	private SpelField.Long maxLifeTime = new SpelField.Long();

	private Boolean stopOnLogout;
	private SpelField.Long heartbeatTimeout = new SpelField.Long();

	private final Map<Class<? extends ISpecExtension>, ISpecExtension> specExtensions = new HashMap<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLogoURL() {
		return logoURL;
	}

	public void setLogoURL(String logoURL) {
		this.logoURL = logoURL;
	}

	public AccessControl getAccessControl() {
		return accessControl;
	}

	public void setAccessControl(AccessControl accessControl) {
		this.accessControl = accessControl;
	}

	public List<ContainerSpec> getContainerSpecs() {
		return containerSpecs;
	}

	public ContainerSpec getContainerSpec(String image) {
		if (image == null || image.isEmpty()) return null;
		// we compare with the original value here, when AppRecovery tries to recover the spec, we don't interpret spel
		return containerSpecs.stream().filter(s -> {
			if (image.endsWith(":latest") && !s.getImage().getOriginalValue().contains(":")) {
				// if we query for the latest image and the spec does not contain a tag -> then add :latest to the
                // image name of the spec.
				// e.g. querying for "debian:latest" while "debian" is specified in the spec
				return image.equals(s.getImage() + ":latest");
			} else {
				return image.equals(s.getImage().getOriginalValue());
			}
		}).findAny().orElse(null);
	}

	public void setContainerSpecs(List<ContainerSpec> containerSpecs) {
		this.containerSpecs = containerSpecs;
		for (int i = 0; i < this.containerSpecs.size(); i++) {
			this.containerSpecs.get(i).setIndex(i);
		}
	}

	public SpelField.Long getMaxLifeTime() {
		return maxLifeTime;
	}

	public void setMaxLifeTime(SpelField.Long maxLifeTime) {
		this.maxLifeTime = maxLifeTime;
	}

	public Boolean stopOnLogout() {
		return stopOnLogout;
	}

	public void setStopOnLogout(Boolean stopOnLogout) {
		this.stopOnLogout = stopOnLogout;
	}

	public SpelField.Long getHeartbeatTimeout() {
		return heartbeatTimeout;
	}

	public void setHeartbeatTimeout(SpelField.Long heartbeatTimeout) {
		this.heartbeatTimeout = heartbeatTimeout;
	}

    public Parameters getParameters() {
        return parameters;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

	public void addSpecExtension(ISpecExtension specExtension) {
		specExtensions.put(specExtension.getClass(), specExtension);
	}

	public <T> T getSpecExtension(Class<T> extensionClass) {
		return extensionClass.cast(specExtensions.get(extensionClass));
	}

	public void resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
		heartbeatTimeout.resolve(resolver, context);
		maxLifeTime.resolve(resolver, context);
		for (ISpecExtension iSpecExtension : specExtensions.values()) {
			iSpecExtension.resolve(resolver, context);
		}
		for (ContainerSpec containerSpec : containerSpecs) {
			containerSpec.resolve(resolver, context.copy(containerSpec));
		}
	}

}
