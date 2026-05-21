package com.virtualoffice.notifications;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

// Single entry point for running every unit test in the service. In IntelliJ:
// right-click this class -> Run. From the CLI: `./mvnw test` runs the same
// set; the suite is for IDE convenience and for explicit "run them all"
// semantics independent of Surefire's defaults.
@Suite
@SuiteDisplayName("Notifications Service - All Tests")
@SelectPackages("com.virtualoffice.notifications")
@IncludeClassNamePatterns(".*Test$")
public class AllTestsSuite {
}
