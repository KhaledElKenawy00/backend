package com.khalwsh.notifications.model;

import com.khalwsh.notifications.messaging.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "inbox_idx",  def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "unread_idx", def = "{'userId': 1, 'read': 1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    private Long userId;

    private NotificationType type;

    private String title;

    private String body;

    private Map<String, Object> data;

    private boolean read;

    private Instant readAt;

    @Indexed(unique = true)
    private String eventId;

    private Instant createdAt;
}
