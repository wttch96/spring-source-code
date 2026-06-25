package org.springframework.wttch;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

class SpringWttchTests {

    @Test
    void loadEmptyContext() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EmptyConfig.class);
            ctx.refresh();
            System.out.println(ctx.getBean(EmptyConfig.class));
            assertThat(ctx).isNotNull();
        }
    }

    @Configuration
    static class EmptyConfig {
    }
}

