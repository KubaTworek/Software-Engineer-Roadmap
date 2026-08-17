package com.example.videostreaming.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public EventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publishTranscodingRequested(VideoEvents.TranscodingRequested event) {
        rabbitTemplate.convertAndSend(props.exchange(), props.transcodingRoutingKey(), event);
    }

    public void publishTranscodingDlq(VideoEvents.TranscodingRequested event) {
        rabbitTemplate.convertAndSend(props.exchange(), props.transcodingDlqRoutingKey(), event);
    }

    public void publishQoe(VideoEvents.QoePlaybackEvent event) {
        rabbitTemplate.convertAndSend(props.exchange(), props.qoeRoutingKey(), event);
    }

    public void publishLiveStart(VideoEvents.LiveStartRequested event) {
        rabbitTemplate.convertAndSend(props.exchange(), props.liveStartRoutingKey(), event);
    }

    public void publishLiveStop(VideoEvents.LiveStopRequested event) {
        rabbitTemplate.convertAndSend(props.exchange(), props.liveStopRoutingKey(), event);
    }

    public void publishLiveDlq(Object event) {
        rabbitTemplate.convertAndSend(props.exchange(), props.liveDlqRoutingKey(), event);
    }
}
