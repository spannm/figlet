package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FigletException}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletExceptionTest {

    @Test
    void constructor_withMessage_shouldSetMessage() {
        assertThat(new FigletException("boom"))
            .hasMessage("boom")
            .hasNoCause();
    }

    @Test
    void constructor_withMessageAndCause_shouldSetBoth() {
        Throwable cause = new IllegalStateException("root cause");

        assertThat(new FigletException("boom", cause))
            .hasMessage("boom")
            .hasCause(cause);
    }

    @Test
    void figletException_shouldBeRuntimeException() {
        assertThat(new FigletException("x")).isInstanceOf(RuntimeException.class);
    }

}

