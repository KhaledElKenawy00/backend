package com.virtualoffice.chat_service.controller;

import com.virtualoffice.chat_service.dto.request.CreateThreadRequest;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.dto.response.ThreadResponse;
import com.virtualoffice.chat_service.dto.response.WebSocketEvent;
import com.virtualoffice.chat_service.service.ChannelService;
import com.virtualoffice.chat_service.service.MessageService;
import com.virtualoffice.chat_service.service.ThreadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadControllerTest {

    @Mock
    private ThreadService threadService;

    @Mock
    private MessageService messageService;

    @Mock
    private ChannelService channelService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ThreadController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    // ────────────────────────────────────────
    // createThread
    // ────────────────────────────────────────

    @Nested
    class CreateThread {

        @Test
        void shouldReturn201() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            CreateThreadRequest req = CreateThreadRequest.builder()
                    .rootMessageId("msg1").name("thread1").build();
            ThreadResponse expected = ThreadResponse.builder()
                    .id("t1").channelId("ch1").name("thread1").build();
            when(threadService.createThread(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(expected);

            ResponseEntity<ThreadResponse> response = controller.createThread("ch1", req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(expected);
        }
    }

    // ────────────────────────────────────────
    // getChannelThreads — membership check
    // ────────────────────────────────────────

    @Nested
    class GetChannelThreads {

        @Test
        void shouldReturnThreadsForMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            PaginatedResponse<ThreadResponse> expected = PaginatedResponse.<ThreadResponse>builder()
                    .content(List.of()).currentPage(1).totalElements(0).totalPages(0).build();
            when(threadService.getChannelThreads("ch1", 1, 20)).thenReturn(expected);

            ResponseEntity<PaginatedResponse<ThreadResponse>> response =
                    controller.getChannelThreads("ch1", 1, 20, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldThrow403ForNonMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(false);

            assertThatThrownBy(() -> controller.getChannelThreads("ch1", 1, 20, httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ────────────────────────────────────────
    // getThread — membership check via parent channel
    // ────────────────────────────────────────

    @Nested
    class GetThread {

        @Test
        void shouldReturnThreadForMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder()
                    .id("t1").channelId("ch1").name("thread1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(true);

            ResponseEntity<ThreadResponse> response = controller.getThread("t1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(thread);
        }

        @Test
        void shouldThrow403ForNonMemberOfParentChannel() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder()
                    .id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(false);

            assertThatThrownBy(() -> controller.getThread("t1", httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ────────────────────────────────────────
    // deleteThread — with WebSocket broadcast
    // ────────────────────────────────────────

    @Nested
    class DeleteThread {

        @Test
        void shouldDeleteAndBroadcastToBothChannelAndThreadTopics() {
            HttpServletRequest httpRequest = mockRequest("10", "ADMIN");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);

            ResponseEntity<Void> response = controller.deleteThread("t1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(threadService).deleteThread("t1", 10, "ADMIN");
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/thread/t1"), any(WebSocketEvent.class));
        }

        @Test
        void shouldIncludeChannelIdAndThreadIdInPayload() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);

            controller.deleteThread("t1", httpRequest);

            org.mockito.ArgumentCaptor<WebSocketEvent> captor = org.mockito.ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), captor.capture());
            WebSocketEvent<?> event = captor.getValue();
            assertThat(event.getAction()).isEqualTo("THREAD_DELETED");
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> payload = (java.util.Map<String, String>) event.getPayload();
            assertThat(payload).containsEntry("threadId", "t1").containsEntry("channelId", "ch1");
        }
    }

    // ────────────────────────────────────────
    // getThreadMessages — membership + pagination
    // ────────────────────────────────────────

    @Nested
    class GetThreadMessages {

        @Test
        void shouldThrow403ForNonMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(false);

            assertThatThrownBy(() -> controller.getThreadMessages("t1", null, null, 1, 50, httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        void shouldReturnPageBasedPagination() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            PaginatedResponse<MessageResponse> expected = PaginatedResponse.<MessageResponse>builder()
                    .content(List.of()).currentPage(1).totalElements(0).totalPages(0).build();
            when(messageService.getThreadMessages("t1", 1, 50)).thenReturn(expected);

            ResponseEntity<?> response = controller.getThreadMessages("t1", null, null, 1, 50, httpRequest);

            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldUseCursorBefore() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            List<MessageResponse> expected = List.of();
            when(messageService.getThreadMessagesBefore("t1", "c1", 50)).thenReturn(expected);

            ResponseEntity<?> response = controller.getThreadMessages("t1", "c1", null, 1, 50, httpRequest);

            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldUseCursorAfter() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            List<MessageResponse> expected = List.of();
            when(messageService.getThreadMessagesAfter("t1", "c2", 50)).thenReturn(expected);

            ResponseEntity<?> response = controller.getThreadMessages("t1", null, "c2", 1, 50, httpRequest);

            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldPrioritizeBeforeOverAfter() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            when(messageService.getThreadMessagesBefore("t1", "c1", 50)).thenReturn(List.of());

            controller.getThreadMessages("t1", "c1", "c2", 1, 50, httpRequest);

            verify(messageService).getThreadMessagesBefore("t1", "c1", 50);
            verify(messageService, never()).getThreadMessagesAfter(any(), any(), anyInt());
        }
    }
}
