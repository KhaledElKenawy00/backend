package com.virtualoffice.chat_service.service;

import com.virtualoffice.chat_service.dto.response.UnreadCountResponse;

public interface ReadReceiptService {

    void markAsRead(String channelId, Integer userId, String lastReadMessageId);

    UnreadCountResponse getUnreadCount(String channelId, Integer userId);

    void markThreadAsRead(String threadId, Integer userId, String lastReadMessageId);

    UnreadCountResponse getThreadUnreadCount(String threadId, Integer userId);
}
