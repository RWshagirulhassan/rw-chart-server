package com.readywealth.trading.trade_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.readywealth.trading.trade_engine.auth.infrastructure.config.InternalApiProperties;
import com.readywealth.trading.trade_engine.auth.infrastructure.config.KiteProperties;
import com.readywealth.trading.trade_engine.auth.infrastructure.config.TokenExpiryProperties;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapProperties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapSchedulerProperties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapV2Properties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.KiteSocketProperties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.PublisherProperties;
import com.readywealth.trading.trade_engine.session.infrastructure.config.StressSessionProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
	KiteProperties.class,
	TokenExpiryProperties.class,
	InternalApiProperties.class,
	KiteSocketProperties.class,
	PublisherProperties.class,
	BootstrapProperties.class,
	BootstrapSchedulerProperties.class,
	BootstrapV2Properties.class,
	TradingProperties.class,
	StressSessionProperties.class
})
public class ReadyTradeEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReadyTradeEngineApplication.class, args);
	}

}
