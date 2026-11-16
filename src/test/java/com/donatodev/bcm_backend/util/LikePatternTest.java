package com.donatodev.bcm_backend.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LikePatternTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "NRT-119|NRT-119",
            "%|!%",
            "_|!_",
            "!|!!",
            "50%_off!|50!%!_off!!",
            "' OR '1'='1|' OR '1'='1"})
    void wildcardsAndTheEscapeMatchOnlyThemselves(String typed, String bound) {
        assertThat(LikePattern.literal(typed)).isEqualTo(bound);
    }

    @Test
    void nullStaysNull() {
        assertThat(LikePattern.literal(null)).isNull();
    }

    @Test
    void cannotBeInstantiated() throws Exception {
        Constructor<LikePattern> constructor = LikePattern.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance).isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
