package com.receipttrust;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReceiptTrustApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceiptTrustApplication.class, args);
    }
}
