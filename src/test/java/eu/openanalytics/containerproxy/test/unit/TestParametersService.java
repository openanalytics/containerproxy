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
import org.apache.commons.lang3.tuple.Pair;
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
                        Pair.of(1, "A"),
                        Pair.of(2, "B"),
                        Pair.of(3, "C"),
                        Pair.of(4, "D"),
                        Pair.of(5, "E"),
                        Pair.of(6, "F"),
                        Pair.of(7, "G"),
                        Pair.of(8, "H"),
                        Pair.of(9, "I"),
                        Pair.of(10, "J")
                ),
                allowedParametersForUser.getValues().get("parameter1"));

        Assertions.assertEquals(
                Arrays.asList(
                        Pair.of(1, "1"),
                        Pair.of(2, "2"),
                        Pair.of(3, "3"),
                        Pair.of(4, "4"),
                        Pair.of(5, "5"),
                        Pair.of(6, "6"),
                        Pair.of(7, "7"),
                        Pair.of(8, "8"),
                        Pair.of(9, "9"),
                        Pair.of(10, "10"),
                        Pair.of(11, "11"),
                        Pair.of(12, "12"),
                        Pair.of(13, "13"),
                        Pair.of(14, "14"),
                        Pair.of(15, "15"),
                        Pair.of(16, "16"),
                        Pair.of(17, "17"),
                        Pair.of(18, "18"),
                        Pair.of(19, "19"),
                        Pair.of(20, "20")
                ),
                allowedParametersForUser.getValues().get("parameter2"));

        Assertions.assertEquals(
                Arrays.asList(
                        Pair.of(1, "foo"),
                        Pair.of(2, "bar"),
                        Pair.of(3, "foobar"),
                        Pair.of(4, "barfoo"),
                        Pair.of(5, "bazz"),
                        Pair.of(6, "fozz"),
                        Pair.of(7, "foobarfoo"),
                        Pair.of(8, "barfoobar")
                ),
                allowedParametersForUser.getValues().get("parameter3"));

        Assertions.assertEquals(
                Arrays.asList(
                        Pair.of(1, "yes"),
                        Pair.of(2, "no"),
                        Pair.of(3, "maybe"),
                        Pair.of(4, "well")
                ),
                allowedParametersForUser.getValues().get("parameter4"));
    }

    private void testAllowedValue(ProxySpec spec, String parameter1, String parameter2, String parameter3, String parameter4) throws InvalidParametersException {
        ProvidedParameters providedParameters = new ProvidedParameters(new HashMap<String, String>() {{
            put("parameter1", parameter1);
            put("parameter2", parameter2);
            put("parameter3", parameter3);
            put("parameter4", parameter4);
        }});

        Assertions.assertTrue(parametersService.validateRequest(auth, spec, providedParameters));
    }

    private void testNotAllowedValue(ProxySpec spec, String parameter1, String parameter2, String parameter3, String parameter4) {
        ProvidedParameters providedParameters = new ProvidedParameters(new HashMap<String, String>() {{
            put("parameter1", parameter1);
            put("parameter2", parameter2);
            put("parameter3", parameter3);
            put("parameter4", parameter4);
        }});

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.validateRequest(auth, spec, providedParameters),
                "Provided parameter values are not allowed");
    }

    @Test
    public void testParseAndValidateRequest() throws InvalidParametersException {
        // test that all allowed values are allowed by the parseAndValidateRequest function
        ProxySpec spec = proxyService.getProxySpec("big-parameters");

        testAllowedValue(spec, "A", "1", "foo", "yes");
        testAllowedValue(spec, "A", "2", "foo", "yes");
        testAllowedValue(spec, "A", "1", "foobarfoo", "no");
        testAllowedValue(spec, "A", "1", "barfoobar", "yes");

        // test that allowed values but invalid combinations are not allowed
        testNotAllowedValue(spec, "A", "1", "foobarfoo", "yes");
        testNotAllowedValue(spec, "A", "1", "foobarfoo", "maybe");
        testNotAllowedValue(spec, "A", "1", "foobarfoo", "well");
        testNotAllowedValue(spec, "B", "2", "foobarfoo", "yes");
        testNotAllowedValue(spec, "F", "6", "foobarfoo", "maybe");
        testNotAllowedValue(spec, "G", "3", "foobarfoo", "well");
        testNotAllowedValue(spec, "A", "1", "barfoobar", "no");
        testNotAllowedValue(spec, "A", "1", "barfoobar", "maybe");
        testNotAllowedValue(spec, "A", "1", "barfoobar", "well");
        testNotAllowedValue(spec, "B", "2", "barfoobar", "no");
        testNotAllowedValue(spec, "F", "6", "barfoobar", "maybe");
        testNotAllowedValue(spec, "G", "3", "barfoobar", "well");

        // test that invalid values are not allowed
        testNotAllowedValue(spec, "ABC", "BLUB", "LBAB", "...");
        testNotAllowedValue(spec, "123", "ABC", "W434$:", "@@");
    }

    @Test
    public void testParseAndValidateRequestNoParameters() throws InvalidParametersException {
        ProxySpec spec = proxyService.getProxySpec("no-parameters");

        ProvidedParameters providedParameters = new ProvidedParameters();
        Assertions.assertFalse(parametersService.validateRequest(auth, spec, providedParameters));
    }

    @Test
    public void testInvalidNumberOfParameters() {
        ProxySpec spec = proxyService.getProxySpec("big-parameters");

        // too many parameters
        ProvidedParameters providedParameters = new ProvidedParameters(new HashMap<String, String>() {{
            put("parameter1", "A");
            put("parameter2", "1");
            put("parameter3", "foo");
            put("parameter4", "no");
            put("parameter5", "no");
        }});

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.validateRequest(auth, spec, providedParameters),
                "Invalid number of parameters provided");

        // too few parameters
        ProvidedParameters providedParameters2 = new ProvidedParameters(new HashMap<String, String>() {{
            put("parameter1", "A");
            put("parameter2", "1");
            put("parameter3", "foo");
        }});

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.validateRequest(auth, spec, providedParameters2),
                "Invalid number of parameters provided");
    }

    @Test
    public void testInvalidParameterIds() {
        ProxySpec spec = proxyService.getProxySpec("big-parameters");

        ProvidedParameters providedParameters = new ProvidedParameters(new HashMap<String, String>() {{
            put("parameter1", "A");
            put("parameter2", "1");
            put("parameter3", "foo");
            put("parameterXXXX", "no");
        }});

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.validateRequest(auth, spec, providedParameters),
                "Missing value for parameter parameter4");

        ProvidedParameters providedParameters2 = new ProvidedParameters(new HashMap<String, String>() {{
            put("parameterABC", "A");
            put("parameter#$#$", "1");
            put("parameter3343434", "foo");
            put("parameterXXXX", "no");
        }});

        Assertions.assertThrows(InvalidParametersException.class,
                () -> parametersService.validateRequest(auth, spec, providedParameters2),
                "Missing value for parameter parameter1");
    }

}
