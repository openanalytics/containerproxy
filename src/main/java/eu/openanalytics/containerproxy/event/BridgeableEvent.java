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
package eu.openanalytics.containerproxy.event;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.context.ApplicationEvent;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.MINIMAL_CLASS,
        property = "@class"
)
public abstract class BridgeableEvent extends ApplicationEvent {

    public static final String SOURCE_NOT_AVAILABLE = "SOURCE_NOT_AVAILABLE";

    public abstract BridgeableEvent withSource(String source);

    public BridgeableEvent() {
        super(SOURCE_NOT_AVAILABLE);
    }

}
