package com.project.shopapp.Consumers;


import com.project.shopapp.configurations.ConfigRabbitmq;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class MessageConsumer {

    @RabbitListener(queues = ConfigRabbitmq.QUEUE)
    public void receiveMessage(String message) {

        System.out.println(">> [RabbitMQ] Received: " + message);
    }


}
