package eu.openanalytics.containerproxy.event;

import org.springframework.context.ApplicationEvent;

import java.time.Duration;

public class ProxyStopEvent extends ApplicationEvent {

    private final String userId;
    private final String specId;
    private final Duration usageTime;

    public ProxyStopEvent(Object source, String userId, String specId, Duration usageTime) {
        super(source);
        this.userId = userId;
        this.specId = specId;
        this.usageTime = usageTime;
    }

    public String getUserId() {
        return userId;
    }

    public String getSpecId() {
        return specId;
    }

    public Duration getUsageTime() {
        return usageTime;
    }
}
