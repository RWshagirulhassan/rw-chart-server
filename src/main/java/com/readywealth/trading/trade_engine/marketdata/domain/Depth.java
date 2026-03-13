package com.readywealth.trading.trade_engine.marketdata.domain;

public class Depth {
	private Integer quantity;
	private Double price;
	private Integer orders;

	public Depth() {
	}

	public Depth(Integer quantity, Double price, Integer orders) {
		this.quantity = quantity;
		this.price = price;
		this.orders = orders;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	public Double getPrice() {
		return price;
	}

	public void setPrice(Double price) {
		this.price = price;
	}

	public Integer getOrders() {
		return orders;
	}

	public void setOrders(Integer orders) {
		this.orders = orders;
	}
}
