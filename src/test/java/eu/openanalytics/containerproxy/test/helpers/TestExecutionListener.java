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
package eu.openanalytics.containerproxy.test.helpers;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public class TestExecutionListener extends SummaryGeneratingListener {

    public TestExecutionListener() {

    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        super.executionSkipped(testIdentifier, reason);
        if (testIdentifier == null || reason == null || !testIdentifier.isTest()) return;

        System.out.println();
        System.out.printf("\t\t--> Skipping test \"%s\"%n", testIdentifier.getDisplayName());
        System.out.println();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        super.executionStarted(testIdentifier);
        if (testIdentifier == null || !testIdentifier.isTest()) return;

        System.out.println();
        System.out.printf("\t\t--> Started test \"%s\"%n", testIdentifier.getDisplayName());
        System.out.println();
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        super.executionFinished(testIdentifier, testExecutionResult);
        if (testIdentifier == null || testExecutionResult == null || !testIdentifier.isTest()) return;

        System.out.println();
        System.out.printf("\t\t--> Finished test \"%s\": %s%n", testIdentifier.getDisplayName(), testExecutionResult);
        System.out.println();
    }
}
