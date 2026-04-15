package com.heima.codereview.api.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AiClientTimeoutConfig {

    @Bean
    @Primary
    public RestClient.Builder aiRestClientBuilder(
            @Value("${code-review.ai.connect-timeout-ms:15000}") long connectTimeoutMs,
            @Value("${code-review.ai.read-timeout-ms:120000}") long readTimeoutMs,
            @Value("${code-review.ai.write-timeout-ms:120000}") long writeTimeoutMs,
            @Value("${code-review.ai.call-timeout-ms:180000}") long callTimeoutMs) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .writeTimeout(Duration.ofMillis(writeTimeoutMs))
                .callTimeout(Duration.ofMillis(callTimeoutMs))
                .retryOnConnectionFailure(true)
                .build();
        return RestClient.builder()
                .requestFactory(new OkHttp3ClientHttpRequestFactory(httpClient));
    }
}
