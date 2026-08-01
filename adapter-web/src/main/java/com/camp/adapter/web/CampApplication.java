package com.camp.adapter.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 애플리케이션 조립 지점. runtimeOnly 로 올라온 infra 와 다른 어댑터까지 스캔한다. */
@SpringBootApplication(scanBasePackages = "com.camp")
public class CampApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampApplication.class, args);
    }
}
