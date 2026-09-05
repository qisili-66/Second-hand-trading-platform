package com.example.Second_hand.trading.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.mybatis.spring.annotation.MapperScan;

@MapperScan("com.example.Second_hand.trading.platform.mapper")
@SpringBootApplication
@EnableScheduling
public class SecondHandTradingPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecondHandTradingPlatformApplication.class, args);
	}

}
