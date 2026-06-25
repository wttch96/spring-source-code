package org.springframework.wttch;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SpringWttchTests {

    @Test
    void loadEmptyContext() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EmptyConfig.class);
            ctx.refresh();
            assertThat(ctx).isNotNull();
        }
    }

    static class EmptyConfig {
    }
}

