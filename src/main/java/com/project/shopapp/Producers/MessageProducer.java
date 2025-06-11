package com.project.shopapp.Producers;

import com.project.shopapp.configurations.ConfigRabbitmq;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendMessage(String message) {
        rabbitTemplate.convertAndSend(
                ConfigRabbitmq.EXCHANGE,
                ConfigRabbitmq.ROUTING_KEY,
                message
        );
    }

}
