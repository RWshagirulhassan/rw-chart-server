package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.domain.Depth;
import com.readywealth.trading.trade_engine.marketdata.domain.IndexQuote;
import com.readywealth.trading.trade_engine.marketdata.domain.TickQuote;
import com.readywealth.trading.trade_engine.marketdata.domain.TypedTick;
import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TickMapper {
	private static final Logger log = LoggerFactory.getLogger(TickMapper.class);
	private final AtomicLong lastFractionalWarnMs = new AtomicLong(0);

	public TypedTick map(Tick tick) {
		if (tick == null) {
			return new TypedTick(null, null);
		}
		if (tick.isTradable()) {
			return new TypedTick(toTickQuote(tick), null);
		}
		return new TypedTick(null, toIndexQuote(tick));
	}

	private TickQuote toTickQuote(Tick tick) {
		TickQuote quote = new TickQuote();
		quote.setInstrumentToken(tick.getInstrumentToken());
		quote.setMode(tick.getMode());
		quote.setLastTradedPrice(tick.getLastTradedPrice());
		quote.setTickTimestamp(resolveTimestamp(tick));
		quote.setVolumeTradedToday(safeLongToInt(tick.getVolumeTradedToday(), tick.getInstrumentToken(), "volumeTradedToday"));
		quote.setLastTradedQuantity(safeDoubleToInt(tick.getLastTradedQuantity(), tick.getInstrumentToken(), "lastTradedQuantity"));
		quote.setDepth(mapDepth(tick));
		return quote;
	}

	private IndexQuote toIndexQuote(Tick tick) {
		IndexQuote quote = new IndexQuote();
		quote.setInstrumentToken(tick.getInstrumentToken());
		quote.setMode(tick.getMode());
		quote.setTradable(tick.isTradable());
		quote.setLastTradedPrice(tick.getLastTradedPrice());
		quote.setHighPrice(tick.getHighPrice());
		quote.setLowPrice(tick.getLowPrice());
		quote.setOpenPrice(tick.getOpenPrice());
		quote.setClosePrice(tick.getClosePrice());
		quote.setChange(tick.getChange());
		quote.setTickTimestamp(resolveTimestamp(tick));
		return quote;
	}

	private Long resolveTimestamp(Tick tick) {
		if (tick.getTickTimestamp() != null) {
			return tick.getTickTimestamp().getTime();
		}
		if (tick.getLastTradedTime() != null) {
			return tick.getLastTradedTime().getTime();
		}
		return null;
	}

	private Map<String, List<Depth>> mapDepth(Tick tick) {
		Map<String, ArrayList<com.zerodhatech.models.Depth>> sourceDepth = tick.getMarketDepth();
		if (sourceDepth == null || sourceDepth.isEmpty()) {
			return null;
		}
		Map<String, List<Depth>> mapped = new HashMap<>();
		for (Map.Entry<String, ArrayList<com.zerodhatech.models.Depth>> entry : sourceDepth.entrySet()) {
			List<Depth> values = new ArrayList<>();
			for (com.zerodhatech.models.Depth value : entry.getValue()) {
				values.add(new Depth(
					safeLongToInt(value.getQuantity(), tick.getInstrumentToken(), "depth.quantity"),
					value.getPrice(),
					safeLongToInt(value.getOrders(), tick.getInstrumentToken(), "depth.orders")
				));
			}
			mapped.put(entry.getKey(), values);
		}
		return mapped;
	}

	private Integer safeLongToInt(long value, long token, String fieldName) {
		if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
			log.warn("tick_field_overflow token={} field={} value={}", token, fieldName, value);
			return null;
		}
		return (int) value;
	}

	private Integer safeDoubleToInt(double value, long token, String fieldName) {
		if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
			log.warn("tick_field_overflow token={} field={} value={}", token, fieldName, value);
			return null;
		}
		if (value % 1 != 0) {
			int normalized = (int) Math.floor(value);
			long now = System.currentTimeMillis();
			long previous = lastFractionalWarnMs.get();
			if (now - previous >= 5000 && lastFractionalWarnMs.compareAndSet(previous, now)) {
				log.warn("tick_field_fractional_normalized token={} field={} value={} normalized={}",
						token, fieldName, value, normalized);
			}
			return normalized;
		}
		return (int) value;
	}
}
