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
package com.virtualoffice.chat_service.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors workspace-service's {@code MemberRoleResponse} (GET …/members/{userId}/role). The
 * producer sends {@code Long} ids that deserialize into {@code Integer} here. {@code active}
 * reflects whether the user currently holds an active desk in the workspace; the producer record
 * component serializes as {@code isActive}, mapped explicitly below.
 */
public record WorkspaceMemberRole(
        Integer userId,
        Integer workspaceId,
        WorkspaceRole role,
        @JsonProperty("isActive") boolean active) {
}
