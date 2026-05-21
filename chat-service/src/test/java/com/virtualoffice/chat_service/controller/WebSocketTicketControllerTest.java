package com.virtualoffice.chat_service.controller;

import com.virtualoffice.chat_service.service.WebSocketTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.virtualoffice.chat_service.dto.response.WebSocketTicketResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketTicketControllerTest {

    @Mock
    private WebSocketTicketService webSocketTicketService;

    @InjectMocks
    private WebSocketTicketController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    @Nested
    class CreateTicket {

        @Test
        void shouldReturnTicketForUser() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(webSocketTicketService.createTicket(10, "USER")).thenReturn("ticket-abc");

            ResponseEntity<WebSocketTicketResponse> response = controller.createTicket(httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTicket()).isEqualTo("ticket-abc");
        }

        @Test
        void shouldReturnTicketForAdmin() {
            HttpServletRequest httpRequest = mockRequest("42", "ADMIN");
            when(webSocketTicketService.createTicket(42, "ADMIN")).thenReturn("admin-ticket");

            ResponseEntity<WebSocketTicketResponse> response = controller.createTicket(httpRequest);

            assertThat(response.getBody().getTicket()).isEqualTo("admin-ticket");
        }

        @Test
        void shouldPassRoleToService() {
            HttpServletRequest httpRequest = mockRequest("5", "USER");
            when(webSocketTicketService.createTicket(5, "USER")).thenReturn("t");

            controller.createTicket(httpRequest);

            verify(webSocketTicketService).createTicket(5, "USER");
        }
    }
}
