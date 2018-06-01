package eu.openanalytics.containerproxy.model.spec;

import java.util.Arrays;

public class ProxyAccessControl {

	private String[] groups;

	public String[] getGroups() {
		return groups;
	}

	public void setGroups(String[] groups) {
		this.groups = groups;
	}
	
	public void copy(ProxyAccessControl target) {
		if (groups != null) {
			target.setGroups(Arrays.copyOf(groups, groups.length));
		}
	}
	
}
