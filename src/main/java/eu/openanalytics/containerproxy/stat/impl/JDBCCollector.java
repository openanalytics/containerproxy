/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
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
 */
public class JDBCCollector extends AbstractDbCollector {

    private final String url;
    private final String username;
    private final String password;
    private final String tableName;
    private HikariDataSource ds;
    @Inject
    private Environment environment;

    public JDBCCollector(String url, String username, String password, String tableNam) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.tableName = tableNam;
    }

    @PostConstruct
    public void init() throws IOException {
        ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);

        Long connectionTimeout = environment.getProperty("proxy.usage-stats-hikari.connection-timeout", Long.class);
        if (connectionTimeout != null) {
            ds.setConnectionTimeout(connectionTimeout);
        }

        Long idleTimeout = environment.getProperty("proxy.usage-stats-hikari.idle-timeout", Long.class);
        if (idleTimeout != null) {
            ds.setIdleTimeout(idleTimeout);
        }

        Long maxLifetime = environment.getProperty("proxy.usage-stats-hikari.max-lifetime", Long.class);
        if (maxLifetime != null) {
            ds.setMaxLifetime(maxLifetime);
        }

        Integer minimumIdle = environment.getProperty("proxy.usage-stats-hikari.minimum-idle", Integer.class);
        if (minimumIdle != null) {
            ds.setMinimumIdle(minimumIdle);
        }

        Integer maximumPoolSize = environment.getProperty("proxy.usage-stats-hikari.maximum-pool-size", Integer.class);
        if (maximumPoolSize != null) {
            ds.setMaximumPoolSize(maximumPoolSize);
        }

        // create table if not already exists
        try (Connection con = ds.getConnection()) {
            Statement statement = con.createStatement();
            if (con.getMetaData().getDatabaseProductName().equals("Microsoft SQL Server")) {
                statement.execute(
                    "IF OBJECT_ID('" + tableName + "', 'U') IS NULL" +
                        " create table " + tableName + "(" +
                        " event_time datetime," +
                        " username varchar(128)," +
                        " type varchar(128)," +
                        " data text)");
            } else {
                statement.execute(
                    "create table if not exists " + tableName + "(" +
                        " event_time timestamp," +
                        " username varchar(128)," +
                        " type varchar(128)," +
                        " data text)");
            }
        } catch (SQLException e) {
            throw new IOException("Exception while logging stats", e);
        }
    }

    @Override
    protected void writeToDb(long timestamp, String userId, String type, String data) throws IOException {
        String sql = "INSERT INTO " + tableName + "(event_time, username, type, data) VALUES (?,?,?,?)";
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement stmt = con.prepareStatement(sql)) {
                stmt.setTimestamp(1, new Timestamp(timestamp));
                stmt.setString(2, userId);
                stmt.setString(3, type);
                stmt.setString(4, data);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IOException("Exception while logging stats", e);
        }
    }
}
