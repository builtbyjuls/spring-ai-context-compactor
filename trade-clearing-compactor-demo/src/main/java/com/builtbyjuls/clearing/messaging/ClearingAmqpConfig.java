package com.builtbyjuls.clearing.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClearingAmqpConfig {

    public static final String TRADE_TOPIC_EXCHANGE = "clearing.trade.topic";
    public static final String MARGIN_DIRECT_EXCHANGE = "clearing.margin.direct";
    public static final String DEAD_LETTER_EXCHANGE = "clearing.dlx";

    public static final String TRADE_INBOUND_QUEUE = "clearing.trade.inbound.q";
    public static final String MARGIN_CALL_QUEUE = "clearing.margin.call.q";
    public static final String TRADE_DLQ = "clearing.trade.dlq";

    public static final String TRADE_SUBMITTED_ROUTING_KEY = "clearing.trade.submitted.#";
    public static final String MARGIN_CALL_ROUTING_KEY = "clearing.margin.call";
    public static final String TRADE_DLQ_ROUTING_KEY = "clearing.trade.dlq";

    @Bean
    public TopicExchange tradeTopicExchange() {
        return ExchangeBuilder.topicExchange(TRADE_TOPIC_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange marginDirectExchange() {
        return ExchangeBuilder.directExchange(MARGIN_DIRECT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue tradeInboundQueue() {
        return QueueBuilder.durable(TRADE_INBOUND_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TRADE_DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 60000L)
                .build();
    }

    @Bean
    public Queue marginCallQueue() {
        return QueueBuilder.durable(MARGIN_CALL_QUEUE).build();
    }

    @Bean
    public Queue tradeDlq() {
        return QueueBuilder.durable(TRADE_DLQ).build();
    }

    @Bean
    public Binding tradeInboundBinding(Queue tradeInboundQueue, TopicExchange tradeTopicExchange) {
        return BindingBuilder.bind(tradeInboundQueue).to(tradeTopicExchange).with(TRADE_SUBMITTED_ROUTING_KEY);
    }

    @Bean
    public Binding marginCallBinding(Queue marginCallQueue, DirectExchange marginDirectExchange) {
        return BindingBuilder.bind(marginCallQueue).to(marginDirectExchange).with(MARGIN_CALL_ROUTING_KEY);
    }

    @Bean
    public Binding tradeDlqBinding(Queue tradeDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(tradeDlq).to(deadLetterExchange).with(TRADE_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter("*");
    }
}
