package eu.openanalytics.containerproxy.util;

import eu.openanalytics.containerproxy.backend.strategy.impl.DefaultProxyLogoutStrategy;
import eu.openanalytics.containerproxy.service.AccessControlService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Helper class that inject dependencies to lose up circular dependencies between beans.
 * With refactoring this helper class might get redundant at a later stage.
 */
@Component
public class DependencyHelper {

    private HeartbeatService heartbeatService;

    private ProxyMappingManager proxyMappingManager;

    private ProxyService proxyService;

    private DefaultProxyLogoutStrategy defaultProxyLogoutStrategy;

    private UserService userService;

    private AccessControlService accessControlService;

    public DependencyHelper(
            @Autowired HeartbeatService heartbeatService,
            @Autowired ProxyMappingManager proxyMappingManager,
            @Autowired ProxyService proxyService,
            @Autowired DefaultProxyLogoutStrategy defaultProxyLogoutStrategy,
            @Autowired UserService userService,
            @Autowired AccessControlService accessControlService
    ) {
        this.heartbeatService = heartbeatService;
        this.proxyMappingManager = proxyMappingManager;
        this.linkBeansProxyHeartbeat();

        this.proxyService = proxyService;
        this.defaultProxyLogoutStrategy = defaultProxyLogoutStrategy;
        this.linkBeansProxyProxyLogout();

        this.userService = userService;
        this.accessControlService = accessControlService;
        this.linkBeansUserAccessControl();
    }

    private void linkBeansProxyHeartbeat() {
        this.proxyMappingManager.setHeartbeatService(this.heartbeatService);
    }

    private void linkBeansProxyProxyLogout() {
        this.defaultProxyLogoutStrategy.setProxyService(this.proxyService);
    }

    private void linkBeansUserAccessControl() {
        this.userService.setAccessControlService(this.accessControlService);
    }
}
