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
import eu.openanalytics.containerproxy.api.dto.ChangeProxyStatusDto;
import eu.openanalytics.containerproxy.api.dto.SwaggerDto;
import eu.openanalytics.containerproxy.event.ProxyPauseEvent;
import eu.openanalytics.containerproxy.event.ProxyResumeEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.service.AsyncProxyService;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class ProxyStatusController {

    @Inject
    private ProxyService proxyService;

    @Inject
    private AsyncProxyService asyncProxyService;

    @Inject
    private UserService userService;

    private final ConcurrentHashMap<String, List<DeferredResult<ResponseEntity<ApiResponse<Proxy>>>>> watchers = new ConcurrentHashMap<>();

    @Operation(
            summary = "Change the status of a proxy.", tags = "ContainerProxy",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChangeProxyStatusDto.class),
                            examples = {
                                    @ExampleObject(name = "Stopping", description = "Stop a proxy.", value = "{\"desiredState\": \"Stopping\"}"),
                                    @ExampleObject(name = "Pausing", description = "Pause a proxy.", value = "{\"desiredState\": \"Pausing\"}"),
                                    @ExampleObject(name = "Resuming", description = "Resume a proxy.", value = "{\"desiredState\": \"Resuming\"}"),
                                    @ExampleObject(name = "Resuming with parameters", description = "Resume a proxy.", value = "{\"desiredState\": \"Resuming\", \"parameters\":{\"resources\":\"2 CPU cores - 8G RAM\",\"other_parameter\":\"example\"}}")
                            }
                    )
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Status of proxy changed.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {@ExampleObject(value = "{\"status\": \"success\", \"data\": null}")}
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Proxy not found or no permission.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"forbidden\"}")}
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Proxy is not in correct status to change status.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"Cannot stop proxy because it is not in New, Up or Paused status (status is Stopping)\"}")}
                            )
                    })
    })
    @ResponseBody
    @RequestMapping(value = "/api/{proxyId}/status", method = RequestMethod.PUT)
    public ResponseEntity<ApiResponse<Void>> changeProxyStatus(@PathVariable String proxyId, @RequestBody ChangeProxyStatusDto changeProxyStateDto) {
        Proxy proxy = proxyService.getProxy(proxyId);
        if (proxy == null) {
            return ApiResponse.failForbidden();
        }

        if (changeProxyStateDto.getDesiredState().equals("Pausing")) {
            if (!proxy.getStatus().equals(ProxyStatus.Up)) {
                return ApiResponse.fail(String.format("Cannot pause proxy because it is not in Up status (status is %s)", proxy.getStatus()));
            }
            asyncProxyService.pauseProxy(proxy, false);
        } else if (changeProxyStateDto.getDesiredState().equals("Resuming")) {
            if (!proxy.getStatus().equals(ProxyStatus.Paused)) {
                return ApiResponse.fail(String.format("Cannot resume proxy because it is not in Paused status (status is %s)", proxy.getStatus()));
            }
            try {
                asyncProxyService.resumeProxy(proxy, changeProxyStateDto.getParameters());
            } catch (InvalidParametersException ex) {
                return ApiResponse.fail(ex.getMessage());
            }
        } else if (changeProxyStateDto.getDesiredState().equals("Stopping")) {
            if (proxy.getStatus().equals(ProxyStatus.Stopped)) {
                return ApiResponse.fail("Cannot stop proxy because it is already stopped");
            }
            asyncProxyService.stopProxy(proxy, false);
        } else {
            return ApiResponse.fail("Invalid desiredState");
        }

        return ApiResponse.success();
    }

    /**
     * Get the state of a proxy and optionally watches for the state to become in a final (i.e. non transitioning) state.
     */
    @Operation(summary = "Get the status of a proxy and optionally wait for the status to change.", tags = "ContainerProxy")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Proxy found and status returned.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = SwaggerDto.ProxyResponse.class),
                                    examples = {
                                            @ExampleObject(name = "Up Proxy", value = "{\"status\": \"success\", \"data\": {\"id\": \"5f39a7cf-c9ff-4a85-9313-d561ec79cca9\", \"status\": \"Up\", \"startupTimestamp\": 1234, " +
                                                    "\"createdTimestamp\": 1234, \"userId\": \"jack\", \"specId\": \"01_hello\", \"displayName\": \"01_hello\", \"containers\": [{\"index\": 0, \"id\": " +
                                                    "\"96a9e43437e356a8bbd6abb5bd4aa9f1436db49d95b3de8abcf03bccb15e2254\", \"targets\": {\"5f39a7cf-c9ff-4a85-9313-d561ec79cca9\": \"http://localhost:20000\"}, \"runtimeValues\": " +
                                                    "{\"SHINYPROXY_CONTAINER_INDEX\": 0}}], \"runtimeValues\": {\"SHINYPROXY_DISPLAY_NAME\": \"01_hello\", \"SHINYPROXY_MAX_LIFETIME\": -1, \"SHINYPROXY_FORCE_FULL_RELOAD\": false, " +
                                                    "\"SHINYPROXY_CREATED_TIMESTAMP\": \"1234\", \"SHINYPROXY_WEBSOCKET_RECONNECTION_MODE\": \"None\", \"SHINYPROXY_INSTANCE\": \"03bc19d7d1970f737815c2d27ece37496ddee6f0\", " +
                                                    "\"SHINYPROXY_MAX_INSTANCES\": 1, \"SHINYPROXY_HEARTBEAT_TIMEOUT\": -1, \"SHINYPROXY_PUBLIC_PATH\": \"/app_proxy/5f39a7cf-c9ff-4a85-9313-d561ec79cca9/\", \"SHINYPROXY_APP_INSTANCE\": " +
                                                    "\"_\"}}}"),
                                            @ExampleObject(name = "Stopped proxy", value = "{\"status\": \"success\", \"data\": {\"id\": \"515a2e7e-ecf1-45b4-aebb-79d2029e1904\", \"status\": \"Stopped\", \"startupTimestamp\": 0, " +
                                                    "\"createdTimestamp\": 0, \"userId\": null, \"specId\": null, \"displayName\": null, \"containers\": [], \"runtimeValues\": {}}}")
                                    }
                            )
                    }),
    })
    @JsonView(Views.UserApi.class)
    @RequestMapping(value = "/api/{proxyId}/status", method = RequestMethod.GET)
    public DeferredResult<ResponseEntity<ApiResponse<Proxy>>> getProxyStatus(@PathVariable String proxyId,
                                                                             @Parameter(description = "Whether to watch for the status to change to Up, Stopped or Paused.")
                                                                             @RequestParam(value = "watch", required = false, defaultValue = "false") Boolean watch,
                                                                             @Parameter(description = "Time to wait for status to change, must be between 10 and 60 seconds (inclusive).")
                                                                             @RequestParam(value = "timeout", required = false, defaultValue = "10") Long timeout) {
        Proxy proxy = proxyService.getProxy(proxyId);
        if (proxy == null) {
            // proxy not found -> assume it has been stopped
            DeferredResult<ResponseEntity<ApiResponse<Proxy>>> res = new DeferredResult<>();
            res.setResult(ApiResponse.success(Proxy.builder()
                    .id(proxyId)
                    .status(ProxyStatus.Stopped)
                    .build()));
            return res;
        }

        if (!userService.isOwner(proxy)) {
            throw new AccessDeniedException("Cannot get state of proxy %s: access denied");
        }

        if (!watch) {
            DeferredResult<ResponseEntity<ApiResponse<Proxy>>> res = new DeferredResult<>();
            res.setResult(ApiResponse.success(proxy));
            return res;
        }

        if (proxy.getStatus() == ProxyStatus.Up
                || proxy.getStatus() == ProxyStatus.Stopped
                || proxy.getStatus() == ProxyStatus.Paused) {
            DeferredResult<ResponseEntity<ApiResponse<Proxy>>> res = new DeferredResult<>();
            res.setResult(ApiResponse.success(proxy));
            return res;
        }

        if (timeout < 10 || timeout > 60) {
            DeferredResult<ResponseEntity<ApiResponse<Proxy>>> res = new DeferredResult<>();
            res.setResult(ApiResponse.fail("Timeout must be between 10 and 60 seconds (inclusive)."));
            return res;
        }

        DeferredResult<ResponseEntity<ApiResponse<Proxy>>> output = new DeferredResult<>(timeout * 1000, () -> {
            // note: no need to remove watcher on timeout, it will be cleaned up when proxy starts/fails/stops/...
            Proxy res = proxyService.getProxy(proxyId);
            if (res != null) {
                return ApiResponse.success(res);
            }
            // proxy not found -> assume it has been stopped
            return ApiResponse.success(Proxy.builder()
                    .id(proxyId)
                    .status(ProxyStatus.Stopped)
                    .build());
        });

        watchers.compute(proxyId, (key, oldValue) -> {
            if (oldValue == null) {
                oldValue = new ArrayList<>();
            }
            oldValue.add(output);
            return oldValue;
        });

        return output;
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) {
        completeWatchers(event.getProxyId());
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
        completeWatchersStoppedProxy(event.getProxyId());
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) {
        completeWatchersStoppedProxy(event.getProxyId());
    }

    @EventListener
    public void onProxyPauseEvent(ProxyPauseEvent event) {
        completeWatchers(event.getProxyId());
    }

    @EventListener
    public void onProxyPauseEvent(ProxyResumeEvent event) {
        completeWatchers(event.getProxyId());
    }

    private void completeWatchers(String proxyId) {
        List<DeferredResult<ResponseEntity<ApiResponse<Proxy>>>> proxyWatchers = watchers.remove(proxyId);
        if (proxyWatchers == null) {
            return;
        }
        for (DeferredResult<ResponseEntity<ApiResponse<Proxy>>> watcher : proxyWatchers) {
            if (!watcher.isSetOrExpired()) {
                watcher.setResult(ApiResponse.success(proxyService.getProxy(proxyId)));
            }
        }
    }

    private void completeWatchersStoppedProxy(String proxyId) {
        List<DeferredResult<ResponseEntity<ApiResponse<Proxy>>>> proxyWatchers = watchers.remove(proxyId);
        if (proxyWatchers == null) {
            return;
        }
        for (DeferredResult<ResponseEntity<ApiResponse<Proxy>>> watcher : proxyWatchers) {
            if (!watcher.isSetOrExpired()) {
                watcher.setResult(ApiResponse.success(Proxy.builder()
                        .id(proxyId)
                        .status(ProxyStatus.Stopped)
                        .build()));
            }
        }
    }

}
