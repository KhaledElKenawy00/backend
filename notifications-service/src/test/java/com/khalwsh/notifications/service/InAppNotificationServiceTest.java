package com.khalwsh.notifications.service;

import com.khalwsh.notifications.messaging.NotificationEvent;
import com.khalwsh.notifications.messaging.NotificationType;
import com.khalwsh.notifications.model.Notification;
import com.khalwsh.notifications.repository.NotificationRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock private NotificationRepository repository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private InAppNotificationService service;

    private NotificationEvent taskAssignedEvent(Map<String, Object> payload) {
        NotificationEvent event = new NotificationEvent();
        event.setEventId("evt-1");
        event.setType(NotificationType.TASK_ASSIGNED);
        event.setOccurredAt(Instant.parse("2026-05-16T14:00:00Z"));
        event.setPayload(payload);
        return event;
    }

    @Test
    void createFromEventBuildsDocumentFromPayload() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = new HashMap<>();
        payload.put("assigneeUserId", 12);
        payload.put("taskId", 884);
        payload.put("taskTitle", "Wire up SMTP");
        payload.put("assignedBy", 4);
        payload.put("assignedByName", "Mostafa");
        payload.put("workspaceId", 7);
        payload.put("dueAt", "2026-05-22T17:00:00Z");

        Optional<Notification> saved = service.createFromEvent(taskAssignedEvent(payload));

        assertThat(saved).isPresent();
        Notification n = saved.get();
        assertThat(n.getUserId()).isEqualTo(12L);
        assertThat(n.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(n.getTitle()).isEqualTo("New task assigned");
        assertThat(n.getBody()).contains("Mostafa").contains("Wire up SMTP");
        assertThat(n.getData()).containsEntry("taskId", 884);
        assertThat(n.getData()).containsEntry("workspaceId", 7);
        assertThat(n.isRead()).isFalse();
        assertThat(n.getEventId()).isEqualTo("evt-1");
    }

    @Test
    void createFromEventAcceptsAssigneeUserIdAsLong() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Notification> saved = service.createFromEvent(
                taskAssignedEvent(Map.of("assigneeUserId", 9999999999L,
                                          "taskTitle", "T",
                                          "assignedByName", "X")));

        assertThat(saved).isPresent();
        assertThat(saved.get().getUserId()).isEqualTo(9999999999L);
    }

    @Test
    void createFromEventAcceptsAssigneeUserIdAsString() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Notification> saved = service.createFromEvent(
                taskAssignedEvent(Map.of("assigneeUserId", "42",
                                          "taskTitle", "T",
                                          "assignedByName", "X")));

        assertThat(saved).isPresent();
        assertThat(saved.get().getUserId()).isEqualTo(42L);
    }

    @Test
    void createFromEventReturnsEmptyWhenAssigneeUserIdMissing() {
        Optional<Notification> saved = service.createFromEvent(
                taskAssignedEvent(Map.of("taskTitle", "T")));

        assertThat(saved).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void createFromEventReturnsEmptyWhenPayloadIsNull() {
        NotificationEvent event = taskAssignedEvent(null);

        Optional<Notification> saved = service.createFromEvent(event);

        assertThat(saved).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void createFromEventReturnsEmptyOnDuplicateKey() {
        when(repository.save(any())).thenThrow(new DuplicateKeyException("eventId"));

        Optional<Notification> saved = service.createFromEvent(
                taskAssignedEvent(Map.of("assigneeUserId", 12)));

        assertThat(saved).isEmpty();
    }

    @Test
    void createFromEventDefaultsBodyWhenAssignedByNameMissing() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Notification> saved = service.createFromEvent(
                taskAssignedEvent(Map.of("assigneeUserId", 12, "taskTitle", "Task X")));

        assertThat(saved).isPresent();
        assertThat(saved.get().getBody()).startsWith("Someone").contains("Task X");
    }

    @Test
    void createFromEventDefaultsBodyWhenTaskTitleMissing() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Notification> saved = service.createFromEvent(
                taskAssignedEvent(Map.of("assigneeUserId", 12, "assignedByName", "Mostafa")));

        assertThat(saved).isPresent();
        assertThat(saved.get().getBody()).contains("a task");
    }

    @Test
    void listDelegatesToRepository() {
        Page<Notification> page = new PageImpl<>(List.of(new Notification()), PageRequest.of(2, 5), 15);
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(12L), any())).thenReturn(page);

        Page<Notification> result = service.list(12L, 2, 5);

        assertThat(result.getTotalElements()).isEqualTo(15);
        assertThat(result.getNumber()).isEqualTo(2);
    }

    @Test
    void unreadCountDelegatesToRepository() {
        when(repository.countByUserIdAndReadFalse(12L)).thenReturn(7L);
        assertThat(service.unreadCount(12L)).isEqualTo(7L);
    }

    @Test
    void markReadFlipsUnreadDocumentAndReturnsTrue() {
        Notification n = Notification.builder().id("abc").userId(12L).read(false).build();
        when(repository.findByIdAndUserId("abc", 12L)).thenReturn(Optional.of(n));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = service.markRead("abc", 12L);

        assertThat(result).isTrue();
        assertThat(n.isRead()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    void markReadOnAlreadyReadIsIdempotent() {
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Notification n = Notification.builder().id("abc").userId(12L).read(true).readAt(earlier).build();
        when(repository.findByIdAndUserId("abc", 12L)).thenReturn(Optional.of(n));

        boolean result = service.markRead("abc", 12L);

        assertThat(result).isTrue();
        assertThat(n.getReadAt()).isEqualTo(earlier);
        verify(repository, never()).save(any());
    }

    @Test
    void markReadOnMissingReturnsFalse() {
        when(repository.findByIdAndUserId("ghost", 12L)).thenReturn(Optional.empty());

        boolean result = service.markRead("ghost", 12L);

        assertThat(result).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void markReadCrossUserBehavesAsNotFound() {
        // Querying by id AND userId means another user's id is invisible to us.
        when(repository.findByIdAndUserId("abc", 13L)).thenReturn(Optional.empty());

        boolean result = service.markRead("abc", 13L);

        assertThat(result).isFalse();
    }

    @Test
    void markAllReadIssuesBulkUpdateScopedByUserAndUnreadOnly() {
        UpdateResult result = mock(UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(5L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Notification.class)))
                .thenReturn(result);

        long count = service.markAllRead(12L);
        assertThat(count).isEqualTo(5L);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(queryCaptor.capture(), updateCaptor.capture(), eq(Notification.class));

        // Query is scoped to this user and only unread documents.
        String queryString = queryCaptor.getValue().getQueryObject().toJson();
        assertThat(queryString).contains("userId").contains("12").contains("read");

        // Update sets both read and readAt. Don't render the Update's JSON
        // because BSON has no default codec for Instant in this test context.
        Update update = updateCaptor.getValue();
        assertThat(update.getUpdateObject().keySet()).contains("$set");
        assertThat(update.getUpdateObject().get("$set", org.bson.Document.class).keySet())
                .containsExactlyInAnyOrder("read", "readAt");
        assertThat(update.getUpdateObject().get("$set", org.bson.Document.class).get("read"))
                .isEqualTo(true);
    }

    @Test
    void deleteRemovesExistingAndReturnsTrue() {
        Notification n = Notification.builder().id("abc").userId(12L).build();
        when(repository.findByIdAndUserId("abc", 12L)).thenReturn(Optional.of(n));

        boolean result = service.delete("abc", 12L);

        assertThat(result).isTrue();
        verify(repository).delete(n);
    }

    @Test
    void deleteMissingReturnsFalse() {
        when(repository.findByIdAndUserId("ghost", 12L)).thenReturn(Optional.empty());

        boolean result = service.delete("ghost", 12L);

        assertThat(result).isFalse();
        verify(repository, never()).delete(any(Notification.class));
    }
}
