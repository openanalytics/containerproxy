/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import javax.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import eu.openanalytics.containerproxy.service.HeartbeatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@RestController
@Api(tags = "Heartbeat", description="API for sending proxy heartbeats")
public class HeartbeatController extends BaseController {

	@Inject
	private HeartbeatService heartbeatService;
	
	@RequestMapping(value="/api/heartbeat/{proxyId}", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation("Send a heartbeat to keep the specified proxy alive")
	@ApiResponses({
		@ApiResponse(code=200, message="Heartbeat registered"),
		@ApiResponse(code=404, message="Proxy not found")
	})
	public ResponseEntity<String> heartbeat(
			@ApiParam("The ID of the active proxy to send a heartbeat for")
			@PathVariable String proxyId) {
		
		heartbeatService.heartbeatReceived(proxyId);
		return new ResponseEntity<>("{}", HttpStatus.OK);
	}
}