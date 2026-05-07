package com.khalwsh.chat_service.controller;

import com.khalwsh.chat_service.dto.request.EditMessageRequest;
import com.khalwsh.chat_service.dto.request.SendMessageRequest;
import com.khalwsh.chat_service.dto.response.MessageResponse;
import com.khalwsh.chat_service.dto.response.PaginatedResponse;
import com.khalwsh.chat_service.dto.response.WebSocketEvent;
import com.khalwsh.chat_service.service.ChannelService;
import com.khalwsh.chat_service.service.MessageService;
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
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private ChannelService channelService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    // ────────────────────────────────────────
    // sendMessage
    // ────────────────────────────────────────

    @Nested
    class SendMessage {

        @Test
        void shouldReturn201AndBroadcastToChannelTopic() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            SendMessageRequest req = SendMessageRequest.builder().content("hello").build();
            MessageResponse expected = MessageResponse.builder().id("msg1").channelId("ch1").build();
            when(messageService.sendMessage(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(expected);

            ResponseEntity<MessageResponse> response = controller.sendMessage("ch1", req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(expected);
            // REST send must broadcast NEW_MESSAGE so WS subscribers see it
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate, never()).convertAndSend(startsWith("/topic/thread/"), any(WebSocketEvent.class));
        }

        @Test
        void shouldBroadcastToBothTopicsWhenInThread() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            SendMessageRequest req = SendMessageRequest.builder().content("threaded").threadId("t1").build();
            MessageResponse saved = MessageResponse.builder()
                    .id("msg1").channelId("ch1").threadId("t1").content("threaded").build();
            when(messageService.sendMessage(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(saved);

            controller.sendMessage("ch1", req, httpRequest);

            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/thread/t1"), any(WebSocketEvent.class));
        }

        @Test
        void shouldBroadcastEventActionAsNewMessage() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            SendMessageRequest req = SendMessageRequest.builder().content("hi").build();
            MessageResponse saved = MessageResponse.builder().id("msg1").channelId("ch1").build();
            when(messageService.sendMessage(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(saved);

            controller.sendMessage("ch1", req, httpRequest);

            org.mockito.ArgumentCaptor<WebSocketEvent> captor = org.mockito.ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo("NEW_MESSAGE");
            assertThat(captor.getValue().getPayload()).isEqualTo(saved);
        }
    }

    // ────────────────────────────────────────
    // getMessages — membership + pagination
    // ────────────────────────────────────────

    @Nested
    class GetMessages {

        @Test
        void shouldThrow403ForNonMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(false);

            assertThatThrownBy(() -> controller.getMessages("ch1", null, null, 1, 50, httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        void shouldReturnPageBasedPagination() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            PaginatedResponse<MessageResponse> expected = PaginatedResponse.<MessageResponse>builder()
                    .content(List.of()).currentPage(1).totalElements(0).totalPages(0).build();
            when(messageService.getChannelMessages("ch1", 1, 50)).thenReturn(expected);

            ResponseEntity<?> response = controller.getMessages("ch1", null, null, 1, 50, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldUseCursorBeforeWhenProvided() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            List<MessageResponse> expected = List.of();
            when(messageService.getChannelMessagesBefore("ch1", "cursor1", 50)).thenReturn(expected);

            ResponseEntity<?> response = controller.getMessages("ch1", "cursor1", null, 1, 50, httpRequest);

            assertThat(response.getBody()).isEqualTo(expected);
            verify(messageService, never()).getChannelMessages(any(), anyInt(), anyInt());
        }

        @Test
        void shouldUseCursorAfterWhenProvided() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            List<MessageResponse> expected = List.of();
            when(messageService.getChannelMessagesAfter("ch1", "cursor2", 50)).thenReturn(expected);

            ResponseEntity<?> response = controller.getMessages("ch1", null, "cursor2", 1, 50, httpRequest);

            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldPrioritizeBeforeOverAfter() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            List<MessageResponse> expected = List.of();
            when(messageService.getChannelMessagesBefore("ch1", "c1", 50)).thenReturn(expected);

            ResponseEntity<?> response = controller.getMessages("ch1", "c1", "c2", 1, 50, httpRequest);

            verify(messageService).getChannelMessagesBefore("ch1", "c1", 50);
            verify(messageService, never()).getChannelMessagesAfter(any(), any(), anyInt());
        }
    }

    // ────────────────────────────────────────
    // editMessage — with WebSocket broadcast
    // ────────────────────────────────────────

    @Nested
    class EditMessage {

        @Test
        void shouldEditAndBroadcastToChannelOnly() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            EditMessageRequest req = new EditMessageRequest();
            req.setContent("edited");
            MessageResponse edited = MessageResponse.builder()
                    .id("msg1").channelId("ch1").threadId(null).content("edited").build();
            when(messageService.editMessage("msg1", "edited", 10, "USER")).thenReturn(edited);

            ResponseEntity<MessageResponse> response = controller.editMessage("msg1", req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate, never()).convertAndSend(startsWith("/topic/thread/"), any(WebSocketEvent.class));
        }

        @Test
        void shouldBroadcastToBothTopicsWhenInThread() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            EditMessageRequest req = new EditMessageRequest();
            req.setContent("edited in thread");
            MessageResponse edited = MessageResponse.builder()
                    .id("msg1").channelId("ch1").threadId("t1").content("edited in thread").build();
            when(messageService.editMessage("msg1", "edited in thread", 10, "USER")).thenReturn(edited);

            controller.editMessage("msg1", req, httpRequest);

            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/thread/t1"), any(WebSocketEvent.class));
        }
    }

    // ────────────────────────────────────────
    // deleteMessage — with WebSocket broadcast
    // ────────────────────────────────────────

    @Nested
    class DeleteMessage {

        @Test
        void shouldDeleteAndBroadcastToChannelOnly() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            MessageResponse deleted = MessageResponse.builder()
                    .id("msg1").channelId("ch1").threadId(null).deleted(true).build();
            when(messageService.deleteMessage("msg1", 10, "USER")).thenReturn(deleted);

            ResponseEntity<Void> response = controller.deleteMessage("msg1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNull();
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate, never()).convertAndSend(startsWith("/topic/thread/"), any(WebSocketEvent.class));
        }

        @Test
        void shouldBroadcastToBothTopicsWhenInThread() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            MessageResponse deleted = MessageResponse.builder()
                    .id("msg1").channelId("ch1").threadId("t1").deleted(true).build();
            when(messageService.deleteMessage("msg1", 10, "USER")).thenReturn(deleted);

            controller.deleteMessage("msg1", httpRequest);

            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/thread/t1"), any(WebSocketEvent.class));
        }

        @Test
        void shouldEmitMessageIdInDeletePayload() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            MessageResponse deleted = MessageResponse.builder()
                    .id("msg1").channelId("ch1").threadId(null).deleted(true).build();
            when(messageService.deleteMessage("msg1", 10, "USER")).thenReturn(deleted);

            controller.deleteMessage("msg1", httpRequest);

            org.mockito.ArgumentCaptor<WebSocketEvent> captor = org.mockito.ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo("DELETE_MESSAGE");
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> payload = (java.util.Map<String, String>) captor.getValue().getPayload();
            assertThat(payload).containsEntry("messageId", "msg1");
        }

        @Test
        void shouldNotBroadcastWhenDeleteReturnsNull() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(messageService.deleteMessage("msg1", 10, "USER")).thenReturn(null);

            controller.deleteMessage("msg1", httpRequest);

            verifyNoInteractions(messagingTemplate);
        }
    }
}
