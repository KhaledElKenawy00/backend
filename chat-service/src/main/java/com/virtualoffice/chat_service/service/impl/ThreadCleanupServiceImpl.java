package com.virtualoffice.chat_service.service.impl;

import com.virtualoffice.chat_service.model.Message;
import com.virtualoffice.chat_service.repository.MessageRepository;
import com.virtualoffice.chat_service.service.ThreadCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadCleanupServiceImpl implements ThreadCleanupService {

    private final MessageRepository messageRepository;

    @Async
    @Override
    public void cleanupThreadMessages(ObjectId threadId) {
        try {
            List<Message> threadMessages = messageRepository.findAllByThreadId(threadId);
            Instant now = Instant.now();

            boolean needToWrite = false;
            int deletedCount = 0;
            for (Message message : threadMessages) {
                if (!message.getDeleted()) {
                    message.setDeleted(true);
                    message.setContent(null);
                    message.setDeletedAt(now);
                    message.setUpdatedAt(now);
                    needToWrite = true;
                    deletedCount++;
                }
            }

            if (!threadMessages.isEmpty() && needToWrite) {
                messageRepository.saveAll(threadMessages);
                log.info("cleaned up {} messages for deleted thread {}", deletedCount, threadId.toHexString());
            }
        } catch (Exception e) {
            // swallow — the thread itself is already marked deleted, so the messages are unreachable
            log.error("failed to clean up messages for thread {}: {}", threadId.toHexString(), e.getMessage(), e);
        }
    }
}
