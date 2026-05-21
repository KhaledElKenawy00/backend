package com.virtualoffice.chat_service.service;

import org.bson.types.ObjectId;

public interface ThreadCleanupService {

    void cleanupThreadMessages(ObjectId threadId);
}
