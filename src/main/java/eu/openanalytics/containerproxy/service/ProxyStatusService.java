package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProxyStatusService {

    private final Map<String, ProxyStartupLog> startupLog = new ConcurrentHashMap<>();

    /**
     * Step 1: proxy has been created
     */
    public void proxyCreated(Proxy proxy) {
        ProxyStartupLog proxyStartupLog = new ProxyStartupLog();
        proxyStartupLog.getCreateProxy().stepStarted();
        startupLog.put(proxy.getId(), proxyStartupLog);
    }

    /**
     * Step 2: container has been created and is starting up
     */
    public void containerStarting(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getStartContainer(container.getIndex()).stepStarted();
    }

    /**
     * Step 3: container has been started and application is starting up
     */
    public void containerStarted(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getStartContainer(container.getIndex()).stepSucceeded();
        startupLog.get(proxy.getId()).getStartApplication(container.getIndex()).stepStarted();
    }

    /**
     * Step 3 (fail): container could not be started
     */
    public void containerStartupFailed(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getStartContainer(container.getIndex()).stepFailed();
        startupLog.get(proxy.getId()).getCreateProxy().stepFailed();
    }


    /**
     *  Step 4: all containers has been started and all applications are running -> proxy has been started
     */
    public void proxyStarted(Proxy proxy) {
        for (Container container: proxy.getContainers()) {
            startupLog.get(proxy.getId()).getStartApplication(container.getIndex()).stepSucceeded();
        }
        startupLog.get(proxy.getId()).getCreateProxy().stepSucceeded();
    }

    /**
     * Step 4 (fail): one of the applications is unreachable -> it could not be started
     */
    public void applicationStartupFailed(Proxy proxy) {
        for (Container container: proxy.getContainers()) {
            startupLog.get(proxy.getId()).getStartApplication(container.getIndex()).stepFailed();
        }
        startupLog.get(proxy.getId()).getCreateProxy().stepFailed();
    }

    /**
     * Step 5: proxy has been removed (e.g. stopped by user, stopped because it failed to start ...)
     */
    public void proxyRemoved(Proxy proxy) {
        startupLog.remove(proxy.getId());
    }

    /**
     * Get the startup log of a specific container
     */
    public ProxyStartupLog getStartupLog(String proxyId) {
        return startupLog.get(proxyId);
    }

}
