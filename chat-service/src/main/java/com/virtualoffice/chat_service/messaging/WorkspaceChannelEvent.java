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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Consumer-side mirror of workspace-service's {@code WorkspaceChannelEvent} wire contract
 * (INTEGRATION.md §5.1). Field names and enum constants must match the producer; the producer
 * sends {@code Long} ids which deserialize into {@code Integer} here (the chat-service id type).
 * {@code members} is populated only on CREATE; {@code userId} carries the add/remove subject.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceChannelEvent {

    private String eventId;
    private WorkspaceChannelEventType type;
    private Integer workspaceId;
    private String name;
    private Integer userId;
    private List<Integer> members;
}
