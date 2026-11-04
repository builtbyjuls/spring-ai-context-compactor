package com.builtbyjuls.clearing.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class MarginCallPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MarginCallPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMarginCall(MarginCallEvent event) {
        rabbitTemplate.convertAndSend(
                ClearingAmqpConfig.MARGIN_DIRECT_EXCHANGE,
                ClearingAmqpConfig.MARGIN_CALL_ROUTING_KEY,
                event
        );
    }
}
