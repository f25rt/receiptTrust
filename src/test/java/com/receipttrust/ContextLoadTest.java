package com.receipttrust;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContextLoadTest {

    @Test
    void contextLoads() {
        // Verifies the full Spring context wires up and JPA entities map against
        // the H2 (PostgreSQL-mode) schema created via ddl-auto.
    }
}
