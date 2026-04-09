package com.hospital.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;

/**
 * 记录应用启停关键事件，便于定位异常重启场景。
 */
@Slf4j
@Component
public class ApplicationLifecycleLogger implements ApplicationListener<ContextClosedEvent> {

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.warn("应用上下文关闭事件触发: time={}, thread={}, pid={}",
                LocalDateTime.now(), Thread.currentThread().getName(), currentPid());
    }

    @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("应用启动完成: time={}, thread={}, pid={}",
                LocalDateTime.now(), Thread.currentThread().getName(), currentPid());
    }

    private String currentPid() {
        try {
            return ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
