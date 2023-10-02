/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ContainerSpec {

    /**
     * Index in the array of ContainerSpecs of the ProxySpec.
     */
    private Integer index;
    private SpelField.String image;
    @Builder.Default
    private SpelField.StringList cmd = new SpelField.StringList();
    @Builder.Default
    private SpelField.StringMap env = new SpelField.StringMap();
    @Builder.Default
    private SpelField.String envFile = new SpelField.String();
    @Builder.Default
    private SpelField.String network = new SpelField.String();
    @Builder.Default
    private SpelField.StringList networkConnections = new SpelField.StringList();
    @Builder.Default
    private SpelField.StringList dns = new SpelField.StringList();
    @Builder.Default
    private SpelField.StringList volumes = new SpelField.StringList(new ArrayList<>());
    @Builder.Default
    private List<PortMapping> portMapping = new ArrayList<>();
    private boolean privileged;
    @Builder.Default
    private SpelField.String memoryRequest = new SpelField.String();
    @Builder.Default
    private SpelField.String memoryLimit = new SpelField.String();
    @Builder.Default
    private SpelField.String cpuRequest = new SpelField.String();
    @Builder.Default
    private SpelField.String cpuLimit = new SpelField.String();
    @Builder.Default
    private SpelField.StringMap labels = new SpelField.StringMap();
    @Builder.Default
    private List<DockerSwarmSecret> dockerSwarmSecrets = new ArrayList<>();
    private String dockerRegistryDomain;
    private String dockerRegistryUsername;
    private String dockerRegistryPassword;

	public void setCmd(List<String> cmd) {
		this.cmd = new SpelField.StringList(cmd);
	}

	public void setEnv(Map<String, String> env) {
		this.env = new SpelField.StringMap(env);
	}

	public void setNetworkConnections(List<String> networkConnections) {
		this.networkConnections = new SpelField.StringList(networkConnections);
	}

	public void setDns(List<String> dns) {
		this.dns = new SpelField.StringList(dns);
	}

	public void setVolumes(List<String> volumes) {
		this.volumes = new SpelField.StringList(volumes);
	}

    public void setLabels(Map<String, String> labels) {
        this.labels = new SpelField.StringMap(labels);
    }

    public ContainerSpec firstResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
                .image(image.resolve(resolver, context))
                .cmd(cmd.resolve(resolver, context))
                .envFile(envFile.resolve(resolver, context))
                .network(network.resolve(resolver, context))
                .networkConnections(networkConnections.resolve(resolver, context))
                .dns(dns.resolve(resolver, context))
                .volumes(volumes.resolve(resolver, context))
                .memoryRequest(memoryRequest.resolve(resolver, context))
                .memoryLimit(memoryLimit.resolve(resolver, context))
                .cpuRequest(cpuRequest.resolve(resolver, context))
                .cpuLimit(cpuLimit.resolve(resolver, context))
                .portMapping(portMapping.stream().map(p -> p.resolve(resolver, context)).collect(Collectors.toList()))
                .build();
    }

    public ContainerSpec finalResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
                .env(env.resolve(resolver, context))
                .labels(labels.resolve(resolver, context))
                .build();
    }

}
