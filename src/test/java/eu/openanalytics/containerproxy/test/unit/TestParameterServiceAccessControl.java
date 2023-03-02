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
package eu.openanalytics.containerproxy.test.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.AccessControlEvaluationService;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ParametersService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.test.proxy.PropertyOverrideContextInitializer;
import eu.openanalytics.containerproxy.test.proxy.TestIntegrationOnKube;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {TestIntegrationOnKube.TestConfiguration.class, ContainerProxyApplication.class})
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
@ActiveProfiles("parameters")
public class TestParameterServiceAccessControl {

    private ParametersService parametersService;
    private IAuthenticationBackend authBackend;

    @Inject
    private IProxySpecProvider proxySpecProvider;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private ProxyService proxyService;
    private UserService userService;

    @PostConstruct
    public void init() {
        authBackend = mock(IAuthenticationBackend.class);
        userService = mock(UserService.class);
        SpecExpressionResolver specExpressionResolver = new SpecExpressionResolver(new GenericApplicationContext());
        AccessControlEvaluationService accessControlEvaluationService = new AccessControlEvaluationService(authBackend, userService, specExpressionResolver);

        parametersService = new ParametersService(proxySpecProvider, accessControlEvaluationService, objectMapper);
    }

    @Test
    public void testWithAccessControl() throws InvalidParametersException {
        when(authBackend.hasAuthorization()).thenReturn(true);

        ProxySpec spec = proxyService.getProxySpec("with-access-control");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("thomas");

        AllowedParametersForUser allowedParametersForUser = parametersService.calculateAllowedParametersForUser(auth, spec, null);

        Assertions.assertEquals(10, allowedParametersForUser.getAllowedCombinations().size());
        Assertions.assertEquals(
                Arrays.asList(
                        "base_r",
                        "biogrid_r"
                ),
                allowedParametersForUser.getValues().get("environment"));
        Assertions.assertEquals(
                Arrays.asList(
                         "3.0.6",
                         "4.0.5",
                         "4.1.3",
                         "4.0.3"
                ),
                allowedParametersForUser.getValues().get("version"));
        Assertions.assertEquals(
                Arrays.asList(
                         "2G",
                         "4G",
                         "8G"
                ),
                allowedParametersForUser.getValues().get("memory"));

        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 1)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 4)));

        // try to "start" the app with correct parameters
        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("environment", "base_r");
            put("version", "4.0.5");
            put("memory", "8G");
        }};

        Assertions.assertTrue(parametersService.parseAndValidateRequest(auth, spec, providedParameters).isPresent());

        // try to "start" the app with not-allowed parameters
        Map<String, String> providedParameters2 = new HashMap<String, String>() {{
            put("environment", "breeding_r");
            put("version", "4.0.3");
            put("memory", "5G");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters2),
                "Provided parameter values are not allowed");

        // try to "start" the app with not-allowed parameters
        Map<String, String> providedParameters3 = new HashMap<String, String>() {{
            put("environment", "biogrid_r");
            put("version", "4.0.3");
            put("memory", "25G");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters3),
                "Provided parameter values are not allowed");
    }

    @Test
    public void testWithAccessControlWithGroupMembership() throws InvalidParametersException {
        when(authBackend.hasAuthorization()).thenReturn(true);

        ProxySpec spec = proxyService.getProxySpec("with-access-control");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("thomas");
        when(userService.isMember(auth, "breeding")).thenReturn(true);

        AllowedParametersForUser allowedParametersForUser = parametersService.calculateAllowedParametersForUser(auth, spec, null);

        Assertions.assertEquals(11, allowedParametersForUser.getAllowedCombinations().size());
        Assertions.assertEquals(
                Arrays.asList(
                        "base_r",
                        "breeding_r",
                        "biogrid_r"
                ),
                allowedParametersForUser.getValues().get("environment"));
        Assertions.assertEquals(
                Arrays.asList(
                         "3.0.6",
                         "4.0.5",
                         "4.1.3",
                         "4.0.3"
                ),
                allowedParametersForUser.getValues().get("version"));
        Assertions.assertEquals(
                Arrays.asList(
                        "2G",
                        "4G",
                        "8G",
                        "5G"
                ),
                allowedParametersForUser.getValues().get("memory"));

        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 1)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(4, 1, 1)));

        // try to "start" the app with correct parameters
        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("environment", "breeding_r");
            put("version", "4.0.3");
            put("memory", "5G");
        }};

        Assertions.assertTrue(parametersService.parseAndValidateRequest(auth, spec, providedParameters).isPresent());

        // try to "start" the app with not-allowed parameters
        Map<String, String> providedParameters3 = new HashMap<String, String>() {{
            put("environment", "biogrid_r");
            put("version", "4.0.3");
            put("memory", "25G");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters3),
                "Provided parameter values are not allowed");
    }

    @Test
    public void testWithAccessControlWithAccessExpression() throws InvalidParametersException {
        when(authBackend.hasAuthorization()).thenReturn(true);

        ProxySpec spec = proxyService.getProxySpec("with-access-control");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("thomas");
        when(auth.getAuthorities()).thenReturn((Collection) Collections.singletonList(new SimpleGrantedAuthority("ROLE_DEV")));

        AllowedParametersForUser allowedParametersForUser = parametersService.calculateAllowedParametersForUser(auth, spec, null);

        Assertions.assertEquals(11, allowedParametersForUser.getAllowedCombinations().size());
        Assertions.assertEquals(
                Arrays.asList(
                        "base_r",
                        "biogrid_r"
                ),
                allowedParametersForUser.getValues().get("environment"));
        Assertions.assertEquals(
                Arrays.asList(
                        "3.0.6",
                        "4.0.5",
                        "4.1.3",
                        "4.1.13",
                        "4.0.3"
                ),
                allowedParametersForUser.getValues().get("version"));
        Assertions.assertEquals(
                Arrays.asList(
                        "2G",
                        "4G",
                        "8G"
                ),
                allowedParametersForUser.getValues().get("memory"));

        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 1)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(4, 1, 1)));

        // try to "start" the app with correct parameters
        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("environment", "biogrid_r");
            put("version", "4.1.13");
            put("memory", "8G");
        }};

        Assertions.assertTrue(parametersService.parseAndValidateRequest(auth, spec, providedParameters).isPresent());

        // try to "start" the app with not-allowed parameters
        Map<String, String> providedParameters2 = new HashMap<String, String>() {{
            put("environment", "breeding_r");
            put("version", "4.0.3");
            put("memory", "5G");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters2),
                "Provided parameter values are not allowed");

        // try to "start" the app with not-allowed parameters
        Map<String, String> providedParameters3 = new HashMap<String, String>() {{
            put("environment", "biogrid_r");
            put("version", "4.0.3");
            put("memory", "25G");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters3),
                "Provided parameter values are not allowed");
    }

    @Test
    public void testWithAccessControlWithAccessUsers() throws InvalidParametersException {
        when(authBackend.hasAuthorization()).thenReturn(true);

        ProxySpec spec = proxyService.getProxySpec("with-access-control");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("jeff");

        AllowedParametersForUser allowedParametersForUser = parametersService.calculateAllowedParametersForUser(auth, spec, null);

        Assertions.assertEquals(11, allowedParametersForUser.getAllowedCombinations().size());
        Assertions.assertEquals(
                Arrays.asList(
                        "base_r",
                        "biogrid_r"
                ),
                allowedParametersForUser.getValues().get("environment"));
        Assertions.assertEquals(
                Arrays.asList(
                        "3.0.6",
                        "4.0.5",
                        "4.1.3",
                        "4.0.3"
                ),
                allowedParametersForUser.getValues().get("version"));
        Assertions.assertEquals(
                Arrays.asList(
                        "2G",
                        "4G",
                        "8G",
                        "25G"
                ),
                allowedParametersForUser.getValues().get("memory"));

        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 1)));
        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(2, 4, 4)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 4)));

        // try to "start" the app with correct parameters
        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("environment", "biogrid_r");
            put("version", "4.0.3");
            put("memory", "25G");
        }};

        Assertions.assertTrue(parametersService.parseAndValidateRequest(auth, spec, providedParameters).isPresent());

        // try to "start" the app with not-allowed parameters
        Map<String, String> providedParameters2 = new HashMap<String, String>() {{
            put("environment", "breeding_r");
            put("version", "4.0.3");
            put("memory", "5G");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters2),
                "Provided parameter values are not allowed");

        // try to "start" the app with not-allowed parameters
        Map<String, String> providedParameters3 = new HashMap<String, String>() {{
            put("environment", "biogrid_r");
            put("version", "4.1.13");
            put("memory", "8G");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters3),
                "Provided parameter values are not allowed");
    }

}
