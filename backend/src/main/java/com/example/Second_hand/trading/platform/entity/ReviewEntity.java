package com.example.Second_hand.trading.platform.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("reviews")
public class ReviewEntity {
	@TableId(type = IdType.AUTO)
	private Long id;
	private Long orderId;
	private Long reviewerId;
	private Long targetUserId;
	private Integer rating;
	private String content;
	private LocalDateTime createdAt;
}
