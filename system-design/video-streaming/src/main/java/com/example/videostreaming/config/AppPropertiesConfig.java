package com.example.videostreaming.config;

import com.example.videostreaming.storage.StorageProperties;
import com.example.videostreaming.transcoding.TranscodingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, TranscodingProperties.class})
public class AppPropertiesConfig {}
