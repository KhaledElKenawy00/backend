package com.virtualoffice.chat_service.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingAspectTest {

    private LoggingAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new LoggingAspect();
    }

    private ProceedingJoinPoint mockJoinPoint(String shortString, Object[] args, Object result) throws Throwable {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.toShortString()).thenReturn(shortString);
        when(jp.getSignature()).thenReturn(sig);
        when(jp.getArgs()).thenReturn(args);
        if (result != null) {
            when(jp.proceed()).thenReturn(result);
        }
        return jp;
    }

    // ── service logging ──

    @Nested
    class ServiceLogging {

        @Test
        void shouldReturnResultFromProceed() throws Throwable {
            ProceedingJoinPoint jp = mockJoinPoint("ChannelServiceImpl.getChannel(..)", new Object[]{"ch1"}, "result");

            Object result = aspect.logServiceCall(jp);

            assertThat(result).isEqualTo("result");
        }

        @Test
        void shouldPropagateException() throws Throwable {
            ProceedingJoinPoint jp = mockJoinPoint("ChannelServiceImpl.getChannel(..)", new Object[]{}, null);
            when(jp.proceed()).thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> aspect.logServiceCall(jp))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("boom");
        }

        @Test
        void shouldHandleNullArgs() throws Throwable {
            ProceedingJoinPoint jp = mockJoinPoint("SomeService.doSomething(..)", null, "ok");

            Object result = aspect.logServiceCall(jp);

            assertThat(result).isEqualTo("ok");
        }

        @Test
        void shouldHandleEmptyArgs() throws Throwable {
            ProceedingJoinPoint jp = mockJoinPoint("SomeService.doSomething(..)", new Object[]{}, "ok");

            Object result = aspect.logServiceCall(jp);

            assertThat(result).isEqualTo("ok");
        }

        @Test
        void shouldHandleNullArgInArray() throws Throwable {
            ProceedingJoinPoint jp = mockJoinPoint("SomeService.doSomething(..)", new Object[]{null, "hello"}, "ok");

            Object result = aspect.logServiceCall(jp);

            assertThat(result).isEqualTo("ok");
        }
    }

    // ── controller logging ──

    @Nested
    class ControllerLogging {

        @Test
        void shouldReturnResult() throws Throwable {
            ProceedingJoinPoint jp = mockJoinPoint("ChannelController.getChannel(..)", new Object[]{"id1"}, "response");

            Object result = aspect.logControllerCall(jp);

            assertThat(result).isEqualTo("response");
        }

        @Test
        void shouldPropagateControllerException() throws Throwable {
            ProceedingJoinPoint jp = mockJoinPoint("ChannelController.getChannel(..)", new Object[]{}, null);
            when(jp.proceed()).thenThrow(new IllegalArgumentException("bad id"));

            assertThatThrownBy(() -> aspect.logControllerCall(jp))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bad id");
        }
    }
}
