package aicrediflux.token.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aicredifluxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AICrediFlux API")
                        .version("1.0.0")
                        .description("AICrediFlux backend API documentation and test console."))
                .components(new Components()
                        .addSecuritySchemes("aicrediflux", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("aicrediflux")
                                .description("Sa-Token login token header for /api/** endpoints."))
                        .addSecuritySchemes("Authorization", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API Key")
                                .description("Bearer API key for AI relay endpoints such as /v1/**.")));
    }
}