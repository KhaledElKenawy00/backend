/*
 * Copyright (c) 2025 My Virtual Office
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package com.virtualoffice.workspace.controller;

import com.virtualoffice.workspace.dto.request.InviteMemberRequest;
import com.virtualoffice.workspace.dto.response.InvitationResponse;
import com.virtualoffice.workspace.service.InvitationService;
import com.virtualoffice.workspace.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/workspace/{workspaceId}/invitations")
    public ResponseEntity<InvitationResponse> invite(@PathVariable Long workspaceId,
                                                     @Valid @RequestBody InviteMemberRequest request,
                                                     HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invitationService.invite(workspaceId, request, userId));
    }

    @GetMapping("/api/workspace/{workspaceId}/invitations")
    public List<InvitationResponse> list(@PathVariable Long workspaceId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return invitationService.getInvitations(workspaceId, userId);
    }

    @DeleteMapping("/api/workspace/{workspaceId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long workspaceId, @PathVariable Long invitationId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        invitationService.revokeInvitation(workspaceId, invitationId, userId);
    }

    // The invitee accepts with their own identity (X-User-Id) — the Desk is created here.
    @PostMapping("/api/invitations/accept")
    public InvitationResponse accept(@RequestParam String token, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return invitationService.acceptInvite(token, userId);
    }

    @PostMapping("/api/invitations/decline")
    public InvitationResponse decline(@RequestParam String token) {
        return invitationService.declineInvite(token);
    }
}
