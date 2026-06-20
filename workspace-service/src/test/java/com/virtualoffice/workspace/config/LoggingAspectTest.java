/*
 * Copyright (c) 2025 My Virtual Office
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package com.virtualoffice.workspace.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingAspectTest {

    private final LoggingAspect aspect = new LoggingAspect();

    private ProceedingJoinPoint jp;

    @BeforeEach
    void setUp() {
        jp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.toShortString()).thenReturn("Service.method()");
        when(jp.getSignature()).thenReturn(sig);
    }

    @Test
    void serviceCallLogsSuccessAndSummarizesArgs() throws Throwable {
        when(jp.getArgs()).thenReturn(new Object[]{"a", null, 1});
        when(jp.proceed()).thenReturn("ok");
        assertThat(aspect.logServiceCall(jp)).isEqualTo("ok");
    }

    @Test
    void serviceCallWithNoArgs() throws Throwable {
        when(jp.getArgs()).thenReturn(new Object[]{});
        when(jp.proceed()).thenReturn("ok");
        assertThat(aspect.logServiceCall(jp)).isEqualTo("ok");
    }

    @Test
    void serviceCallLogsResponseStatusExceptionQuietly() throws Throwable {
        when(jp.getArgs()).thenReturn(null);
        when(jp.proceed()).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "nope"));
        assertThatThrownBy(() -> aspect.logServiceCall(jp)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void serviceCallLogsUnexpectedException() throws Throwable {
        when(jp.getArgs()).thenReturn(new Object[]{});
        when(jp.proceed()).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> aspect.logServiceCall(jp)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void controllerCallLogsSuccess() throws Throwable {
        when(jp.proceed()).thenReturn("ok");
        assertThat(aspect.logControllerCall(jp)).isEqualTo("ok");
    }

    @Test
    void controllerCallLogsResponseStatusException() throws Throwable {
        when(jp.proceed()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no"));
        assertThatThrownBy(() -> aspect.logControllerCall(jp)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void controllerCallLogsUnexpectedException() throws Throwable {
        when(jp.proceed()).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> aspect.logControllerCall(jp)).isInstanceOf(IllegalStateException.class);
    }
}
