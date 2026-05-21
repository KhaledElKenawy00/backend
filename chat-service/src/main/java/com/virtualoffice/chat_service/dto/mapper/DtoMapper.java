package com.virtualoffice.chat_service.dto.mapper;

import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.ThreadResponse;
import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChatThread;
import com.virtualoffice.chat_service.model.Message;
import org.bson.types.ObjectId;

public class DtoMapper {

    private DtoMapper() {}

    public static ChannelResponse toChannelResponse(Channel channel) {
        return ChannelResponse.builder()
                .id(channel.getId().toHexString())
                .name(channel.getName())
                .type(channel.getType().name())
                .workspaceId(channel.getWorkspaceId())
                .members(channel.getMembers())
                .createdBy(channel.getCreatedBy())
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .build();
    }

    public static MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId().toHexString())
                .channelId(message.getChannelId().toHexString())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .type(message.getType().name())
                .threadId(toHex(message.getThreadId()))
                .replyToId(toHex(message.getReplyToId()))
                .mentions(message.getMentions())
                .clientMessageId(message.getClientMessageId())
                .deleted(message.getDeleted())
                .deletedAt(message.getDeletedAt())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    public static ThreadResponse toThreadResponse(ChatThread thread) {
        return ThreadResponse.builder()
                .id(thread.getId().toHexString())
                .channelId(thread.getChannelId().toHexString())
                .rootMessageId(thread.getRootMessageId().toHexString())
                .name(thread.getName())
                .createdBy(thread.getCreatedBy())
                .deleted(thread.getDeleted())
                .createdAt(thread.getCreatedAt())
                .updatedAt(thread.getUpdatedAt())
                .build();
    }

    private static String toHex(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }
}
