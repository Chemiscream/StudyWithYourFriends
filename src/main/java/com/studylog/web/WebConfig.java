package com.studylog.web;

import com.studylog.media.MediaService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/** 把磁盘上的 media 目录挂到 /media/**，文件名含内容哈希，可长期缓存。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MediaService media;

    public WebConfig(MediaService media) {
        this.media = media;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = media.root().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
