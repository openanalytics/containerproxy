/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.containerproxy.service.leader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Service used to run background processing on a single server, determined by the {@link ILeaderService}.
 * This implies that the processing always happens on a single server running the latest configuration.
 *
 * This unrelated to events send within or between servers. This only acts as an eventloop.
 */
@Service
public class GlobalEventLoopService {

    private final LinkedBlockingQueue<Callback> channel = new LinkedBlockingQueue<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public GlobalEventLoopService(ILeaderService leaderService) {
        Thread eventProcessor = new Thread(() -> {
            while (true) {
                try {
                    Callback event = channel.take();
                    try {
                        logger.debug("Processing event");

                        if (event.onlyIfLeader && !leaderService.isLeader()) {
                            // not the leader -> ignore events send to this channel
                            continue;
                        }

                        event.callback.run();
                    } catch (Exception ex) {
                        logger.error("Error while processing event in the GlobalEventLoop {}: ", event, ex);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "GlobalEventLoop");
        eventProcessor.setDaemon(true);
        eventProcessor.start();
    }

    public void schedule(Runnable runnable) {
        channel.add(new Callback(runnable, true));
    }

    public void schedule(Runnable runnable, boolean onlyIfLeader) {
        channel.add(new Callback(runnable, onlyIfLeader));
    }

    private record Callback(Runnable callback, boolean onlyIfLeader) {

    }

}
