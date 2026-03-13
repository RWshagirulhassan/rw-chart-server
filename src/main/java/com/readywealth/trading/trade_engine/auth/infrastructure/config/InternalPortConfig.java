package com.readywealth.trading.trade_engine.auth.infrastructure.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalPortConfig {
	@Bean
	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> additionalConnectorCustomizer(
		InternalApiProperties internalApiProperties
	) {
		return factory -> {
			if (internalApiProperties.getPort() > 0) {
				factory.addAdditionalConnectors(createInternalConnector(internalApiProperties.getPort()));
			}
		};
	}

	private Connector createInternalConnector(int port) {
		Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
		connector.setScheme("http");
		connector.setSecure(false);
		connector.setPort(port);
		return connector;
	}
}
