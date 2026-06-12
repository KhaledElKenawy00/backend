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
package com.virtualoffice.chat_service.service.impl;

import com.virtualoffice.chat_service.dto.mapper.DtoMapper;
import com.virtualoffice.chat_service.dto.request.SendMessageRequest;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.model.ChatThread;
import com.virtualoffice.chat_service.model.Message;
import com.virtualoffice.chat_service.model.MessageType;
import com.virtualoffice.chat_service.repository.MessageRepository;
import com.virtualoffice.chat_service.repository.ThreadRepository;
import com.virtualoffice.chat_service.service.ChannelService;
import com.virtualoffice.chat_service.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final ChannelService channelService;
    private final ThreadRepository threadRepository;

    @Override
    public MessageResponse sendMessage(String channelId, SendMessageRequest request, Integer senderId, String senderRole) {
        if (!channelService.isMember(channelId, senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you are not a member of this channel");
        }

        ObjectId channelOid = new ObjectId(channelId);

        String normalized = request.getClientMessageId();
        if (normalized != null && normalized.isBlank()) {
            normalized = null;
        }
        final String clientMsgId = normalized;

        if (clientMsgId != null) {
            Optional<Message> existing = messageRepository.findBySenderIdAndClientMessageId(senderId, clientMsgId);
            if (existing.isPresent()) {
                return DtoMapper.toMessageResponse(existing.get());
            }
        }

        ObjectId threadOid = toObjectId(request.getThreadId());
        if (threadOid != null) {
            ChatThread thread = threadRepository.findActiveById(threadOid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "thread not found"));
            if (!thread.getChannelId().equals(channelOid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "thread does not belong to this channel");
            }
        }

        ObjectId replyOid = toObjectId(request.getReplyToId());
        if (replyOid != null) {
            Message replyTarget = messageRepository.findById(replyOid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "replyToId not found"));
            if (!replyTarget.getChannelId().equals(channelOid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "replyToId does not belong to this channel");
            }
        }

        Instant now = Instant.now();

        Message message = Message.builder()
                .channelId(channelOid)
                .senderId(senderId)
                .senderRole(senderRole != null ? senderRole : "USER")
                .content(request.getContent())
                .type(MessageType.TEXT)
                .threadId(threadOid)
                .replyToId(replyOid)
                .mentions(request.getMentions())
                .clientMessageId(clientMsgId)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            return DtoMapper.toMessageResponse(messageRepository.save(message));
        } catch (DuplicateKeyException e) {
            if (clientMsgId != null) {
                return messageRepository.findBySenderIdAndClientMessageId(senderId, clientMsgId)
                        .map(DtoMapper::toMessageResponse)
                        .orElseThrow(() -> e);
            }
            throw e;
        }
    }

    @Override
    public PaginatedResponse<MessageResponse> getChannelMessages(String channelId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt", "_id"));

        Page<Message> messagePage = messageRepository.findChannelMessages(new ObjectId(channelId), pageRequest);

        List<MessageResponse> messages = messagePage.getContent()
                .stream()
                .map(DtoMapper::toMessageResponse)
                .toList();

        return PaginatedResponse.<MessageResponse>builder()
                .content(messages)
                .totalPages(messagePage.getTotalPages())
                .totalElements(messagePage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public List<MessageResponse> getChannelMessagesBefore(String channelId, String beforeId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC,  "_id"));

        return messageRepository.findChannelMessagesBefore(new ObjectId(channelId), new ObjectId(beforeId), pageRequest)
                .stream()
                .map(DtoMapper::toMessageResponse)
                .toList();
    }

    @Override
    public List<MessageResponse> getChannelMessagesAfter(String channelId, String afterId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC,  "_id"));

        return messageRepository.findChannelMessagesAfter(new ObjectId(channelId), new ObjectId(afterId), pageRequest)
                .stream()
                .map(DtoMapper::toMessageResponse)
                .toList();
    }

    @Override
    public PaginatedResponse<MessageResponse> getThreadMessages(String threadId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt", "_id"));

        Page<Message> messagePage = messageRepository.findThreadMessages(new ObjectId(threadId), pageRequest);

        List<MessageResponse> messages = messagePage.getContent()
                .stream()
                .map(DtoMapper::toMessageResponse)
                .toList();

        return PaginatedResponse.<MessageResponse>builder()
                .content(messages)
                .totalPages(messagePage.getTotalPages())
                .totalElements(messagePage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public List<MessageResponse> getThreadMessagesBefore(String threadId, String beforeId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt", "_id"));

        return messageRepository.findThreadMessagesBefore(new ObjectId(threadId), new ObjectId(beforeId), pageRequest)
                .stream()
                .map(DtoMapper::toMessageResponse)
                .toList();
    }

    @Override
    public List<MessageResponse> getThreadMessagesAfter(String threadId, String afterId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "createdAt", "_id"));

        return messageRepository.findThreadMessagesAfter(new ObjectId(threadId), new ObjectId(afterId), pageRequest)
                .stream()
                .map(DtoMapper::toMessageResponse)
                .toList();
    }

    @Override
    public MessageResponse editMessage(String messageId, String newContent, Integer requestingUserId, String requestingUserRole) {
        ObjectId msgId = new ObjectId(messageId);
        Message message = messageRepository.findById(msgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found"));

        if (message.getDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot edit a deleted message");
        }

        if (!message.getSenderId().equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you can only edit your own messages");
        }

        message.setContent(newContent);
        message.setUpdatedAt(Instant.now());

        return DtoMapper.toMessageResponse(messageRepository.save(message));
    }

    @Override
    public MessageResponse deleteMessage(String messageId, Integer requestingUserId, String requestingUserRole) {
        ObjectId msgId = new ObjectId(messageId);
        Message message = messageRepository.findById(msgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found"));

        // null tells the controller "already deleted, don't broadcast"
        if (message.getDeleted()) {
            return null;
        }

        boolean isOwner = message.getSenderId().equals(requestingUserId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requestingUserRole);

        if (!isOwner) {
            if (!isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you can only delete your own messages");
            }
            if ("ADMIN".equalsIgnoreCase(message.getSenderRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admins cannot delete other admins' messages");
            }
        }

        message.setDeleted(true);
        message.setContent(null);
        message.setDeletedAt(Instant.now());
        message.setUpdatedAt(Instant.now());

        return DtoMapper.toMessageResponse(messageRepository.save(message));
    }

    private ObjectId toObjectId(String hexString) {
        return hexString != null ? new ObjectId(hexString) : null;
    }
}
