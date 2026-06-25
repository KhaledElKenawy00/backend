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

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Outbound RabbitMQ wiring for workspace → chat-service channel events (INTEGRATION.md §5.1).
 * Only the exchange is declared here; the queue/binding live in the consumer (chat-service).
 * Declaring the JSON converter bean lets Spring Boot wire it into the auto-configured
 * {@code RabbitTemplate}.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange workspaceExchange(@Value("${workspace.events.exchange}") String exchange) {
        return new DirectExchange(exchange);
    }

    @Bean
    public Jackson2JsonMessageConverter workspaceMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
