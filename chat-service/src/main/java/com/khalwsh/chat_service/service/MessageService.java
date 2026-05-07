package com.khalwsh.chat_service.service;

import com.khalwsh.chat_service.dto.request.SendMessageRequest;
import com.khalwsh.chat_service.dto.response.MessageResponse;
import com.khalwsh.chat_service.dto.response.PaginatedResponse;

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
