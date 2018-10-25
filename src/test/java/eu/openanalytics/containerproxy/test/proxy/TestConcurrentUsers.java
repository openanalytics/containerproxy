/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import eu.openanalytics.containerproxy.ContainerProxyApplication;

@SpringBootTest(classes= {ContainerProxyApplication.class}, webEnvironment=WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
public class TestConcurrentUsers {

	@Inject
	private WebTestClient webClient;
	
	@Inject
	private Environment environment;
	
	private String[] user1;
	private String[] user2;
	private String[] specIds;
	
	@Before
	public void init() {
		user1 = new String[] { 
				environment.getProperty("proxy.users[0].name"),
				environment.getProperty("proxy.users[0].password")
		};
		user2 = new String[] { 
				environment.getProperty("proxy.users[1].name"),
				environment.getProperty("proxy.users[1].password")
		};
		specIds = new String[] {
				environment.getProperty("proxy.specs[0].id"),
				environment.getProperty("proxy.specs[1].id")
		};
	}
	
	@Test
	public void test1User2Sessions1Spec() throws Exception {
		Map<String,String> proxyInfo1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		Map<String,String> proxyInfo2 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		doDeleteProxy(proxyInfo1.get("proxyId"), proxyInfo1.get("jSessionId"));
        doDeleteProxy(proxyInfo2.get("proxyId"), proxyInfo2.get("jSessionId"));
	}
	
	@Test
	public void test1User2Sessions2Specs() throws Exception {
		Map<String,String> proxyInfo1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		Map<String,String> proxyInfo2 = loginAndLaunchProxy(user1[0], user1[1], specIds[1]);
        doDeleteProxy(proxyInfo1.get("proxyId"), proxyInfo1.get("jSessionId"));
        doDeleteProxy(proxyInfo2.get("proxyId"), proxyInfo2.get("jSessionId"));
	}
	
	@Test
	public void test2Users2Sessions1Spec() throws Exception {
		Map<String,String> proxyInfo1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		Map<String,String> proxyInfo2 = loginAndLaunchProxy(user2[0], user2[1], specIds[0]);
        doDeleteProxy(proxyInfo1.get("proxyId"), proxyInfo1.get("jSessionId"));
        doDeleteProxy(proxyInfo2.get("proxyId"), proxyInfo2.get("jSessionId"));
	}
	
	@Test
	public void test2Users2Sessions2Specs() throws Exception {
		Map<String,String> proxyInfo1 = loginAndLaunchProxy(user1[0], user1[1], specIds[0]);
		Map<String,String> proxyInfo2 = loginAndLaunchProxy(user2[0], user2[1], specIds[1]);
        doDeleteProxy(proxyInfo1.get("proxyId"), proxyInfo1.get("jSessionId"));
        doDeleteProxy(proxyInfo2.get("proxyId"), proxyInfo2.get("jSessionId"));
	}
	
	private Map<String,String> loginAndLaunchProxy(String username, String password, String specId) throws Exception {
		String jSessionId = doFormLogin(username, password);
		Map<?,?> proxyInfo = doLaunchProxy(specId, jSessionId);
		Thread.sleep(1000);
		Map<?,?> targets = (Map<?,?>) proxyInfo.get("targets");
		String endpoint = targets.keySet().iterator().next().toString() + "/";
		doGetEndpoint(endpoint, jSessionId);
		return new HashMap<String, String>() {
			{
				put("proxyId", (String)proxyInfo.get("id"));
				put("jSessionId", jSessionId);
			}
		};
	}
	
	private String doFormLogin(String username, String password) {
		String setCookie = webClient.post()
				.uri("/login")
				.body(BodyInserters.fromFormData("username", username).with("password", password))
				.exchange()
				.expectStatus().isEqualTo(302)
				.expectHeader().exists("Set-Cookie")
				.returnResult(String.class).getResponseHeaders().getFirst("Set-Cookie");

		String jSessionId = setCookie.split("=")[1];
		return jSessionId.substring(0, jSessionId.indexOf(';'));
	}
	
	private Map<?,?> doLaunchProxy(String specId, String jSessionId) {
		return webClient.post()
				.uri("/api/proxy/" + specId)
				.cookie("JSESSIONID", jSessionId)
				.header("Content-Type", MediaType.APPLICATION_JSON.toString())
				.body(BodyInserters.fromObject("[]"))
				.exchange()
				.expectStatus().isEqualTo(201)
				.returnResult(Map.class).getResponseBody().blockFirst();
	}
	
	private void doDeleteProxy(String proxyId, String jSessionId) {
		webClient.delete()
				.uri("/api/proxy/" + proxyId)
				.cookie("JSESSIONID", jSessionId)
				.exchange()
				.expectStatus().isEqualTo(200)
				.returnResult(String.class).getResponseBodyContent();
	}
	
	private byte[] doGetEndpoint(String endpoint, String jSessionId) {
		return webClient.get()
				.uri(endpoint)
				.cookie("JSESSIONID", jSessionId)
				.exchange()
				.expectStatus().isEqualTo(200)
				.returnResult(String.class).getResponseBodyContent();
	}
}
