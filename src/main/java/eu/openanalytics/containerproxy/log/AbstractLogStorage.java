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
package eu.openanalytics.containerproxy.log;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.util.ProxyHashMap;
import org.springframework.core.env.Environment;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractLogStorage implements ILogStorage {

    private ConcurrentHashMap<String, LogPaths> proxyStreams = ProxyHashMap.create();

    @Inject
    protected Environment environment;

    protected String containerLogPath;

    @Override
    public void initialize() throws IOException {
        containerLogPath = environment.getProperty("proxy.container-log-path");
    }

    @Override
    public String getStorageLocation() {
        return containerLogPath;
    }

    @Override
    public LogPaths getLogs(Proxy proxy) {
        return proxyStreams.computeIfAbsent(proxy.getId(), (k) -> {
            String timestamp = new SimpleDateFormat("dd_MMM_yyyy_kk_mm_ss").format(new Date()); // TODO include time
            return new LogPaths(
                    Paths.get(containerLogPath, String.format("%s_%s_%s_stdout.log", proxy.getSpecId(), proxy.getId(), timestamp)),
                    Paths.get(containerLogPath, String.format("%s_%s_%s_stderr.log", proxy.getSpecId(), proxy.getId(), timestamp))
            );
        });
    }

    @Override
    public void stopService() {
        proxyStreams = ProxyHashMap.create();
    }

}
