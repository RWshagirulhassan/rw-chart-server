package com.readywealth.trading.trade_engine.marketdata.domain;

import java.util.List;
import java.util.Map;

public class TickQuote {
	private Long instrumentToken;
	private String mode;
	private Double lastTradedPrice;
	private Long tickTimestamp;
	private Integer volumeTradedToday;
	private Integer lastTradedQuantity;
	private Map<String, List<Depth>> depth;

	public Long getInstrumentToken() {
		return instrumentToken;
	}

	public void setInstrumentToken(Long instrumentToken) {
		this.instrumentToken = instrumentToken;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public Double getLastTradedPrice() {
		return lastTradedPrice;
	}

	public void setLastTradedPrice(Double lastTradedPrice) {
		this.lastTradedPrice = lastTradedPrice;
	}

	public Long getTickTimestamp() {
		return tickTimestamp;
	}

	public void setTickTimestamp(Long tickTimestamp) {
		this.tickTimestamp = tickTimestamp;
	}

	public Integer getVolumeTradedToday() {
		return volumeTradedToday;
	}

	public void setVolumeTradedToday(Integer volumeTradedToday) {
		this.volumeTradedToday = volumeTradedToday;
	}

	public Integer getLastTradedQuantity() {
		return lastTradedQuantity;
	}

	public void setLastTradedQuantity(Integer lastTradedQuantity) {
		this.lastTradedQuantity = lastTradedQuantity;
	}

	public Map<String, List<Depth>> getDepth() {
		return depth;
	}

	public void setDepth(Map<String, List<Depth>> depth) {
		this.depth = depth;
	}
}
