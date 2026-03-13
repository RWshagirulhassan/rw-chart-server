package com.readywealth.trading.trade_engine.auth.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiFilter extends OncePerRequestFilter {
	private static final String INTERNAL_PREFIX = "/internal/";
	private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

	private final InternalApiProperties internalApiProperties;

	public InternalApiFilter(InternalApiProperties internalApiProperties) {
		this.internalApiProperties = internalApiProperties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path == null || !path.startsWith(INTERNAL_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		if (internalApiProperties.getPort() <= 0) {
			response.sendError(HttpStatus.NOT_FOUND.value());
			return;
		}

		if (request.getServerPort() != internalApiProperties.getPort()) {
			response.sendError(HttpStatus.FORBIDDEN.value(), "Internal API available only on internal port");
			return;
		}

		String expected = internalApiProperties.getApiKey();
		String actual = request.getHeader(INTERNAL_KEY_HEADER);
		if (expected == null || expected.isBlank() || !expected.equals(actual)) {
			response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid internal API key");
			return;
		}

		filterChain.doFilter(request, response);
	}
}
