package eu.openanalytics.containerproxy.event;

import org.springframework.context.ApplicationEvent;

public class ProxyStartFailedEvent extends ApplicationEvent {

    private final String userId;
    private final String specId;

    public ProxyStartFailedEvent(Object source, String userId, String specId) {
        super(source);
        this.userId = userId;
        this.specId = specId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSpecId() {
        return specId;
    }

}
