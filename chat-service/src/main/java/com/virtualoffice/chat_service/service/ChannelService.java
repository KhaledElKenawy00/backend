package com.virtualoffice.chat_service.service;

import com.virtualoffice.chat_service.dto.request.CreateChannelRequest;
import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;

public interface ChannelService {

    ChannelResponse createGroupChannel(CreateChannelRequest request, Integer creatorUserId);

    PaginatedResponse<ChannelResponse> getWorkspaceChannels(Integer workspaceId, Integer userId, int page, int limit);

    ChannelResponse getChannel(String channelId);

    void joinChannel(String channelId, Integer userId);

    void leaveChannel(String channelId, Integer userId);

    ChannelResponse getOrCreateDm(Integer currentUserId, Integer targetUserId);

    PaginatedResponse<ChannelResponse> getDirectMessages(Integer userId, int page, int limit);

    boolean isMember(String channelId, Integer userId);
}
