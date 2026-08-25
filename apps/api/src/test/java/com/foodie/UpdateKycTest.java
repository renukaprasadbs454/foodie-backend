package com.foodie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:foodie-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
public class UpdateKycTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
@Test
public void executeUpdate() {
    int rows = jdbcTemplate.update(
            "UPDATE \"delivery_partner\" " +
            "SET \"kyc_status\" = 'VERIFIED' " +
            "WHERE CAST(\"user_credential_id\" AS VARCHAR) LIKE '4317a3a8-ed21%'"
    );

    System.out.println("========== UPDATED KYC ROWS: " + rows + " ==========");
}
}
