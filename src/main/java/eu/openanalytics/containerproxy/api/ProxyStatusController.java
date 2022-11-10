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
package eu.openanalytics.containerproxy.api;

import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.api.dto.ChangeProxyStatusDto;
import eu.openanalytics.containerproxy.api.exception.ProxyNotFoundException;
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
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @ResponseBody
    @RequestMapping(value = "/api/{proxyId}/status", method = RequestMethod.PUT)
    public ResponseEntity<ApiResponse<Void>> changeProxyStatus(@PathVariable String proxyId, @RequestBody ChangeProxyStatusDto changeProxyStateDto) {
        Proxy proxy = proxyService.getProxy(proxyId);
        if (proxy == null) {
            throw new ProxyNotFoundException(proxyId);
        }

        if (changeProxyStateDto.getDesiredState().equals("Pausing")) {
            if (!proxy.getStatus().equals(ProxyStatus.Up)) {
                return ApiResponse.fail(String.format("Cannot pause proxy because it is not in Up status (status is %s)", proxy.getStatus()));
            }
            asyncProxyService.pauseProxy(proxy,false);
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
            if (!proxy.getStatus().equals(ProxyStatus.New)
                    && !proxy.getStatus().equals(ProxyStatus.Starting)
                    && !proxy.getStatus().equals(ProxyStatus.Up)
                    && !proxy.getStatus().equals(ProxyStatus.Resuming)
                    && !proxy.getStatus().equals(ProxyStatus.Paused)) {
                return ApiResponse.fail(String.format("Cannot stop proxy because it is not in New, Up or Paused status (status is %s)", proxy.getStatus()));
            }
            asyncProxyService.stopProxy(proxy,false);
        } else {
            return ApiResponse.fail("Invalid desiredStatus");
        }

        return ApiResponse.success();
    }

    /**
     * Get the state of a proxy and optionally watches for the state to become in a final (i.e. non transitioning) state.
     */
    @JsonView(Views.UserApi.class)
    @RequestMapping(value = "/api/{proxyId}/status", method = RequestMethod.GET)
    public DeferredResult<ResponseEntity<ApiResponse<Proxy>>> getProxyStatus(@PathVariable String proxyId,
                                                                @RequestParam(value = "watch", required = false, defaultValue = "false") Boolean watch,
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
        watchers.putIfAbsent(proxyId, new ArrayList<>());
        watchers.get(proxyId).add(output);
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

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ProxyNotFoundException.class)
    public ApiResponse<Void> handleProxyNotFoundException(ProxyNotFoundException ex) {
        return ApiResponse.errorBody(ex.getMessage());
    }

}
