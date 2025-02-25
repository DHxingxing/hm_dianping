package com.hmdp.mq;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import static com.hmdp.mq.RabbitMQConstants.*;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange seckillExchange(){
        return new DirectExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Queue seckillQueue(){
        return new Queue(SECKILL_QUEUE);
    }

    @Bean
    public Binding binding(){
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

//
//    @Bean
//    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(){
//        return new Jackson2JsonMessageConverter();
//    }

//    @Bean
//    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
//        return new RabbitTemplate(connectionFactory);
//    }

}
