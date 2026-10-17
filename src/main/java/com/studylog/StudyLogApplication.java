package com.studylog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudyLogApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudyLogApplication.class, args);
    }
}
