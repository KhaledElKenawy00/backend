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
package com.virtualoffice.chat_service.service;

import com.virtualoffice.chat_service.dto.request.SendMessageRequest;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;

import java.util.List;

public interface MessageService {

    MessageResponse sendMessage(String channelId, SendMessageRequest request, Integer senderId, String senderRole);

    PaginatedResponse<MessageResponse> getChannelMessages(String channelId, int page, int limit);

    List<MessageResponse> getChannelMessagesBefore(String channelId, String beforeId, int limit);

    List<MessageResponse> getChannelMessagesAfter(String channelId, String afterId, int limit);

    PaginatedResponse<MessageResponse> getThreadMessages(String threadId, int page, int limit);

    List<MessageResponse> getThreadMessagesBefore(String threadId, String beforeId, int limit);

    List<MessageResponse> getThreadMessagesAfter(String threadId, String afterId, int limit);

    MessageResponse editMessage(String messageId, String newContent, Integer requestingUserId, String requestingUserRole);

    // returns null when the message was already deleted (caller should skip the broadcast)
    MessageResponse deleteMessage(String messageId, Integer requestingUserId, String requestingUserRole);
}
