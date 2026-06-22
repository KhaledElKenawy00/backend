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
package com.virtualoffice.service.user.security;

import com.virtualoffice.service.user.domain.entity.User;
import com.virtualoffice.service.user.domain.enumuration.AccountStatus;
import com.virtualoffice.service.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    private User user(AccountStatus status, boolean disabled) {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setPassword("hashed");
        u.setAccountStatus(status);
        u.setDisabled(disabled);
        return u;
    }

    @Test
    void loadsActiveUser() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user(AccountStatus.ACTIVE, false)));

        UserDetails details = service.loadUserByUsername("user@example.com");

        assertThat(details.getUsername()).isEqualTo("user@example.com");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void suspendedUserIsLocked() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user(AccountStatus.SUSPENDED, false)));

        UserDetails details = service.loadUserByUsername("user@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void disabledUserIsRejected() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user(AccountStatus.ACTIVE, true)));

        assertThatThrownBy(() -> service.loadUserByUsername("user@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void missingUserIsRejected() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
