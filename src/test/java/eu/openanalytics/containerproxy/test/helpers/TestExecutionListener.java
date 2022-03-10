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

import com.google.common.collect.Iterables;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.util.Arrays;
import java.util.Objects;

public class TestExecutionListener extends SummaryGeneratingListener {

    public TestExecutionListener() {

    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        super.executionSkipped(testIdentifier, reason);
        if (testIdentifier == null || reason == null || !testIdentifier.isTest()) return;

        System.out.println();
        System.out.printf("\t\t--> Skipping test \"%s\"%n", identifier(testIdentifier));
        System.out.println();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        super.executionStarted(testIdentifier);
        if (testIdentifier == null || !testIdentifier.isTest()) return;

        System.out.println();
        System.out.printf("\t\t--> Started test \"%s\"%n", identifier(testIdentifier));
        System.out.println();
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        super.executionFinished(testIdentifier, testExecutionResult);
        if (testIdentifier == null || testExecutionResult == null || !testIdentifier.isTest()) return;

        System.out.println();
        System.out.printf("\t\t--> Finished test \"%s\": %s%n", identifier(testIdentifier), testExecutionResult);
        System.out.println();
    }

    private String identifier(TestIdentifier testIdentifier) {
        if (!testIdentifier.getSource().isPresent()) {
            return testIdentifier.getDisplayName();
        }
        MethodSource methodSource = (MethodSource) testIdentifier.getSource().get();
        String className = Iterables.getLast(Arrays.asList(methodSource.getClassName().split("\\.")));

        if (!Objects.equals(methodSource.getMethodParameterTypes(), "")) {
            return String.format("%s %s %s", className, methodSource.getMethodName(), testIdentifier.getDisplayName());
        }
        return String.format("%s %s", className, methodSource.getMethodName());
    }
}
