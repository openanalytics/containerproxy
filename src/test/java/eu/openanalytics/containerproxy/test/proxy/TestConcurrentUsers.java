/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.test.proxy;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

@SpringBootTest(classes = { ContainerProxyApplication.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
public class TestConcurrentUsers {

	@Inject
	private TestRestTemplate restTemplate;

	@Inject
	private Environment environment;

	@LocalServerPort
	private int port;

	private String[] user1;
	private String[] user2;
	private String[] specIds;

	@BeforeEach
	public void init() {
		user1 = new String[] { environment.getProperty("proxy.users[0].name"),
				environment.getProperty("proxy.users[0].password") };
		user2 = new String[] { environment.getProperty("proxy.users[1].name"),
				environment.getProperty("proxy.users[1].password") };
		specIds = new String[] { environment.getProperty("proxy.specs[0].id"),
				environment.getProperty("proxy.specs[1].id") };
	}

	@Test
	public void test1User2Sessions1Spec() throws Exception {
		String proxyId1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		String proxyId2 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		doDeleteProxy(proxyId1, user1[0], user1[1]);
		doDeleteProxy(proxyId2, user1[0], user1[1]);
		Thread.sleep(2000); // delete is handled async -> give time to stop container
	}

	@Test
	public void test1User2Sessions2Specs() throws Exception {
		String proxyId1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		String proxyId2 = loginAndLaunchProxy(user1[0], user1[1], specIds[1]);
		doDeleteProxy(proxyId1, user1[0], user1[1]);
		doDeleteProxy(proxyId2, user1[0], user1[1]);
		Thread.sleep(2000); // delete is handled async -> give time to stop container
	}

	@Test
	public void test2Users2Sessions1Spec() throws Exception {
		String proxyId1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		String proxyId2 = loginAndLaunchProxy(user2[0], user2[1], specIds[0]);
		doDeleteProxy(proxyId1, user1[0], user1[1]);
		doDeleteProxy(proxyId2, user2[0], user2[1]);
		Thread.sleep(2000); // delete is handled async -> give time to stop container
	}

	@Test
	public void test2Users2Sessions2Specs() throws Exception {
		String proxyId1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		String proxyId2 = loginAndLaunchProxy(user2[0], user2[1], specIds[1]);
		doDeleteProxy(proxyId1, user1[0], user1[1]);
		doDeleteProxy(proxyId2, user2[0], user2[1]);
		Thread.sleep(2000); // delete is handled async -> give time to stop container
	}

	private String loginAndLaunchProxy(String username, String password, String specId) throws Exception {
		Set<RuntimeSetting> createProxyBody = new HashSet<RuntimeSetting>();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		HttpEntity<Set<RuntimeSetting>> createProxyRequest = new HttpEntity<Set<RuntimeSetting>>(createProxyBody,
				headers);

		ResponseEntity<Proxy> createProxyResponse = this.restTemplate.withBasicAuth(username, password).postForEntity(
				"http://localhost:" + port + "/api/proxy/{proxySpecId}", createProxyRequest, Proxy.class, specId);
		Assertions.assertEquals(201, createProxyResponse.getStatusCodeValue());

		Proxy createdProxy = createProxyResponse.getBody();
		Thread.sleep(1000);
		String endpoint = createdProxy.getTargets().keySet().iterator().next().toString() + "/";
		doGetEndpoint(endpoint, username, password);
		
		return createdProxy.getId();
	}

	private void doDeleteProxy(String proxyId, String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> deleteProxyResponse = this.restTemplate
				.withBasicAuth(username, password)
				.exchange("http://localhost:" + port + "/api/proxy/{proxyId}", HttpMethod.DELETE, null, String.class, proxyId);

		Assertions.assertEquals(200, deleteProxyResponse.getStatusCodeValue());
	}

	private byte[] doGetEndpoint(String endpoint, String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> getEndpointResponse = this.restTemplate.withBasicAuth(username, password)
				.getForEntity("http://localhost:" + port + "/api/route/" + endpoint, String.class);
		Assertions.assertEquals(200, getEndpointResponse.getStatusCodeValue());

		return getEndpointResponse.getBody().getBytes();
	}
}
