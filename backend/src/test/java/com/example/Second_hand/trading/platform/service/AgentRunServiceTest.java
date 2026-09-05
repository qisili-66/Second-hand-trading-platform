package com.example.Second_hand.trading.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import com.example.Second_hand.trading.platform.exception.AgentServiceException;
import com.example.Second_hand.trading.platform.exception.AgentServiceException.Reason;

class AgentRunServiceTest {
	@Test
	void authenticatedUserIdOverridesBrowserSuppliedUserIdAndWritesAuditRecords() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AgentService agentService = mock(AgentService.class);
		when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
		Map<String, Object> agentResult = new LinkedHashMap<>();
		agentResult.put("model", "rule-fallback");
		agentResult.put("recommendations", List.of());
		agentResult.put("steps", List.of(Map.of(
				"type", "tool", "tool", "search_items", "input", "{\"phone\":\"123\"}",
				"output", "{}", "status", "FAILED", "durationMs", 12, "errorCode", "tool_unavailable")));
		when(agentService.buyerRun(any())).thenReturn(agentResult);
		AgentRunService service = new AgentRunService(jdbcTemplate, agentService, new ObjectMapper(), 90);

		service.createBuyerRun(101L, new LinkedHashMap<>(Map.of("message", "想买 iPad", "userId", 999L)));

		ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
		verify(agentService).buyerRun(requestCaptor.capture());
		assertEquals(101L, requestCaptor.getValue().get("userId"));
		assertEquals("想买 iPad", requestCaptor.getValue().get("message"));
		List<String> sql = updateSql(jdbcTemplate);
		assertEquals(true, sql.stream().anyMatch(value -> value.contains("INSERT INTO agent_runs")));
		assertEquals(true, sql.stream().anyMatch(value -> value.contains("INSERT INTO agent_steps")));
		assertEquals(true, sql.stream().anyMatch(value -> value.contains("SET status = 'SUCCEEDED'")));
	}

	@Test
	void excludesUnavailableRecommendationDuringRealtimeVerification() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AgentService agentService = mock(AgentService.class);
		when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
		Map<String, Object> agentResult = new LinkedHashMap<>();
		agentResult.put("model", "rule-fallback");
		agentResult.put("recommendations", List.of(Map.of("item_id", 88L, "price", 1000, "reason", "推荐")));
		agentResult.put("steps", List.of());
		when(agentService.buyerRun(any())).thenReturn(agentResult);
		AgentRunService service = new AgentRunService(jdbcTemplate, agentService, new ObjectMapper(), 90);

		Map<String, Object> result = service.createBuyerRun(101L, Map.of("message", "想买平板"));

		assertEquals(List.of(), result.get("recommendations"));
		assertEquals(false, updateSql(jdbcTemplate).stream().anyMatch(value -> value.contains("INSERT INTO agent_recommendations")));
	}

	@Test
	void excludesRecommendationWhenRealtimePriceChanged() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AgentService agentService = mock(AgentService.class);
		when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
				.thenReturn(List.of(Map.of("itemId", 88L, "title", "平板", "price", 1200, "conditionLevel", "GOOD", "campus", "校本部")));
		Map<String, Object> agentResult = new LinkedHashMap<>();
		agentResult.put("model", "rule-fallback");
		agentResult.put("recommendations", List.of(Map.of("item_id", 88L, "price", 1000, "reason", "推荐")));
		agentResult.put("steps", List.of());
		when(agentService.buyerRun(any())).thenReturn(agentResult);
		AgentRunService service = new AgentRunService(jdbcTemplate, agentService, new ObjectMapper(), 90);

		Map<String, Object> result = service.createBuyerRun(101L, Map.of("message", "想买平板"));

		assertEquals(List.of(), result.get("recommendations"));
		assertEquals(false, updateSql(jdbcTemplate).stream().anyMatch(value -> value.contains("INSERT INTO agent_recommendations")));
	}

	@Test
	void savesRecommendationSnapshotOnlyAfterRealtimePriceVerification() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AgentService agentService = mock(AgentService.class);
		when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
				.thenReturn(List.of(Map.of("itemId", 88L, "title", "平板", "price", 1000, "conditionLevel", "GOOD", "campus", "校本部")));
		Map<String, Object> agentResult = new LinkedHashMap<>();
		agentResult.put("model", "rule-fallback");
		agentResult.put("recommendations", List.of(Map.of("item_id", 88L, "price", 1000, "reason", "推荐")));
		agentResult.put("steps", List.of());
		when(agentService.buyerRun(any())).thenReturn(agentResult);
		AgentRunService service = new AgentRunService(jdbcTemplate, agentService, new ObjectMapper(), 90);

		Map<String, Object> result = service.createBuyerRun(101L, Map.of("message", "想买平板"));

		assertEquals(1, ((List<?>) result.get("recommendations")).size());
		assertEquals(true, updateSql(jdbcTemplate).stream().anyMatch(value -> value.contains("INSERT INTO agent_recommendations")));
	}

	@Test
	void returnsBasicFilterAndPersistsFailedRunWhenAiServiceIsUnavailable() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		AgentService agentService = mock(AgentService.class);
		when(agentService.buyerRun(any())).thenThrow(new AgentServiceException(Reason.UNAVAILABLE, "unavailable"));
		AgentRunService service = new AgentRunService(jdbcTemplate, agentService, new ObjectMapper(), 90);

		Map<String, Object> result = service.createBuyerRun(101L, Map.of("message", "想买平板"));

		assertEquals("basic-filter", result.get("mode"));
		assertEquals(List.of(), result.get("recommendations"));
		assertEquals("UNAVAILABLE", result.get("failureReason"));
		List<String> sql = updateSql(jdbcTemplate);
		assertEquals(true, sql.stream().anyMatch(value -> value.contains("INSERT INTO agent_steps")));
		assertEquals(true, sql.stream().anyMatch(value -> value.contains("SET status = 'FAILED'")));
	}

	@Test
	void orderToolUsesTheAuthenticatedUserForBothParticipantChecks() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
		AgentRunService service = new AgentRunService(jdbcTemplate, mock(AgentService.class), new ObjectMapper(), 90);

		ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
				service.orderStatus(Map.of("orderId", 55L, "userId", 101L)));

		assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
		Object[] args = lastQueryArguments(jdbcTemplate);
		assertEquals(55L, args[0]);
		assertEquals(101L, args[1]);
		assertEquals(101L, args[2]);
	}

	@Test
	void runLookupDoesNotExposeAnotherUsersRun() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
		AgentRunService service = new AgentRunService(jdbcTemplate, mock(AgentService.class), new ObjectMapper(), 90);

		ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.runForUser(202L, "run-owned-by-101"));

		assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
		Object[] args = lastQueryArguments(jdbcTemplate);
		assertEquals("run-owned-by-101", args[0]);
		assertEquals(202L, args[1]);
	}

	@Test
	void clearRunsDeletesOnlyTheAuthenticatedUsersRecords() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(101L))).thenReturn(List.of());
		AgentRunService service = new AgentRunService(jdbcTemplate, mock(AgentService.class), new ObjectMapper(), 90);

		service.clearRunsForUser(101L);

		verify(jdbcTemplate).update("DELETE FROM agent_runs WHERE user_id = ?", 101L);
	}

	private List<String> updateSql(JdbcTemplate jdbcTemplate) {
		return org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
				.filter(invocation -> invocation.getMethod().getName().equals("update"))
				.map(invocation -> String.valueOf(invocation.getArguments()[0]))
				.toList();
	}

	private Object[] lastQueryArguments(JdbcTemplate jdbcTemplate) {
		List<org.mockito.invocation.Invocation> invocations = new ArrayList<>(org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations());
		return invocations.stream()
				.filter(invocation -> invocation.getMethod().getName().equals("queryForList"))
				.reduce((first, second) -> second)
				.orElseThrow()
				.getRawArguments()[1] instanceof Object[] values ? values : new Object[0];
	}
}
