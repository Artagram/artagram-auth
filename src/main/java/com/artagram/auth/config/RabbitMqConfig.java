package com.artagram.auth.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange artagramEventsExchange(
            @Value("${app.rabbitmq.exchange:artagram.events}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue authSubscriptionUpdatedQueue() {
        return QueueBuilder.durable("auth.subscription.updated")
                .withArgument("x-dead-letter-exchange", "artagram.dlx")
                .withArgument("x-dead-letter-routing-key", "auth.subscription.updated.dlq")
                .build();
    }

    @Bean
    public Binding authSubscriptionUpdatedBinding(Queue authSubscriptionUpdatedQueue,
                                                  TopicExchange artagramEventsExchange) {
        return BindingBuilder.bind(authSubscriptionUpdatedQueue)
                .to(artagramEventsExchange)
                .with("subscription.updated");
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
