package com.example.paymentprocessing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest
@TestPropertySource(properties = {
    "MAIL_USERNAME=test@example.com",
    "MAIL_PASSWORD=test-password"
})
class PaymentprocessingApplicationTests {

    @Test
    void contextLoads() {
    }

}