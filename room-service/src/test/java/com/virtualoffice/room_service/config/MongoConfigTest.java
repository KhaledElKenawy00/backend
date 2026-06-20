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
package com.virtualoffice.room_service.config;

import com.mongodb.ConnectionString;
import org.junit.jupiter.api.Test;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    @Test
    void connectionDetailsShouldCarryCredentialsFromUri() {
        MongoConnectionDetails details = new MongoConfig().mongoConnectionDetails(
                "mongodb://root:rootpassword@localhost:27017/room_service?authSource=admin");

        ConnectionString cs = details.getConnectionString();

        assertThat(cs.getUsername()).isEqualTo("root");
        assertThat(cs.getPassword()).isEqualTo("rootpassword".toCharArray());
        assertThat(cs.getDatabase()).isEqualTo("room_service");
        assertThat(cs.getCredential()).isNotNull();
        assertThat(cs.getCredential().getSource()).isEqualTo("admin");
    }
}
