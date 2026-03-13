package com.readywealth.trading.trade_engine.marketdata.domain;

public class IndexQuote {
	private Long instrumentToken;
	private String mode;
	private Boolean tradable;
	private Double lastTradedPrice;
	private Double highPrice;
	private Double lowPrice;
	private Double openPrice;
	private Double closePrice;
	private Double change;
	private Long tickTimestamp;

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

	public Boolean getTradable() {
		return tradable;
	}

	public void setTradable(Boolean tradable) {
		this.tradable = tradable;
	}

	public Double getLastTradedPrice() {
		return lastTradedPrice;
	}

	public void setLastTradedPrice(Double lastTradedPrice) {
		this.lastTradedPrice = lastTradedPrice;
	}

	public Double getHighPrice() {
		return highPrice;
	}

	public void setHighPrice(Double highPrice) {
		this.highPrice = highPrice;
	}

	public Double getLowPrice() {
		return lowPrice;
	}

	public void setLowPrice(Double lowPrice) {
		this.lowPrice = lowPrice;
	}

	public Double getOpenPrice() {
		return openPrice;
	}

	public void setOpenPrice(Double openPrice) {
		this.openPrice = openPrice;
	}

	public Double getClosePrice() {
		return closePrice;
	}

	public void setClosePrice(Double closePrice) {
		this.closePrice = closePrice;
	}

	public Double getChange() {
		return change;
	}

	public void setChange(Double change) {
		this.change = change;
	}

	public Long getTickTimestamp() {
		return tickTimestamp;
	}

	public void setTickTimestamp(Long tickTimestamp) {
		this.tickTimestamp = tickTimestamp;
	}
}
