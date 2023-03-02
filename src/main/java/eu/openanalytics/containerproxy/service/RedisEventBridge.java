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
package eu.openanalytics.containerproxy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.openanalytics.containerproxy.event.BridgeableEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

import java.io.IOException;

public class RedisEventBridge implements MessageListener {

    private final RedisTemplate<String, BridgeableEvent> redisTemplate;

    private final ChannelTopic channelTopic;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ObjectMapper objectMapper;

    private final String source;

    public RedisEventBridge(RedisTemplate<String, BridgeableEvent> redisTemplate, ChannelTopic channelTopic, ApplicationEventPublisher applicationEventPublisher) {
        this.redisTemplate = redisTemplate;
        this.channelTopic = channelTopic;
        this.applicationEventPublisher = applicationEventPublisher;
        String instanceId = java.util.UUID.randomUUID().toString();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        this.source = "SHINYPROXY_REDIS_BRIDGE/" + instanceId;
    }

    @EventListener
    public void onGenerateEvent(BridgeableEvent event) {
        if (event.getSource().equals(source)) {
            return;
        }

        BridgeableEvent outgoingEvent = event.withSource(source);
        redisTemplate.convertAndSend(channelTopic.getTopic(), outgoingEvent);
    }

    public void onMessage(Message message, byte[] pattern) {
        try {
            BridgeableEvent incomingEvent = objectMapper.readValue(message.getBody(), BridgeableEvent.class);

            if (!incomingEvent.getSource().equals(source)) {
                applicationEventPublisher.publishEvent(incomingEvent.withSource(source));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
