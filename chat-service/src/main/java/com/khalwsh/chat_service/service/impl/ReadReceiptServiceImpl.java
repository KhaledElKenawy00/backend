package com.khalwsh.chat_service.service.impl;

import com.khalwsh.chat_service.dto.response.UnreadCountResponse;
import com.khalwsh.chat_service.model.Message;
import com.khalwsh.chat_service.repository.MessageRepository;
import com.khalwsh.chat_service.service.ReadReceiptService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReadReceiptServiceImpl implements ReadReceiptService {

    private final StringRedisTemplate redisTemplate;
    private final MessageRepository messageRepository;

    // bounded growth across (channel, user) pairs — every forward move refreshes the TTL
    private static final long CURSOR_TTL_SECONDS = 30L * 24 * 3600;

    // hex ObjectIds sort lexicographically, so a plain string > comparison is equivalent to chronological ordering
    private static final DefaultRedisScript<Boolean> MOVE_FORWARD_SCRIPT;
    static {
        MOVE_FORWARD_SCRIPT = new DefaultRedisScript<>();
        MOVE_FORWARD_SCRIPT.setScriptText(
                "local current = redis.call('GET', KEYS[1]) " +
                "if not current or ARGV[1] > current then " +
                "  redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) " +
                "  return 1 " +
                "end " +
                "return 0"
        );
        MOVE_FORWARD_SCRIPT.setResultType(Boolean.class);
    }

    // key format: read:{channelId}:{userId} -> lastReadMessageId
    private String channelKey(String channelId, Integer userId) {
        return "read:" + channelId + ":" + userId;
    }

    @Override
    public void markAsRead(String channelId, Integer userId, String lastReadMessageId) {
        validateChannelMessage(channelId, lastReadMessageId);
        moveForwardOnly(channelKey(channelId, userId), lastReadMessageId);
    }

    @Override
    public UnreadCountResponse getUnreadCount(String channelId, Integer userId) {
        String key = channelKey(channelId, userId);
        String lastReadMessageId = redisTemplate.opsForValue().get(key);

        long unreadCount = (lastReadMessageId == null)
                ? messageRepository.countChannelMessages(new ObjectId(channelId))
                : messageRepository.countChannelMessagesAfter(
                        new ObjectId(channelId), new ObjectId(lastReadMessageId));

        return UnreadCountResponse.builder()
                .unreadCount(unreadCount)
                .lastReadMessageId(lastReadMessageId)
                .build();
    }

    // key format: read:thread:{threadId}:{userId} -> lastReadMessageId
    private String threadKey(String threadId, Integer userId) {
        return "read:thread:" + threadId + ":" + userId;
    }

    @Override
    public void markThreadAsRead(String threadId, Integer userId, String lastReadMessageId) {
        validateThreadMessage(threadId, lastReadMessageId);
        moveForwardOnly(threadKey(threadId, userId), lastReadMessageId);
    }

    @Override
    public UnreadCountResponse getThreadUnreadCount(String threadId, Integer userId) {
        String key = threadKey(threadId, userId);
        String lastReadMessageId = redisTemplate.opsForValue().get(key);

        long unreadCount = (lastReadMessageId == null)
                ? messageRepository.countThreadMessages(new ObjectId(threadId))
                : messageRepository.countThreadMessagesAfter(
                        new ObjectId(threadId), new ObjectId(lastReadMessageId));

        return UnreadCountResponse.builder()
                .unreadCount(unreadCount)
                .lastReadMessageId(lastReadMessageId)
                .build();
    }

    // thread replies have their own cursor and must not move the channel-level read state
    private void validateChannelMessage(String channelId, String messageId) {
        Optional<Message> opt = messageRepository.findById(parseId(messageId, "lastReadMessageId"));
        if (opt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lastReadMessageId not found");
        }
        Message m = opt.get();
        if (!m.getChannelId().equals(parseId(channelId, "channelId"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lastReadMessageId does not belong to this channel");
        }
        if (m.getThreadId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lastReadMessageId is a thread reply, not a channel message");
        }
    }

    private void validateThreadMessage(String threadId, String messageId) {
        Optional<Message> opt = messageRepository.findById(parseId(messageId, "lastReadMessageId"));
        if (opt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lastReadMessageId not found");
        }
        Message m = opt.get();
        if (m.getThreadId() == null
                || !m.getThreadId().equals(parseId(threadId, "threadId"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lastReadMessageId does not belong to this thread");
        }
    }

    private ObjectId parseId(String hex, String field) {
        try {
            return new ObjectId(hex);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is not a valid id");
        }
    }

    private void moveForwardOnly(String key, String newMessageId) {
        redisTemplate.execute(
                MOVE_FORWARD_SCRIPT,
                Collections.singletonList(key),
                newMessageId,
                String.valueOf(CURSOR_TTL_SECONDS)
        );
    }
}

