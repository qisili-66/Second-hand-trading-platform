package com.example.Second_hand.trading.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

class AgentInsightsServiceTest {
	@Test
	void buyerInsightsScopesEveryQueryToTheAuthenticatedUser() {
		JdbcTemplate jdbcTemplate = configuredJdbcTemplate();
		AgentInsightsService service = new AgentInsightsService(jdbcTemplate);

		service.buyerInsights(101L);

		List<Invocation> calls = queryInvocations(jdbcTemplate);
		assertEquals(5, calls.size());
		for (Invocation call : calls) {
			Object[] args = arguments(call);
			assertEquals(101L, args[0]);
		}
		assertFalse(sql(calls).contains("messages"));
		assertFalse(sql(calls).contains("chat"));
	}

	@Test
	void sellerInsightsScopesEveryQueryToTheAuthenticatedUser() {
		JdbcTemplate jdbcTemplate = configuredJdbcTemplate();
		AgentInsightsService service = new AgentInsightsService(jdbcTemplate);

		service.sellerInsights(202L);

		List<Invocation> calls = queryInvocations(jdbcTemplate);
		assertEquals(7, calls.size());
		for (Invocation call : calls) {
			Object[] args = arguments(call);
			assertEquals(202L, args[0]);
		}
		assertFalse(sql(calls).contains("messages"));
		assertFalse(sql(calls).contains("chat"));
	}

	@Test
	void operationsInsightsUsesOnlyAggregatedAuditDataAndSameUserRecommendationAttribution() {
		JdbcTemplate jdbcTemplate = configuredJdbcTemplate();
		AgentInsightsService service = new AgentInsightsService(jdbcTemplate);

		service.operationsInsights();

		String sql = sql(queryInvocations(jdbcTemplate));
		assertTrue(sql.contains("o.buyer_id = ar.user_id"));
		assertTrue(sql.contains("o.created_at >= r.created_at"));
		assertTrue(sql.contains("agent_runs"));
		assertTrue(sql.contains("agent_steps"));
		assertTrue(sql.contains("agent_recommendations"));
		assertFalse(sql.contains("messages"));
		assertFalse(sql.contains("chat"));
	}

	private JdbcTemplate configuredJdbcTemplate() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Object.class), any(Object[].class))).thenReturn(0L);
		when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
		return jdbcTemplate;
	}

	private List<Invocation> queryInvocations(JdbcTemplate jdbcTemplate) {
		return new ArrayList<>(org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations()).stream()
				.filter(invocation -> invocation.getMethod().getName().equals("queryForObject")
						|| invocation.getMethod().getName().equals("queryForList"))
				.toList();
	}

	private Object[] arguments(Invocation invocation) {
		Object[] raw = invocation.getRawArguments();
		return raw[raw.length - 1] instanceof Object[] values ? values : new Object[0];
	}

	private String sql(List<Invocation> calls) {
		return calls.stream()
				.map(invocation -> String.valueOf(invocation.getArguments()[0]).toLowerCase())
				.reduce("", (left, right) -> left + " " + right);
	}
}
