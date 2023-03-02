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
package eu.openanalytics.containerproxy.model.spec;

public class AccessControl {

	private String[] groups;
	private String[] users;
	private String expression;

	public String[] getGroups() {
		return groups;
	}

	public String[] getUsers() {
		return users;
	}

	public String getExpression() {
		return expression;
	}

	public void setGroups(String[] groups) {
		this.groups = groups;
	}

	public void setUsers(String[] users) {
		this.users = users;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public boolean hasGroupAccess() {
		return groups != null && groups.length > 0;
	}

	public boolean hasUserAccess() {
		return users != null && users.length > 0;
	}

	public boolean hasExpressionAccess() {
		return expression != null && expression.length() > 0;
	}

}
