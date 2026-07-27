package com.example.Second_hand.trading.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
	@Test
	void rejectsMissingOrWeakSigningSecrets() {
		assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 120));
	}

	@Test
	void signsAndParsesAUserTokenWithAConfiguredSecret() {
		JwtService service = new JwtService("this-is-a-test-secret-with-more-than-thirty-two-characters", 120);

		String token = service.createUserToken(7L, "student-7");
		JwtService.JwtClaims claims = service.parseAuthorization("Bearer " + token).orElseThrow();

		assertEquals(7L, claims.id());
		assertEquals("USER", claims.type());
	}
}
