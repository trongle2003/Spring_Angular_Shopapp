package com.project.shopapp.controllers;

import com.project.shopapp.Producers.MessageProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RabbitTestController {

    private final MessageProducer messageProducer;

    @PostMapping("/send")
    public ResponseEntity<String> send(@RequestBody String message) {
        messageProducer.sendMessage(message);
        return ResponseEntity.ok("Message sent to RabbitMQ: " + message);
    }
}
