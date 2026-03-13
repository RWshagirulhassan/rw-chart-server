package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.PublisherProperties;
import com.readywealth.trading.trade_engine.marketdata.domain.IndexQuote;
import com.readywealth.trading.trade_engine.marketdata.domain.TickQuote;
import com.readywealth.trading.trade_engine.marketdata.domain.TypedTick;
import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import com.zerodhatech.models.Tick;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TickDispatcher {
	private static final Logger log = LoggerFactory.getLogger(TickDispatcher.class);

	private final BlockingQueue<List<Tick>> queue = new LinkedBlockingQueue<>(1000);
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicLong lastTickLogMs = new AtomicLong(0);
	private final PublisherProperties publisherProperties;
	private final TickMapper tickMapper;
	private final TicksWebSocketBroadcaster webSocketBroadcaster;
	private final TickToSeriesIngestService tickToSeriesIngestService;
	private final Counter tickRateCounter;
	private final Counter enqueueInterruptedCounter;

	private ExecutorService workers;

	public TickDispatcher(
		PublisherProperties publisherProperties,
		TickMapper tickMapper,
		TicksWebSocketBroadcaster webSocketBroadcaster,
		TickToSeriesIngestService tickToSeriesIngestService,
		MeterRegistry meterRegistry
	) {
		this.publisherProperties = publisherProperties;
		this.tickMapper = tickMapper;
		this.webSocketBroadcaster = webSocketBroadcaster;
		this.tickToSeriesIngestService = tickToSeriesIngestService;
		this.tickRateCounter = meterRegistry.counter("tick_rate");
		this.enqueueInterruptedCounter = meterRegistry.counter("tick_enqueue_interrupted");
		Gauge.builder("tick_dispatch_queue_depth", queue, BlockingQueue::size).register(meterRegistry);
	}

	@PostConstruct
	public void start() {
		workers = Executors.newFixedThreadPool(Math.max(1, publisherProperties.getDispatcherWorkers()));
		for (int i = 0; i < Math.max(1, publisherProperties.getDispatcherWorkers()); i++) {
			workers.submit(this::runLoop);
		}
	}

	@PreDestroy
	public void stop() {
		running.set(false);
		if (workers != null) {
			workers.shutdownNow();
		}
	}

	public void enqueue(ArrayList<Tick> ticks) {
		if (ticks == null || ticks.isEmpty()) {
			return;
		}
		try {
			queue.put(new ArrayList<>(ticks));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			enqueueInterruptedCounter.increment();
			log.error("tick_enqueue_interrupted batchSize={}", ticks.size());
		}
	}

	private void runLoop() {
		while (running.get()) {
			try {
				List<Tick> ticks = queue.poll(1, TimeUnit.SECONDS);
				if (ticks == null || ticks.isEmpty()) {
					continue;
				}
				tickRateCounter.increment(ticks.size());
				for (Tick tick : ticks) {
					TypedTick typedTick = tickMapper.map(tick);
					tickToSeriesIngestService.ingest(tick, typedTick);
				}
				logSample(ticks);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception ex) {
				log.warn("tick_dispatch_error reason='{}'", ex.getMessage());
				webSocketBroadcaster.broadcastErrorAll(ex.getMessage());
			}
		}
	}

	private void logSample(List<Tick> ticks) {
		long now = System.currentTimeMillis();
		long previous = lastTickLogMs.get();
		if (now - previous < publisherProperties.getLogSampleIntervalMs() || !lastTickLogMs.compareAndSet(previous, now)) {
			return;
		}
		TypedTick typed = tickMapper.map(ticks.get(0));
		TickQuote quote = typed.tickQuote();
		if (quote != null) {
			log.info("tick_sample type=tradeable token={} ltp={} mode={}", quote.getInstrumentToken(), quote.getLastTradedPrice(), quote.getMode());
			return;
		}
		IndexQuote index = typed.indexQuote();
		if (index != null) {
			log.info("tick_sample type=index token={} ltp={} mode={}", index.getInstrumentToken(), index.getLastTradedPrice(), index.getMode());
		}
	}
}
