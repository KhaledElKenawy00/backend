package com.khalwsh.notifications.controller;

import com.khalwsh.notifications.config.GlobalExceptionHandler;
import com.khalwsh.notifications.messaging.NotificationType;
import com.khalwsh.notifications.model.Notification;
import com.khalwsh.notifications.service.InAppNotificationService;
import com.khalwsh.notifications.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InboxControllerTest {

    @Mock private InAppNotificationService service;
    @Mock private UserContext userContext;

    @InjectMocks private InboxController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Notification sample() {
        return Notification.builder()
                .id("abc123")
                .userId(12L)
                .type(NotificationType.TASK_ASSIGNED)
                .title("New task assigned")
                .body("Mostafa assigned you \"Review PR\"")
                .read(false)
                .createdAt(Instant.parse("2026-05-16T14:00:00Z"))
                .build();
    }

    @Test
    void listDefaultsToPage1Size20() throws Exception {
        when(userContext.currentUserId()).thenReturn(12L);
        when(service.list(eq(12L), eq(0), eq(20)))   // 1-based → 0-based
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));
        when(service.unreadCount(12L)).thenReturn(1L);

        mvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))           // 0-based + 1 = 1
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value("abc123"))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void pageZeroIsRejected() throws Exception {
        // validation throws before UserContext is consulted — no stubs needed
        mvc.perform(get("/api/notifications").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must be >= 1"));
    }

    @Test
    void unreadCountReturnsCount() throws Exception {
        when(userContext.currentUserId()).thenReturn(12L);
        when(service.unreadCount(12L)).thenReturn(7L);

        mvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(7));
    }

    @Test
    void markReadOnExistingReturns200() throws Exception {
        when(userContext.currentUserId()).thenReturn(12L);
        when(service.markRead("abc123", 12L)).thenReturn(true);

        mvc.perform(patch("/api/notifications/abc123/read"))
                .andExpect(status().isOk());
    }

    @Test
    void markReadOnMissingReturns404() throws Exception {
        when(userContext.currentUserId()).thenReturn(12L);
        when(service.markRead("ghost", 12L)).thenReturn(false);

        mvc.perform(patch("/api/notifications/ghost/read"))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllReadReturnsCount() throws Exception {
        when(userContext.currentUserId()).thenReturn(12L);
        when(service.markAllRead(12L)).thenReturn(5L);

        mvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(5));
    }

    @Test
    void deleteExistingReturns204() throws Exception {
        when(userContext.currentUserId()).thenReturn(12L);
        when(service.delete("abc123", 12L)).thenReturn(true);

        mvc.perform(delete("/api/notifications/abc123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        when(userContext.currentUserId()).thenReturn(12L);
        when(service.delete("ghost", 12L)).thenReturn(false);

        mvc.perform(delete("/api/notifications/ghost"))
                .andExpect(status().isNotFound());
    }
}
