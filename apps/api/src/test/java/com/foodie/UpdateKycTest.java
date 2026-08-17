package com.foodie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
public class UpdateKycTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void executeUpdate() {
        int rows = jdbcTemplate.update(
                "UPDATE delivery_partner SET kyc_status = 'VERIFIED' WHERE user_credential_id::text LIKE '4317a3a8-ed21%'");
        System.out.println("========== UPDATED KYC ROWS: " + rows + " ==========");
    }
}
