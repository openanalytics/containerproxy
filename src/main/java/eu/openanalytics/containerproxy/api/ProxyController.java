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
package eu.openanalytics.containerproxy.api;

import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.api.dto.SwaggerDto;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;
import java.util.List;


@RestController
public class ProxyController extends BaseController {

	@Inject
	private ProxyService proxyService;

	@Inject
	private ApiSecurityService apiSecurityService;

	@Operation(summary = "Get configured proxy specs. A configuration property controls whether the full spec or a limited subset is returned.", tags = "ContainerProxy")
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Proxy specs are returned.",
					content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = SwaggerDto.ProxySpecsResponse.class),
									examples = {
											@ExampleObject(name = "Full proxy (proxy.api-security.hide-spec-details=false).", value = "{\"status\": \"success\", \"data\": [{\"id\": \"01_hello\", \"displayName\": \"Hello Application\", \"description\": \"Application which demonstrates " +
													"the basics of a Shiny app\", \"logoURL\": null, \"accessControl\": {\"groups\": [\"scientists\", \"mathematicians\"], \"users\": null, \"expression\": null}, \"containerSpecs\": " +
													"[{\"index\": 0, \"image\": \"openanalytics/shinyproxy-demo\", \"cmd\": [\"R\", \"-e\", \"shinyproxy::run_01_hello()\"], \"env\": null, \"envFile\": null, \"network\": null, " +
													"\"networkConnections\": null, \"dns\": null, \"volumes\": [], \"portMapping\": [{\"name\": \"default\", \"port\": 3838, \"targetPath\": null}], \"privileged\": false, \"memoryRequest\":" +
													" null, \"memoryLimit\": null, \"cpuRequest\": null, \"cpuLimit\": null, \"labels\": null, \"dockerSwarmSecrets\": [], \"dockerRegistryDomain\": null, \"dockerRegistryUsername\": null, " +
													"\"dockerRegistryPassword\": null}], \"parameters\": null, \"maxLifeTime\": null, \"stopOnLogout\": null, \"heartbeatTimeout\": null, \"specExtensions\": {\"eu.openanalytics.shinyproxy" +
													".ShinyProxySpecExtension\": {\"id\": \"01_hello\", \"websocketReconnectionMode\": null, \"shinyForceFullReload\": null, \"maxInstances\": null, \"hideNavbarOnMainPageLink\": null, " +
													"\"templateGroup\": null, \"templateProperties\": {}, \"alwaysShowSwitchInstance\": null}, \"eu.openanalytics.containerproxy.backend.kubernetes.KubernetesSpecExtension\": {\"id\": " +
													"\"01_hello\", \"kubernetesPodPatches\": null, \"kubernetesAdditionalManifests\": [], \"kubernetesAdditionalPersistentManifests\": []}}}, {\"id\": \"06_tabsets\", \"displayName\": null, " +
													"\"description\": null, \"logoURL\": null, \"accessControl\": {\"groups\": [\"scientists\"], \"users\": null, \"expression\": null}, \"containerSpecs\": [{\"index\": 0, \"image\": " +
													"\"openanalytics/shinyproxy-demo\", \"cmd\": [\"R\", \"-e\", \"shinyproxy::run_06_tabsets()\"], \"env\": null, \"envFile\": null, \"network\": null, \"networkConnections\": null, " +
													"\"dns\": null, \"volumes\": [], \"portMapping\": [{\"name\": \"default\", \"port\": 3838, \"targetPath\": null}], \"privileged\": false, \"memoryRequest\": null, \"memoryLimit\": null, " +
													"\"cpuRequest\": null, \"cpuLimit\": null, \"labels\": null, \"dockerSwarmSecrets\": [], \"dockerRegistryDomain\": null, \"dockerRegistryUsername\": null, \"dockerRegistryPassword\": " +
													"null}], \"parameters\": null, \"maxLifeTime\": null, \"stopOnLogout\": null, \"heartbeatTimeout\": null, \"specExtensions\": {\"eu.openanalytics.shinyproxy.ShinyProxySpecExtension\": " +
													"{\"id\": \"06_tabsets\", \"websocketReconnectionMode\": null, \"shinyForceFullReload\": null, \"maxInstances\": null, \"hideNavbarOnMainPageLink\": null, \"templateGroup\": null, " +
													"\"templateProperties\": {}, \"alwaysShowSwitchInstance\": null}, \"eu.openanalytics.containerproxy.backend.kubernetes.KubernetesSpecExtension\": {\"id\": \"06_tabsets\", " +
													"\"kubernetesPodPatches\": null, \"kubernetesAdditionalManifests\": [], \"kubernetesAdditionalPersistentManifests\": []}}}]}"),
											@ExampleObject(name = "Limited proxy (proxy.api-security.hide-spec-details=true).", value = "{\"status\": \"success\", \"data\": [{\"id\": \"01_hello\", \"displayName\": \"Hello Application\", " +
													"\"description\": \"Application which demonstrates the basics of a Shiny app\", \"logoURL\": null}, {\"id\": \"06_tabsets\", \"displayName\": null, \"description\": null, \"logoURL\": " +
													"null}]}")
									}
							)
					}),
	})
    @RequestMapping(value="/api/proxyspec", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MappingJacksonValue> listProxySpecs() {
	   List<ProxySpec> specs = proxyService.getProxySpecs(null, false);
	   return ApiResponse.success(apiSecurityService.protectSpecs(specs));
	}

	@Operation(summary = "Get a configured proxy spec. A configuration property controls whether the full spec or a limited subset is returned.", tags = "ContainerProxy")
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Proxy spec is returned.",
					content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = SwaggerDto.ProxySpecResponse.class),
									examples = {
											@ExampleObject(name = "Full proxy (proxy.api-security.hide-spec-details=false).", value = "{\"status\":\"success\",\"data\":{\"id\":\"01_hello\",\"displayName\":\"Hello Application\"," +
													"\"description\":\"Application which demonstrates the basics of a Shiny app\",\"logoURL\":null,\"accessControl\":{\"groups\":[\"scientists\",\"mathematicians\"],\"users\":null," +
													"\"expression\":null},\"containerSpecs\":[{\"index\":0,\"image\":\"openanalytics/shinyproxy-demo\",\"cmd\":[\"R\",\"-e\",\"shinyproxy::run_01_hello()\"],\"env\":null,\"envFile\":null," +
													"\"network\":null,\"networkConnections\":null,\"dns\":null,\"volumes\":[],\"portMapping\":[{\"name\":\"default\",\"port\":3838,\"targetPath\":null}],\"privileged\":false," +
													"\"memoryRequest\":null,\"memoryLimit\":null,\"cpuRequest\":null,\"cpuLimit\":null,\"labels\":null,\"dockerSwarmSecrets\":[],\"dockerRegistryDomain\":null," +
													"\"dockerRegistryUsername\":null,\"dockerRegistryPassword\":null}],\"parameters\":null,\"maxLifeTime\":null,\"stopOnLogout\":null,\"heartbeatTimeout\":null,\"specExtensions\":{\"eu" +
													".openanalytics.shinyproxy.ShinyProxySpecExtension\":{\"id\":\"01_hello\",\"websocketReconnectionMode\":null,\"shinyForceFullReload\":null,\"maxInstances\":null," +
													"\"hideNavbarOnMainPageLink\":null,\"templateGroup\":null,\"templateProperties\":{},\"alwaysShowSwitchInstance\":null},\"eu.openanalytics.containerproxy.backend.kubernetes" +
													".KubernetesSpecExtension\":{\"id\":\"01_hello\",\"kubernetesPodPatches\":null,\"kubernetesAdditionalManifests\":[],\"kubernetesAdditionalPersistentManifests\":[]}}}}"),
											@ExampleObject(name = "Limited proxy (proxy.api-security.hide-spec-details=true).", value = "{\"status\":\"success\",\"data\":{\"id\":\"01_hello\",\"displayName\":\"Hello Application\"," +
													"\"description\":\"Application which demonstrates the basics of a Shiny app\",\"logoURL\":null}}")
									}
							)
					}),
	})
	@RequestMapping(value="/api/proxyspec/{proxySpecId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MappingJacksonValue> getProxySpec(@PathVariable String proxySpecId) {
		ProxySpec spec = proxyService.findProxySpec(s -> s.getId().equals(proxySpecId), false);
		if (spec == null) {
			return ResponseEntity.status(403).body(new MappingJacksonValue(new ApiResponse<>("fail", "forbidden")));
		}
		return ApiResponse.success(apiSecurityService.protectSpecs(spec));
	}

	@Operation(summary = "Get active proxies of logged in user.", tags = "ContainerProxy")
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Active proxies are returned.",
					content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = SwaggerDto.ProxiesResponse.class),
									examples = {
											@ExampleObject(value = "{\"status\": \"success\", \"data\": [{\"id\": \"e0b39ce8-0383-4291-a786-66fc0d861da7\", \"status\": " +
													"\"Up\", \"startupTimestamp\": 1234, \"createdTimestamp\": 1234, \"userId\": \"jack\", \"specId\": \"06_tabsets\", \"displayName\": \"06_tabsets\", \"containers\": [{\"index\": 0, " +
													"\"id\": \"0f30f0db57589de9856c6a99a7483a9fff17923590f1f7e8c2a8df8f98057df5\", \"targets\": {\"e0b39ce8-0383-4291-a786-66fc0d861da7\": \"http://localhost:20001\"}, \"runtimeValues\": " +
													"{\"SHINYPROXY_CONTAINER_INDEX\": 0}}], \"runtimeValues\": {\"SHINYPROXY_DISPLAY_NAME\": \"06_tabsets\", \"SHINYPROXY_MAX_LIFETIME\": 2, \"SHINYPROXY_FORCE_FULL_RELOAD\": false, " +
													"\"SHINYPROXY_CREATED_TIMESTAMP\": \"1234\", \"SHINYPROXY_WEBSOCKET_RECONNECTION_MODE\": \"None\", \"SHINYPROXY_MAX_INSTANCES\": 1, \"SHINYPROXY_INSTANCE\": " +
													"\"e2949f09a353234965441054b13f891533814ece\", \"SHINYPROXY_PUBLIC_PATH\": \"/app_proxy/e0b39ce8-0383-4291-a786-66fc0d861da7/\", \"SHINYPROXY_HEARTBEAT_TIMEOUT\": -1, " +
													"\"SHINYPROXY_APP_INSTANCE\": \"_\"}}, {\"id\": \"8337742a-fd16-487f-9e1e-6d648dd0d64d\", \"status\": \"Up\", \"startupTimestamp\": 1234, \"createdTimestamp\": 1234, \"userId\": " +
													"\"jack\", \"specId\": \"01_hello\", \"displayName\": \"Hello Application\", \"containers\": [{\"index\": 0, \"id\": \"bfbd6f26541f62160dd20a8717af516c7dd9ed4b08e934f253c3142819a90fef\"," +
													" \"targets\": {\"8337742a-fd16-487f-9e1e-6d648dd0d64d\": \"http://localhost:20000\"}, \"runtimeValues\": {\"SHINYPROXY_CONTAINER_INDEX\": 0}}], \"runtimeValues\": " +
													"{\"SHINYPROXY_DISPLAY_NAME\": \"Hello Application\", \"SHINYPROXY_MAX_LIFETIME\": -1, \"SHINYPROXY_FORCE_FULL_RELOAD\": false, \"SHINYPROXY_CREATED_TIMESTAMP\": \"1234\", " +
													"\"SHINYPROXY_WEBSOCKET_RECONNECTION_MODE\": \"None\", \"SHINYPROXY_MAX_INSTANCES\": 1, \"SHINYPROXY_INSTANCE\": \"e2949f09a353234965441054b13f891533814ece\", \"SHINYPROXY_PUBLIC_PATH\":" +
													" \"/app_proxy/8337742a-fd16-487f-9e1e-6d648dd0d64d/\", \"SHINYPROXY_HEARTBEAT_TIMEOUT\": -1, \"SHINYPROXY_APP_INSTANCE\": \"_\"}}]}"),
									}
							)
					}),
	})
	@JsonView(Views.UserApi.class)
	@RequestMapping(value="/api/proxy", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<List<Proxy>>> listProxies() {
		return ApiResponse.success(proxyService.getProxiesOfCurrentUser(null));
	}

	@Operation(summary = "Get an active proxy.", tags = "ContainerProxy")
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "The requesed proxy is returned.",
					content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = SwaggerDto.ProxyResponse.class),
									examples = {
											@ExampleObject(value = "{\"status\": \"success\", \"data\": {\"id\": \"e0b39ce8-0383-4291-a786-66fc0d861da7\", \"status\": " +
													"\"Up\", \"startupTimestamp\": 1234, \"createdTimestamp\": 1234, \"userId\": \"jack\", \"specId\": \"06_tabsets\", \"displayName\": \"06_tabsets\", \"containers\": [{\"index\": 0, " +
													"\"id\": \"0f30f0db57589de9856c6a99a7483a9fff17923590f1f7e8c2a8df8f98057df5\", \"targets\": {\"e0b39ce8-0383-4291-a786-66fc0d861da7\": \"http://localhost:20001\"}, \"runtimeValues\": " +
													"{\"SHINYPROXY_CONTAINER_INDEX\": 0}}], \"runtimeValues\": {\"SHINYPROXY_DISPLAY_NAME\": \"06_tabsets\", \"SHINYPROXY_MAX_LIFETIME\": 2, \"SHINYPROXY_FORCE_FULL_RELOAD\": false, " +
													"\"SHINYPROXY_CREATED_TIMESTAMP\": \"1234\", \"SHINYPROXY_WEBSOCKET_RECONNECTION_MODE\": \"None\", \"SHINYPROXY_MAX_INSTANCES\": 1, \"SHINYPROXY_INSTANCE\": " +
													"\"e2949f09a353234965441054b13f891533814ece\", \"SHINYPROXY_PUBLIC_PATH\": \"/app_proxy/e0b39ce8-0383-4291-a786-66fc0d861da7/\", \"SHINYPROXY_HEARTBEAT_TIMEOUT\": -1, " +
													"\"SHINYPROXY_APP_INSTANCE\": \"_\"}}}"),
									}
							)
					}),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Proxy not found or no permission to access this proxy.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"forbidden\"}")}
                            )
                    }),
	})
	@JsonView(Views.UserApi.class)
	@RequestMapping(value="/api/proxy/{proxyId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<Proxy>> getProxy(@PathVariable String proxyId) {
		Proxy proxy = proxyService.findProxy(p -> p.getId().equals(proxyId), false);
		if (proxy == null) {
			return ApiResponse.failForbidden();
		}
		return ApiResponse.success(proxy);
	}

    @Operation(summary = "Create and start a proxy using the given spec id.", tags = "ContainerProxy")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "The proxy has been created.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = SwaggerDto.ProxyResponse.class),
                                    examples = {
                                            @ExampleObject(value = "{\"status\": \"success\", \"data\": {\"id\": \"7e513890-da59-405c-bceb-c8ffeed62658\", \"status\": \"Up\", \"startupTimestamp\": 1234, \"createdTimestamp\": 1234, " +
													"\"userId\": \"jack\", \"specId\": \"01_hello\", \"displayName\": \"Hello Application\", \"containers\": [{\"index\": 0, \"id\": " +
													"\"9e433c7da03090b0dd4e36c00b2a11d804ff5036a0b700c0e213d62569ab674a\", \"targets\": {\"7e513890-da59-405c-bceb-c8ffeed62658\": \"http://localhost:20000\"}, \"runtimeValues\": " +
													"{\"SHINYPROXY_CONTAINER_INDEX\": 0}}], \"runtimeValues\": {\"SHINYPROXY_DISPLAY_NAME\": \"Hello Application\", \"SHINYPROXY_MAX_LIFETIME\": -1, \"SHINYPROXY_CREATED_TIMESTAMP\": " +
													"\"1234\", \"SHINYPROXY_INSTANCE\": \"e2949f09a353234965441054b13f891533814ece\", \"SHINYPROXY_HEARTBEAT_TIMEOUT\": -1}}}"),
                                    }
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Proxy spec not found or no permission to use this proxy spec.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"forbidden\"}")}
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Failed to start proxy.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"Failed to start proxy\"}")}
                            )
                    }),
    })
    @JsonView(Views.UserApi.class)
	@RequestMapping(value="/api/proxy/{proxySpecId}", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<Proxy>> startProxy(@PathVariable String proxySpecId) throws InvalidParametersException {
		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(proxySpecId), false);
		if (baseSpec == null) {
			return ApiResponse.failForbidden();
		}

        try {
            Proxy proxy = proxyService.startProxy(baseSpec);
            return ApiResponse.created(proxy);
        } catch (Throwable t ) {
            return ApiResponse.error("Failed to start proxy");
        }
	}

}
