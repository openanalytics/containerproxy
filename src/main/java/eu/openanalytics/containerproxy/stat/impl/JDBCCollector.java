/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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
package eu.openanalytics.containerproxy.stat.impl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

import eu.openanalytics.containerproxy.service.EventService.Event;
import eu.openanalytics.containerproxy.stat.IStatCollector;

/**
 * 
 * # MonetDB, Postgresql, MySQL/MariaDB usage-stats-url:
 * jdbc:monetdb://localhost:50000/usage_stats usage-stats-url:
 * jdbc:postgresql://localhost/postgres usage-stats-url:
 * jdbc:mysql://localhost/shinyproxy
 * 
 * Assumed table layout:
 * 
 * create table event( event_time timestamp, username varchar(128), type
 * varchar(128), data text );
 * 
 * 
 * # MS SQL Server usage-stats-url:
 * jdbc:sqlserver://localhost;databaseName=shinyproxy
 * 
 * Assumed table layout:
 * 
 * create table event( event_time datetime, username varchar(128), type
 * varchar(128), data text );
 * 
 */
public class JDBCCollector implements IStatCollector {

	private HikariDataSource ds;

	public JDBCCollector(Environment environment) {
		String baseURL = environment.getProperty("proxy.usage-stats-url");
		String username = environment.getProperty("proxy.usage-stats-username", "monetdb");
		String password = environment.getProperty("proxy.usage-stats-password", "monetdb");
		ds = new HikariDataSource();
		ds.setJdbcUrl(baseURL);
		ds.setUsername(username);
		ds.setPassword(password);
	}


	@Override
	public void accept(Event event, Environment env) throws IOException {
		String sql = "INSERT INTO event(event_time, username, type, data) VALUES (?,?,?,?)";
		try (PreparedStatement stmt = ds.getConnection().prepareStatement(sql)) {
			stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			stmt.setString(2, event.user);
			stmt.setString(3, event.type);
			stmt.setString(4, event.data);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new IOException("Exception while loggin stats", e);
		}
	}
}
