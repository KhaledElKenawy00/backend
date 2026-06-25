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
package com.virtualoffice.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Browser CORS for the gateway. Without this, a browser SPA (e.g. the SkyOffice client on a
 * different origin/port) cannot call any API: the preflight OPTIONS request carries no Authorization
 * header and would be rejected by {@link com.virtualoffice.gateway.security.AuthGatewayFilter}, and
 * responses would lack {@code Access-Control-Allow-Origin}.
 *
 * <p>This {@link CorsWebFilter} is a WebFilter, so it runs ahead of the gateway's GlobalFilters: it
 * answers preflight requests directly (they never reach the auth filter) and adds the CORS response
 * headers to actual requests. Allowed origins are configurable; the default permits any localhost
 * port for local development. Credentials are allowed, so an exact origin (not {@code *}) is
 * reflected — hence origin <em>patterns</em>.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${gateway.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            List<String> allowedOriginPatterns) {

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(allowedOriginPatterns);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }
}
