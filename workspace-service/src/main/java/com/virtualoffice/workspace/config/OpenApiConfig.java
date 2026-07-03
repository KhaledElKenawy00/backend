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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    // Gateway-injected identity headers (see UserContext). Exposed as apiKey schemes so the
    // Swagger UI "Authorize" dialog can set them on every request while testing without a gateway.
    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";
    // Shared secret for the server-to-server /api/internal/** endpoints (see InternalAuthFilter).
    private static final String INTERNAL_TOKEN = "X-Internal-Token";

    @Bean
    public OpenAPI workspaceServiceOpenAPI(@Value("${server.port:8087}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("Workspace Service API")
                        .version("v1")
                        .description("""
                                Spatial backbone for the Virtual Office: workspaces, desks, teams, \
                                map objects, invitations, the 2D layout, and the SkyOffice session API.

                                **Auth:** the API gateway validates the JWT and forwards `X-User-Id` \
                                and `X-User-Role`. Set both via the **Authorize** button to test \
                                endpoints without a gateway. Server-to-server `/api/internal/**` \
                                endpoints instead require the `X-Internal-Token` shared secret."""))
                .servers(List.of(new Server().url("http://localhost:" + port).description("Local")))
                .components(new Components()
                        .addSecuritySchemes(USER_ID, headerScheme(USER_ID, "Integer user id (e.g. 42)"))
                        .addSecuritySchemes(USER_ROLE, headerScheme(USER_ROLE, "USER or ADMIN"))
                        .addSecuritySchemes(INTERNAL_TOKEN,
                                headerScheme(INTERNAL_TOKEN, "Shared secret for /api/internal/** only")))
                // Listed globally so the Authorize dialog offers all three; fill the pair you need.
                .addSecurityItem(new SecurityRequirement()
                        .addList(USER_ID).addList(USER_ROLE).addList(INTERNAL_TOKEN));
    }

    private static SecurityScheme headerScheme(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }
}
