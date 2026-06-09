package com.example.Second_hand.trading.platform.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("notifications")
public class NotificationEntity {
	@TableId(type = IdType.AUTO)
	private Long id;
	private Long userId;
	private String type;
	private String title;
	private String content;
	private LocalDateTime readAt;
	private LocalDateTime createdAt;
}
