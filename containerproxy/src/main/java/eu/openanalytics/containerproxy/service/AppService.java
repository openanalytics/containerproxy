/**
 * ShinyProxy
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
package eu.openanalytics.containerproxy.service;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.model.App;

@ConfigurationProperties(prefix = "proxy")
@Service
public class AppService {

	private List<App> apps = new ArrayList<>();
	
	@Inject
	Environment environment;
	
	public App getApp(String name) {
		for (App app: apps) {
			if (app.getName().equals(name)) return app;
		}
		return null;
	}

	public List<App> getApps() {
		return apps;
	}

}