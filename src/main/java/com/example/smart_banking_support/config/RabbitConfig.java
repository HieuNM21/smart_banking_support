package com.example.smart_banking_support.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String TICKET_QUEUE = "ticket.created.queue";
    public static final String TICKET_EXCHANGE = "ticket.exchange";
    public static final String TICKET_ROUTING_KEY = "ticket.routing.key";

    @Bean
    public Queue queue() {
        return new Queue(TICKET_QUEUE, true);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(TICKET_EXCHANGE);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(TICKET_ROUTING_KEY);
    }

    // Cấu hình để gửi Object (DTO) dưới dạng JSON thay vì byte array
    @Bean
    public Jackson2JsonMessageConverter producerJackson2MessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(producerJackson2MessageConverter());
        return rabbitTemplate;
    }
}