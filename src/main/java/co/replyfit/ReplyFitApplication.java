package co.replyfit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReplyFitApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReplyFitApplication.class, args);
    }
}
