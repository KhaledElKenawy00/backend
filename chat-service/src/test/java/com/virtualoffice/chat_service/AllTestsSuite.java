package com.virtualoffice.chat_service;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

// runs every unit test in the project from a single entry point
// excludes the Spring integration test that needs a running MongoDB/Redis
@Suite
@SelectPackages("com.virtualoffice.chat_service")
@ExcludeClassNamePatterns(".*ChatServiceApplicationTests")
public class AllTestsSuite {
}
