package eu.openanalytics.containerproxy.test.unit;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ParametersService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.test.proxy.PropertyOverrideContextInitializer;
import eu.openanalytics.containerproxy.test.proxy.TestIntegrationOnKube;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;

@SpringBootTest(classes = {TestIntegrationOnKube.TestConfiguration.class, ContainerProxyApplication.class})
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
@ActiveProfiles("parameters")
public class TestParametersService {

    @Inject
    private ProxyService proxyService;

    @Inject
    private ParametersService parametersService;

    @Test
    public void testBigParameters() {
        ProxySpec spec = proxyService.getProxySpec("big-parameters");
        AllowedParametersForUser allowedParametersForUser = parametersService.calculateAllowedParametersForUser(spec);

        Assertions.assertEquals(5190, allowedParametersForUser.getAllowedCombinations().size());

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

    // TODO test duplicate values
    // TODO test duplicate parameters
    // TODO test missing parameters


}
