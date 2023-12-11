package com.jiangwei.flv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author jiangwei97@aliyun.com
 * @since 2023/12/11 9:42
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		// 线程池大小
		scheduler.setPoolSize(10);
		// 线程名字前缀
		scheduler.setThreadNamePrefix("task-thread-");
		return scheduler;
	}

}
