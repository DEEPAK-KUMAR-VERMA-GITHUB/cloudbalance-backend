package com.cloudkeeper.cloudbalance_backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "CloudBalance API",
                version = "1.0",
                description = "Cloud cost optimization and user management platform",
                contact = @Contact(
                        name = "CloudBalance Team",
                        email = "support@cloudbalance.com"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080/api", description = "Local Development"),
                @Server(url = "http://api.cloudbalance.com/api", description = "Production")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
