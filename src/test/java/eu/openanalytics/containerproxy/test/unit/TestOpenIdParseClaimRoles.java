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

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.test.proxy.PropertyOverrideContextInitializer;
import eu.openanalytics.containerproxy.test.proxy.TestProxyService;
import net.minidev.json.JSONArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SpringBootTest(classes = {TestProxyService.TestConfiguration.class, ContainerProxyApplication.class})
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
public class TestOpenIdParseClaimRoles {

    private final Logger logger = LogManager.getLogger(OpenIDAuthenticationBackend.class);

    @Test
    public void testParseProperJsonArray() {
        JSONArray claimValue = new JSONArray();
        claimValue.add("operators");
        claimValue.add("default-roles-master");
        claimValue.add("uma_authorization");
        claimValue.add("offline_access");

        List<String> result = OpenIDAuthenticationBackend.parseRolesClaim(logger, "realm_access_roles", claimValue);

        Assertions.assertEquals(Arrays.asList("operators", "default-roles-master", "uma_authorization", "offline_access"), result);
    }

    @Test
    public void testParseList() {
        List<String> claimValue = new ArrayList<>();
        claimValue.add("operators");
        claimValue.add("default-roles-master");
        claimValue.add("uma_authorization");
        claimValue.add("offline_access");

        List<String> result = OpenIDAuthenticationBackend.parseRolesClaim(logger, "realm_access_roles", claimValue);

        Assertions.assertEquals(Arrays.asList("operators", "default-roles-master", "uma_authorization", "offline_access"), result);
    }

    @Test
    public void testParseProperJsonArrayAsString() {
        String claimValue = "[\"operators\",\"default-roles-master\",\"uma_authorization\",\"offline_access\"]";

        List<String> result = OpenIDAuthenticationBackend.parseRolesClaim(logger, "realm_access_roles", claimValue);

        Assertions.assertEquals(Arrays.asList("operators", "default-roles-master", "uma_authorization", "offline_access"), result);
    }

    @Test
    public void testParseNonStandardJsonArrayAsString() {
        String claimValue = "[operators,default-roles-master,uma_authorization,offline_access]";

        List<String> result = OpenIDAuthenticationBackend.parseRolesClaim(logger, "realm_access_roles", claimValue);

        Assertions.assertEquals(Arrays.asList("operators", "default-roles-master", "uma_authorization", "offline_access"), result);
    }

    @Test
    public void testParseNonStandardJsonArrayAsString2() {
        String claimValue = "[operators, default-roles-master,uma_authorization, offline_access ]";

        List<String> result = OpenIDAuthenticationBackend.parseRolesClaim(logger, "realm_access_roles", claimValue);

        Assertions.assertEquals(Arrays.asList("operators", "default-roles-master", "uma_authorization", "offline_access"), result);
    }

    @Test
    public void testParseNonStandardJsonArrayAsString3() {
        String claimValue = "[operators, default-roles-master,uma_authorization, \"offline_access\"]";

        List<String> result = OpenIDAuthenticationBackend.parseRolesClaim(logger, "realm_access_roles", claimValue);

        Assertions.assertEquals(Arrays.asList("operators", "default-roles-master", "uma_authorization", "offline_access"), result);
    }

    @Test
    public void testParseNonStandardJsonArrayAsString4() {
        String claimValue = "[operators, default-roles-master,uma_authorization, 'offline_access']";

        List<String> result = OpenIDAuthenticationBackend.parseRolesClaim(logger, "realm_access_roles", claimValue);

        Assertions.assertEquals(Arrays.asList("operators", "default-roles-master", "uma_authorization", "offline_access"), result);
    }

}
