package com.analoghub.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
// THÊM ĐOẠN NÀY ĐỂ HIBERNATE QUÉT ĐƯỢC ENTITY CỦA SPRING MODULITH OUTBOX
@EntityScan(basePackages = {
    "com.analoghub.platform",                   // Package code gốc của dự án bạn
    "org.springframework.modulith.events.jpa"   // Package chứa entity DefaultJpaEventPublication
})
public class AnaloghubBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnaloghubBackendApplication.class, args);
    }
}