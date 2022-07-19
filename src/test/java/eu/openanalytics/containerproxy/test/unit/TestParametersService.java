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
package eu.openanalytics.containerproxy.test.unit;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.containerproxy.model.runtime.ProvidedParameters;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ParametersService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.test.proxy.PropertyOverrideContextInitializer;
import eu.openanalytics.containerproxy.test.proxy.TestIntegrationOnKube;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {TestIntegrationOnKube.TestConfiguration.class, ContainerProxyApplication.class})
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
@ActiveProfiles("parameters")
public class TestParametersService {

    @Inject
    private ProxyService proxyService;

    @Inject
    private ParametersService parametersService;

    private Authentication auth = mock(Authentication.class);

    @Test
    public void testBigParameters() {
        ProxySpec spec = proxyService.getProxySpec("big-parameters");
        AllowedParametersForUser allowedParametersForUser = parametersService.calculateAllowedParametersForUser(auth, spec);

        Assertions.assertEquals(5200, allowedParametersForUser.getAllowedCombinations().size());

        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 1, 1)));

        // values are 1-indexed, so 0 may not be present
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(0, 0, 0, 0)));

        // the 7th option of parameter3 may only occur with option 2 of parameter4
        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 7, 2)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 7, 1)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 7, 3)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 7, 4)));

        // the 8th option of parameter3 may only occur with option 1 of parameter4
        Assertions.assertTrue(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 8, 1)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 8, 2)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 8, 3)));
        Assertions.assertFalse(allowedParametersForUser.getAllowedCombinations().contains(Arrays.asList(1, 1, 8, 4)));

        Assertions.assertEquals(
                new HashSet<>(Arrays.asList("parameter1", "parameter2", "parameter3", "parameter4")),
                allowedParametersForUser.getValues().keySet());

        Assertions.assertEquals(
                Arrays.asList(
                        "The letter A",
                        "B",
                        "C",
                        "D",
                        "E",
                        "F",
                        "G",
                        "H",
                        "I",
                        "J"
                ),
                allowedParametersForUser.getValues().get("parameter1"));

        Assertions.assertEquals(
                Arrays.asList(
                        "The number 1",
                        "2",
                        "3",
                        "4",
                        "5",
                        "6",
                        "7",
                        "8",
                        "9",
                        "10",
                        "11",
                        "12",
                        "13",
                        "14",
                        "15",
                        "16",
                        "17",
                        "18",
                        "19",
                        "The number 20"
                ),
                allowedParametersForUser.getValues().get("parameter2"));

        Assertions.assertEquals(
                Arrays.asList(
                        "Foo",
                        "Bar",
                        "foobar",
                        "barfoo",
                        "bazz",
                        "fozz",
                        "foobarfoo",
                        "barfoobar"
                ),
                allowedParametersForUser.getValues().get("parameter3"));

        Assertions.assertEquals(
                Arrays.asList(
                        "YES",
                        "NO",
                        "maybe",
                        "well"
                ),
                allowedParametersForUser.getValues().get("parameter4"));
    }

    private ProvidedParameters testAllowedValue(ProxySpec spec, String parameter1, String parameter2, String parameter3, String parameter4) throws InvalidParametersException {
        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("parameter1", parameter1);
            put("parameter2", parameter2);
            put("parameter3", parameter3);
            put("parameter4", parameter4);
        }};

        Optional<ProvidedParameters> res = parametersService.parseAndValidateRequest(auth, spec, providedParameters);
        Assertions.assertTrue(res.isPresent());
        return res.get();
    }

    private void testNotAllowedValue(ProxySpec spec, String parameter1, String parameter2, String parameter3, String parameter4) {
        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("parameter1", parameter1);
            put("parameter2", parameter2);
            put("parameter3", parameter3);
            put("parameter4", parameter4);
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters),
                "Provided parameter values are not allowed");
    }

    @Test
    public void testParseAndValidateRequest() throws InvalidParametersException {
        // test that all allowed values are allowed by the parseAndValidateRequest function
        ProxySpec spec = proxyService.getProxySpec("big-parameters");

        // make sure that using the backend values is not allowed
        testNotAllowedValue(spec, "A", "1", "foo", "yes");
        testNotAllowedValue(spec, "A", "2", "foo", "yes");
        testNotAllowedValue(spec, "A", "1", "foobarfoo", "no");
        testNotAllowedValue(spec, "A", "1", "barfoobar", "yes");

        ProvidedParameters res1 = testAllowedValue(spec, "The letter A", "The number 1", "Foo", "YES");
        Assertions.assertEquals("A", res1.getValue("parameter1"));
        Assertions.assertEquals("1", res1.getValue("parameter2"));
        Assertions.assertEquals("foo", res1.getValue("parameter3"));
        Assertions.assertEquals("yes", res1.getValue("parameter4"));

        ProvidedParameters res2 = testAllowedValue(spec, "The letter A", "2", "Foo", "YES");
        Assertions.assertEquals("A", res2.getValue("parameter1"));
        Assertions.assertEquals("2", res2.getValue("parameter2"));
        Assertions.assertEquals("foo", res2.getValue("parameter3"));
        Assertions.assertEquals("yes", res2.getValue("parameter4"));

        ProvidedParameters res3 = testAllowedValue(spec, "The letter A", "The number 1", "foobarfoo", "NO");
        Assertions.assertEquals("A", res3.getValue("parameter1"));
        Assertions.assertEquals("1", res3.getValue("parameter2"));
        Assertions.assertEquals("foobarfoo", res3.getValue("parameter3"));
        Assertions.assertEquals("no", res3.getValue("parameter4"));

        ProvidedParameters res4 = testAllowedValue(spec, "The letter A", "The number 1", "barfoobar", "YES");
        Assertions.assertEquals("A", res4.getValue("parameter1"));
        Assertions.assertEquals("1", res4.getValue("parameter2"));
        Assertions.assertEquals("barfoobar", res4.getValue("parameter3"));
        Assertions.assertEquals("yes", res4.getValue("parameter4"));

        // test that allowed values but invalid combinations are not allowed
        testNotAllowedValue(spec, "The letter A", "The number 1", "foobarfoo", "YES");
        testNotAllowedValue(spec, "The letter A", "The number 1", "foobarfoo", "maybe");
        testNotAllowedValue(spec, "The letter A", "The number 1", "foobarfoo", "well");
        testNotAllowedValue(spec, "B", "2", "foobarfoo", "YES");
        testNotAllowedValue(spec, "F", "6", "foobarfoo", "maybe");
        testNotAllowedValue(spec, "G", "3", "foobarfoo", "well");
        testNotAllowedValue(spec, "The letter A", "The number 1", "barfoobar", "NO");
        testNotAllowedValue(spec, "The letter A", "The number 1", "barfoobar", "maybe");
        testNotAllowedValue(spec, "The letter A", "The number 1", "barfoobar", "well");
        testNotAllowedValue(spec, "B", "2", "barfoobar", "NO");
        testNotAllowedValue(spec, "F", "6", "barfoobar", "maybe");
        testNotAllowedValue(spec, "G", "3", "barfoobar", "well");

        // test that invalid values are not allowed
        testNotAllowedValue(spec, "ABC", "BLUB", "LBAB", "...");
        testNotAllowedValue(spec, "123", "ABC", "W434$:", "@@");
    }

    @Test
    public void testParseAndValidateRequestNoParameters() throws InvalidParametersException {
        ProxySpec spec = proxyService.getProxySpec("no-parameters");

        Assertions.assertFalse(parametersService.parseAndValidateRequest(auth, spec, new HashMap<>()).isPresent());
    }

    @Test
    public void testInvalidNumberOfParameters() {
        ProxySpec spec = proxyService.getProxySpec("big-parameters");

        // too many parameters
        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("parameter1", "The letter A");
            put("parameter2", "The number 1");
            put("parameter3", "Foo");
            put("parameter4", "NO");
            put("parameter5", "NO");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters),
                "Invalid number of parameters provided");

        // too few parameters
        Map<String, String> providedParameters2 = new HashMap<String, String>() {{
            put("parameter1", "The letter A");
            put("parameter2", "The number 1");
            put("parameter3", "Foo");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters2),
                "Invalid number of parameters provided");
    }

    @Test
    public void testInvalidParameterIds() {
        ProxySpec spec = proxyService.getProxySpec("big-parameters");

        Map<String, String> providedParameters = new HashMap<String, String>() {{
            put("parameter1", "The letter A");
            put("parameter2", "The number 1");
            put("parameter3", "Foo");
            put("parameterXXXX", "NO");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters),
                "Missing value for parameter parameter4");

        Map<String, String> providedParameters2 = new HashMap<String, String>() {{
            put("parameterABC", "The letter A");
            put("parameter#$#$", "The number 1");
            put("parameter3343434", "Foo");
            put("parameterXXXX", "NO");
        }};

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.parseAndValidateRequest(auth, spec, providedParameters2),
                "Missing value for parameter parameter1");
    }

}
