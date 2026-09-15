package aicrediflux.token.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class UnavailableRagVectorGatewayTest {
    @Test
    void registersFallbackGatewayWhenNoRealVectorStoreIsConfigured() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(UnavailableRagVectorGateway.class);
            context.refresh();

            RagVectorGateway gateway = context.getBean(RagVectorGateway.class);

            assertThat(gateway).isInstanceOf(UnavailableRagVectorGateway.class);
        }
    }
}