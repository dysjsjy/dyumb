package com.dy.umb.aop;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class BloomFilterConfig {

    private final RedissonClient redissonClient;

    public BloomFilterConfig(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Bean(name = "teamBloomFilter")
    public RBloomFilter<String> teamBloomFilter() {
        return redissonClient.getBloomFilter("teamBloomFilter");
    }

    @PostConstruct
    public void initBloomFilter() {
        RBloomFilter<String> bloomFilter = teamBloomFilter();
        // 初始化布隆过滤器，设置预计元素数量和误判率
        boolean initialized = bloomFilter.tryInit(100000L, 0.01); // 预计 10 万条数据，误判率 1%
        if (initialized) {
            log.info("布隆过滤器初始化完成: teamBloomFilter");
        } else {
            log.warn("布隆过滤器已存在，未重复初始化: teamBloomFilter");
        }
    }
}