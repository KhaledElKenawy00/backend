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

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${room.channel.queue}")
    private String roomChannelQueue;

    @Value("${room.exchange}")
    private String roomExchange;

    @Value("${room.channel.routing-key}")
    private String roomChannelRoutingKey;

    @Value("${workspace.channel.queue}")
    private String workspaceChannelQueue;

    @Value("${workspace.exchange}")
    private String workspaceExchange;

    @Value("${workspace.channel.routing-key}")
    private String workspaceChannelRoutingKey;

    @Bean
    public Queue roomChannelQueue() {
        return QueueBuilder.durable(roomChannelQueue).build();
    }

    @Bean
    public DirectExchange roomExchange() {
        return new DirectExchange(roomExchange);
    }

    @Bean
    public Binding roomChannelBinding() {
        return BindingBuilder.bind(roomChannelQueue()).to(roomExchange()).with(roomChannelRoutingKey);
    }

    @Bean
    public Queue workspaceChannelQueue() {
        return QueueBuilder.durable(workspaceChannelQueue).build();
    }

    @Bean
    public DirectExchange workspaceExchange() {
        return new DirectExchange(workspaceExchange);
    }

    @Bean
    public Binding workspaceChannelBinding() {
        return BindingBuilder.bind(workspaceChannelQueue()).to(workspaceExchange()).with(workspaceChannelRoutingKey);
    }

    @Bean
    public JacksonJsonMessageConverter roomMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
