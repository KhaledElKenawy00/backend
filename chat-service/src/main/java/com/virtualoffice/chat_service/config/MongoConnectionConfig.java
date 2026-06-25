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
package com.virtualoffice.chat_service.config;

import com.mongodb.ConnectionString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring Boot 4's default PropertiesMongoConnectionDetails does not carry the credentials from
// `spring.data.mongodb.uri` through to the driver, so connecting to an authenticated MongoDB fails
// with "command requires authentication" (the driver never sends a SCRAM handshake). Supplying the
// ConnectionString explicitly — which includes user:password@ and authSource — restores auth.
// Kept separate from MongoConfig (which injects MongoTemplate) to avoid a bean cycle: the template
// depends on this connection-details bean.
@Configuration
public class MongoConnectionConfig {

    @Bean
    public MongoConnectionDetails mongoConnectionDetails(@Value("${spring.data.mongodb.uri}") String uri) {
        ConnectionString connectionString = new ConnectionString(uri);
        return () -> connectionString;
    }
}
