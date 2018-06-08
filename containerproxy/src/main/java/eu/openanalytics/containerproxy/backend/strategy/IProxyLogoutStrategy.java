package eu.openanalytics.containerproxy.backend.strategy;

/**
 * Defines a strategy for deciding what to do with a user's proxies when
 * the user logs out.
 */
public interface IProxyLogoutStrategy {

	public void onLogout(String userId);

}
