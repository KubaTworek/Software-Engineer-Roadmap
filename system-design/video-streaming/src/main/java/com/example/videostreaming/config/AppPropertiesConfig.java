package com.example.videostreaming.config;

import com.example.videostreaming.cdn.CdnProperties;
import com.example.videostreaming.messaging.MessagingProperties;
import com.example.videostreaming.live.LiveProperties;
import com.example.videostreaming.qoe.QoeProperties;
import com.example.videostreaming.personalization.PersonalizationProperties;
import com.example.videostreaming.search.SearchProperties;
import com.example.videostreaming.storage.StorageProperties;
import com.example.videostreaming.transcoding.TranscodingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        StorageProperties.class,
        TranscodingProperties.class,
        MessagingProperties.class,
        SearchProperties.class,
        QoeProperties.class,
        CdnProperties.class,
        PersonalizationProperties.class,
        LiveProperties.class
})
public class AppPropertiesConfig {}
