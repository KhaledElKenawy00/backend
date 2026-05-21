package com.virtualoffice.chat_service.service;

import com.virtualoffice.chat_service.dto.request.CreateThreadRequest;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.dto.response.ThreadResponse;

public interface ThreadService {

    ThreadResponse createThread(String channelId, CreateThreadRequest request, Integer creatorUserId, String creatorRole);

    PaginatedResponse<ThreadResponse> getChannelThreads(String channelId, int page, int limit);

    ThreadResponse getThread(String threadId);

    void deleteThread(String threadId, Integer requestingUserId, String requestingUserRole);
}
