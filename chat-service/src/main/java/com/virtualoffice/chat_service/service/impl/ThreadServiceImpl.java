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
import com.virtualoffice.chat_service.dto.request.CreateThreadRequest;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.dto.response.ThreadResponse;
import com.virtualoffice.chat_service.model.ChatThread;
import com.virtualoffice.chat_service.model.Message;
import com.virtualoffice.chat_service.repository.MessageRepository;
import com.virtualoffice.chat_service.repository.ThreadRepository;
import com.virtualoffice.chat_service.service.ChannelService;
import com.virtualoffice.chat_service.service.ThreadCleanupService;
import com.virtualoffice.chat_service.service.ThreadService;
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

@Service
@RequiredArgsConstructor
public class ThreadServiceImpl implements ThreadService {

    private final ThreadRepository threadRepository;
    private final MessageRepository messageRepository;
    private final ChannelService channelService;
    private final ThreadCleanupService threadCleanupService;

    @Override
    public ThreadResponse createThread(String channelId, CreateThreadRequest request, Integer creatorUserId, String creatorRole) {
        if (!channelService.isMember(channelId, creatorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you are not a member of this channel");
        }

        ObjectId channelOid = new ObjectId(channelId);
        ObjectId rootMessageOid = new ObjectId(request.getRootMessageId());

        Message rootMessage = messageRepository.findById(rootMessageOid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "root message not found"));

        if (!rootMessage.getChannelId().equals(channelOid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message does not belong to this channel");
        }

        // disallow nested threads — keeps the data model flat (channel → thread → messages)
        if (rootMessage.getThreadId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot create a thread from a message that is already inside a thread");
        }

        if (threadRepository.existsByRootMessageId(rootMessageOid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a thread already exists for this message");
        }

        Instant now = Instant.now();

        ChatThread thread = ChatThread.builder()
                .channelId(channelOid)
                .rootMessageId(rootMessageOid)
                .name(request.getName())
                .createdBy(creatorUserId)
                .creatorRole(creatorRole != null ? creatorRole : "USER")
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            ChatThread saved = threadRepository.save(thread);
            return DtoMapper.toThreadResponse(saved);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a thread already exists for this message");
        }
    }

    @Override
    public PaginatedResponse<ThreadResponse> getChannelThreads(String channelId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<ChatThread> threadPage = threadRepository.findActiveThreadsByChannelId(new ObjectId(channelId), pageRequest);

        List<ThreadResponse> threads = threadPage.getContent()
                .stream()
                .map(DtoMapper::toThreadResponse)
                .toList();

        return PaginatedResponse.<ThreadResponse>builder()
                .content(threads)
                .totalPages(threadPage.getTotalPages())
                .totalElements(threadPage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public ThreadResponse getThread(String threadId) {
        ChatThread thread = threadRepository.findActiveById(new ObjectId(threadId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "thread not found"));
        return DtoMapper.toThreadResponse(thread);
    }

    @Override
    public void deleteThread(String threadId, Integer requestingUserId, String requestingUserRole) {
        ObjectId threadOid = new ObjectId(threadId);
        ChatThread thread = threadRepository.findActiveById(threadOid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "thread not found"));

        boolean isCreator = thread.getCreatedBy().equals(requestingUserId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requestingUserRole);

        if (!isCreator) {
            if (!isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you can only delete your own threads");
            }
            if ("ADMIN".equalsIgnoreCase(thread.getCreatorRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admins cannot delete other admins' threads");
            }
        }

        thread.setDeleted(true);
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);

        threadCleanupService.cleanupThreadMessages(threadOid);
    }
}
