package com.virtualoffice.chat_service.service.impl;

import com.virtualoffice.chat_service.dto.mapper.DtoMapper;
import com.virtualoffice.chat_service.dto.request.CreateChannelRequest;
import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import com.virtualoffice.chat_service.service.ChannelService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;

    @Override
    public ChannelResponse createGroupChannel(CreateChannelRequest request, Integer creatorUserId) {
        if (request.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId is required for group channels");
        }

        List<Integer> members = new ArrayList<>(request.getMembers());
        if (!members.contains(creatorUserId)) {
            members.add(creatorUserId);
        }

        Instant now = Instant.now();

        Channel channel = Channel.builder()
                .name(request.getName())
                .type(ChannelType.GROUP)
                .workspaceId(request.getWorkspaceId())
                .members(members)
                .createdBy(creatorUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Channel saved = channelRepository.save(channel);
        return DtoMapper.toChannelResponse(saved);
    }

    @Override
    public PaginatedResponse<ChannelResponse> getWorkspaceChannels(Integer workspaceId, Integer userId, int page, int limit) {
        // API is 1-based, Spring's PageRequest is 0-based
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.ASC, "name"));

        Page<Channel> channelPage = channelRepository.findWorkspaceChannelsForUser(workspaceId, userId, pageRequest);

        List<ChannelResponse> channels = channelPage.getContent()
                .stream()
                .map(DtoMapper::toChannelResponse)
                .toList();

        return PaginatedResponse.<ChannelResponse>builder()
                .content(channels)
                .totalPages(channelPage.getTotalPages())
                .totalElements(channelPage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public ChannelResponse getChannel(String channelId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found"));
        return DtoMapper.toChannelResponse(channel);
    }

    @Override
    public void joinChannel(String channelId, Integer userId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found"));

        if (channel.getType() != ChannelType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot join a direct message channel");
        }

        if (channel.getMembers().contains(userId)) {
            return;
        }

        channel.getMembers().add(userId);
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
    }

    @Override
    public void leaveChannel(String channelId, Integer userId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found"));

        if (channel.getType() != ChannelType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot leave a direct message channel");
        }

        if (!channel.getMembers().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a member of this channel");
        }

        channel.getMembers().remove(Integer.valueOf(userId));

        // last member out — delete so the (workspaceId, name) slot is freed up for re-use
        if (channel.getMembers().isEmpty()) {
            channelRepository.delete(channel);
            return;
        }

        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
    }

    @Override
    public ChannelResponse getOrCreateDm(Integer currentUserId, Integer targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot create a DM with yourself");
        }

        // canonical key — smaller id first so the lookup is order-independent
        String dmKey = Math.min(currentUserId, targetUserId) + "_" + Math.max(currentUserId, targetUserId);

        Optional<Channel> existing = channelRepository.findByDmKey(dmKey);
        if (existing.isPresent()) {
            return DtoMapper.toChannelResponse(existing.get());
        }

        Instant now = Instant.now();
        Channel dm = Channel.builder()
                .type(ChannelType.DIRECT)
                .workspaceId(null)
                .members(List.of(currentUserId, targetUserId))
                .dmKey(dmKey)
                .createdBy(currentUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            return DtoMapper.toChannelResponse(channelRepository.save(dm));
        } catch (DuplicateKeyException e) {
            // concurrent create lost the race — re-fetch the winner
            return channelRepository.findByDmKey(dmKey)
                    .map(DtoMapper::toChannelResponse)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DM creation failed unexpectedly"));
        }
    }

    @Override
    public PaginatedResponse<ChannelResponse> getDirectMessages(Integer userId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Channel> dmPage = channelRepository.findDirectChannelsForUser(ChannelType.DIRECT, userId, pageRequest);

        List<ChannelResponse> dms = dmPage.getContent()
                .stream()
                .map(DtoMapper::toChannelResponse)
                .toList();

        return PaginatedResponse.<ChannelResponse>builder()
                .content(dms)
                .totalPages(dmPage.getTotalPages())
                .totalElements(dmPage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public boolean isMember(String channelId, Integer userId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id).orElse(null);
        if (channel == null) return false;
        return channel.getMembers().contains(userId);
    }
}
