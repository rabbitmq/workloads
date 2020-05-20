package com.pivotal.rabbitmq.example;

import com.pivotal.rabbitmq.stompws.RabbitStompWsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.concurrent.CompletionStage;

@Controller
public class RestController {

    @Autowired
    @Qualifier("stompForRest")
    RabbitStompWsClient rabbit;

    @PostMapping("/send")
    public String sendMessage(@RequestParam String destination, @RequestBody String body) {
        CompletionStage<String> result = rabbit.send(destination, body).thenApply(o ->  "ok")
                .exceptionally(throwable -> "failed");
        try {
            return result.toCompletableFuture().get();
        } catch (Exception e) {
            return "failed";
        }
    }
}
