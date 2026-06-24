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
package com.virtualoffice.chat_service.messaging;

import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end contract test for the workspace-service → chat-service channel-provisioning path
 * (INTEGRATION.md §5.1) over a <em>real</em> RabbitMQ broker and MongoDB (Testcontainers).
 *
 * <p>It publishes the exact JSON bytes workspace-service emits (no {@code __TypeId__} header,
 * {@code application/json} content type) onto {@code workspace.exchange} and asserts the live
 * {@link WorkspaceChannelListener} materialises and syncs the canonical channel in Mongo. This
 * proves the wire contract, the queue/exchange binding, and the listener effects together.
 *
 * <p>{@code @ServiceConnection} wires each container's connection into the context, so the app's
 * Mongo/RabbitMQ/Redis auto-configuration points at the containers rather than localhost.
 */
@SpringBootTest
@Testcontainers
class WorkspaceChannelEventIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"));

    @Container
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ChannelRepository channelRepository;

    @Value("${workspace.exchange}")
    private String exchange;

    @Value("${workspace.channel.routing-key}")
    private String routingKey;

    private static final int WORKSPACE_ID = 42;

    @BeforeEach
    void clean() {
        channelRepository.deleteAll();
    }

    @Test
    void provisionsAndSyncsCanonicalChannelFromPublishedEvents() {
        // 1. CREATE — workspace-service emits this at workspace creation, owner as first member.
        publish("""
                {"eventId":"e-create","type":"WORKSPACE_CHANNEL_CREATE",\
                "workspaceId":42,"name":"Acme","userId":7,"members":[7]}""");

        Channel channel = awaitCanonical();
        assertThat(channel.getType()).isEqualTo(ChannelType.GROUP);
        assertThat(channel.getCanonical()).isTrue();
        assertThat(channel.getName()).isEqualTo("Acme");
        assertThat(channel.getCreatedBy()).isEqualTo(7);
        assertThat(channel.getMembers()).containsExactly(7);

        // 2. ADD_MEMBER — an invitation was accepted.
        publish("""
                {"eventId":"e-add","type":"WORKSPACE_CHANNEL_ADD_MEMBER",\
                "workspaceId":42,"userId":99}""");
        awaitUntil(() -> canonical().getMembers().contains(99));

        // 3. REMOVE_MEMBER — the member was removed from the workspace.
        publish("""
                {"eventId":"e-remove","type":"WORKSPACE_CHANNEL_REMOVE_MEMBER",\
                "workspaceId":42,"userId":99}""");
        awaitUntil(() -> !canonical().getMembers().contains(99));
        assertThat(canonical().getMembers()).containsExactly(7);
    }

    @Test
    void duplicateCreateDoesNotProvisionASecondChannel() {
        String create = """
                {"eventId":"e","type":"WORKSPACE_CHANNEL_CREATE",\
                "workspaceId":42,"name":"Acme","userId":7,"members":[7]}""";
        publish(create);
        awaitCanonical();
        publish(create);
        // Redelivery must be idempotent — exactly one channel ever exists for the workspace.
        awaitUntil(() -> channelRepository.count() == 1);
        sleep(1500); // let any erroneous second insert land, then re-assert
        assertThat(channelRepository.count()).isEqualTo(1);
    }

    private void publish(String json) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(exchange, routingKey, new Message(json.getBytes(StandardCharsets.UTF_8), props));
    }

    private Channel canonical() {
        return channelRepository.findCanonicalByWorkspaceId(WORKSPACE_ID).orElse(null);
    }

    private Channel awaitCanonical() {
        awaitUntil(() -> canonical() != null);
        return canonical();
    }

    /** Polls a condition for up to 15s, failing the test if it never becomes true. */
    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // condition not satisfiable yet (e.g. channel still absent) — keep polling
            }
            sleep(150);
        }
        throw new AssertionError("condition not met within 15s");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
