package eu.openanalytics.containerproxy.event;

import org.springframework.context.ApplicationEvent;

import java.time.Duration;

public class ProxyStartEvent extends ApplicationEvent {

    private final String userId;
    private final String specId;
    private final Duration startupTime;

    public ProxyStartEvent(Object source, String userId, String specId, Duration startupTime) {
        super(source);
        this.userId = userId;
        this.specId = specId;
        this.startupTime = startupTime;
    }

    public String getUserId() {
        return userId;
    }

    public String getSpecId() {
        return specId;
    }

    public Duration getStartupTime() {
        return startupTime;
    }
}
