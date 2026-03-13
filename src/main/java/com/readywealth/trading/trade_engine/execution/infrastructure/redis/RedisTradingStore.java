package com.readywealth.trading.trade_engine.execution.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyCommand;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.StrategyExecutionSettings;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisTradingStore implements TradingStorePort {

    private static final DateTimeFormatter RATE_BUCKET_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);

    private static final String LUA_APPLY_FILL = """
            local dedupeAdded = redis.call('SADD', KEYS[1], ARGV[1])
            if dedupeAdded == 0 then
              return cjson.encode({duplicate=true})
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))

            local orderRaw = redis.call('GET', KEYS[2])
            if not orderRaw then
              return cjson.encode({duplicate=false,error='order_not_found'})
            end

            local fill = cjson.decode(ARGV[3])
            local order = cjson.decode(orderRaw)
            local qty = tonumber(fill.qty) or 0
            local price = tonumber(fill.price) or 0
            local fees = tonumber(fill.fees) or 0

            local prevFilled = tonumber(order.filledQty) or 0
            local prevAvg = tonumber(order.avgFillPrice) or 0
            local newFilled = prevFilled + qty
            local newAvg = prevAvg
            if newFilled > 0 then
              newAvg = ((prevFilled * prevAvg) + (qty * price)) / newFilled
            end

            order.filledQty = newFilled
            order.avgFillPrice = newAvg
            order.updatedAtEpochMs = tonumber(ARGV[4])
            local orderQty = tonumber(order.qty) or newFilled
            if newFilled >= orderQty then
              order.status = 'FILLED'
            else
              order.status = 'PARTIAL'
            end

            redis.call('SET', KEYS[2], cjson.encode(order))
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[5]))

            redis.call('SET', KEYS[3], ARGV[3])
            redis.call('EXPIRE', KEYS[3], tonumber(ARGV[6]))
            redis.call('ZADD', KEYS[4], tonumber(fill.timestampEpochMs), fill.fillId)
            redis.call('EXPIRE', KEYS[4], tonumber(ARGV[6]))

            if order.status == 'FILLED' then
              redis.call('SREM', KEYS[7], order.orderId)
            end
            redis.call('ZADD', KEYS[8], tonumber(order.updatedAtEpochMs), order.orderId)

            local posRaw = redis.call('GET', KEYS[5])
            local position
            if posRaw then
              position = cjson.decode(posRaw)
            else
              position = {
                accountId = order.accountId,
                instrumentToken = order.instrumentToken,
                tradingsymbol = order.tradingsymbol,
                product = order.product,
                netQty = 0,
                avgPrice = 0,
                realizedPnl = 0,
                unrealizedPnl = 0,
                lastPrice = price,
                updatedAtEpochMs = tonumber(ARGV[4])
              }
            end

            local signedQty = qty
            if order.side == 'SELL' then
              signedQty = -qty
            end
            local net = tonumber(position.netQty) or 0
            local avg = tonumber(position.avgPrice) or 0
            local realized = tonumber(position.realizedPnl) or 0
            local prevRealized = realized
            local newNet = net + signedQty

            if net == 0 or (net > 0 and signedQty > 0) or (net < 0 and signedQty < 0) then
              if newNet == 0 then
                avg = 0
              else
                avg = ((math.abs(net) * avg) + (math.abs(signedQty) * price)) / math.abs(newNet)
              end
            else
              local closingQty = math.min(math.abs(net), math.abs(signedQty))
              local dir = 1
              if net < 0 then dir = -1 end
              realized = realized + (closingQty * (price - avg) * dir)
              if newNet == 0 then
                avg = 0
              elseif (newNet > 0 and net > 0) or (newNet < 0 and net < 0) then
                avg = avg
              else
                avg = price
              end
            end

            position.netQty = newNet
            position.avgPrice = avg
            position.realizedPnl = realized
            local mark = tonumber(ARGV[7]) or price
            position.lastPrice = mark
            position.unrealizedPnl = (mark - avg) * newNet
            position.updatedAtEpochMs = tonumber(ARGV[4])
            redis.call('SET', KEYS[5], cjson.encode(position))

            local fundsRaw = redis.call('GET', KEYS[6])
            local funds
            if fundsRaw then
              funds = cjson.decode(fundsRaw)
            else
              local opening = tonumber(ARGV[8]) or 0
              funds = {
                availableCash = opening,
                utilisedMargin = 0,
                availableMargin = opening,
                openingBalance = opening,
                collateral = cjson.null,
                updatedAtEpochMs = tonumber(ARGV[4])
              }
            end
            local availableCash = tonumber(funds.availableCash) or 0
            local realizedDelta = realized - prevRealized
            local cashDelta
            if order.product == 'MIS' or order.product == 'NRML' then
              -- Broker-like derivative/intraday ledger: notional is not free cash; only realized PnL and charges impact cash.
              cashDelta = realizedDelta - fees
            else
              -- Delivery-like ledger keeps notional settlement behavior.
              cashDelta = (-(signedQty * price)) - fees
            end
            availableCash = availableCash + cashDelta
            funds.availableCash = availableCash
            local marginRatio = tonumber(ARGV[9]) or 0.2
            local utilised = math.abs(newNet) * mark * marginRatio
            funds.utilisedMargin = utilised
            funds.availableMargin = availableCash - utilised
            funds.updatedAtEpochMs = tonumber(ARGV[4])
            redis.call('SET', KEYS[6], cjson.encode(funds))

            return cjson.encode({duplicate=false,order=order,position=position,funds=funds,fill=fill})
            """;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TradingProperties tradingProperties;
    private final DefaultRedisScript<String> applyFillScript;

    public RedisTradingStore(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            TradingProperties tradingProperties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.tradingProperties = tradingProperties;
        this.applyFillScript = new DefaultRedisScript<>(LUA_APPLY_FILL, String.class);
    }

    @Override
    public TradingAccount saveAccount(TradingAccount account) {
        writeJson(accountKey(account.accountId()), account, 0);
        redis.opsForValue().set(accountByUserKey(account.userId()), account.accountId());
        redis.opsForSet().add(accountModeSet(account.mode()), account.accountId());
        return account;
    }

    @Override
    public Optional<TradingAccount> findAccountById(String accountId) {
        return readJson(accountKey(accountId), TradingAccount.class);
    }

    @Override
    public Optional<TradingAccount> findAccountByUserId(String userId) {
        String accountId = redis.opsForValue().get(accountByUserKey(userId));
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return findAccountById(accountId);
    }

    @Override
    public List<TradingAccount> listAccountsByMode(TradingMode mode) {
        Set<String> ids = redis.opsForSet().members(accountModeSet(mode));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<TradingAccount> out = new ArrayList<>();
        for (String id : ids) {
            findAccountById(id).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public TradingOrder saveOrder(TradingOrder order) {
        writeJson(orderKey(order.accountId(), order.orderId()), order, tradingProperties.getRetention().getOrderTtlSeconds());
        redis.opsForZSet().add(orderByInstrumentKey(order.accountId(), order.instrumentToken()), order.orderId(),
                order.updatedAtEpochMs());
        if (order.status().terminal()) {
            redis.opsForSet().remove(openOrdersKey(order.accountId()), order.orderId());
        } else {
            redis.opsForSet().add(openOrdersKey(order.accountId()), order.orderId());
        }
        if (order.brokerOrderId() != null && !order.brokerOrderId().isBlank()) {
            redis.opsForHash().put(orderMapKey(order.accountId()), order.orderId(), order.brokerOrderId());
        }
        return order;
    }

    @Override
    public Optional<TradingOrder> findOrder(String accountId, String orderId) {
        return readJson(orderKey(accountId, orderId), TradingOrder.class);
    }

    @Override
    public List<TradingOrder> listOrders(String accountId, TradingOrderStatus status) {
        List<TradingOrder> out = new ArrayList<>();
        if (status == TradingOrderStatus.OPEN) {
            Set<String> ids = redis.opsForSet().members(openOrdersKey(accountId));
            if (ids != null) {
                for (String id : ids) {
                    Optional<TradingOrder> found = findOrder(accountId, id);
                    if (found.isPresent()) {
                        out.add(found.get());
                    } else {
                        redis.opsForSet().remove(openOrdersKey(accountId), id);
                    }
                }
            }
        } else {
            Set<String> keys = redis.keys(orderKeyPattern(accountId));
            if (keys != null) {
                for (String key : keys) {
                    readJsonRaw(key, TradingOrder.class).ifPresent(out::add);
                }
            }
        }

        if (status == TradingOrderStatus.OPEN) {
            out = out.stream().filter(item -> !item.status().terminal()).toList();
        } else if (status != null) {
            out = out.stream().filter(item -> item.status() == status).toList();
        }
        return out.stream()
                .sorted(Comparator.comparingLong(TradingOrder::createdAtEpochMs).reversed())
                .toList();
    }

    @Override
    public TradingFundsView saveFunds(String accountId, TradingFundsView fundsView) {
        writeJson(fundsKey(accountId), fundsView, 0);
        return fundsView;
    }

    @Override
    public Optional<TradingFundsView> findFunds(String accountId) {
        return readJson(fundsKey(accountId), TradingFundsView.class);
    }

    @Override
    public TradingPosition savePosition(TradingPosition position) {
        writeJson(positionKey(position.accountId(), position.instrumentToken(), position.product()), position, 0);
        return position;
    }

    @Override
    public List<TradingPosition> listPositions(String accountId) {
        Set<String> keys = redis.keys(positionKeyPattern(accountId));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<TradingPosition> out = new ArrayList<>();
        for (String key : keys) {
            readJsonRaw(key, TradingPosition.class).ifPresent(out::add);
        }
        return out.stream().sorted(Comparator.comparingLong(TradingPosition::updatedAtEpochMs).reversed()).toList();
    }

    @Override
    public Optional<TradingPosition> findPosition(String accountId, long instrumentToken, TradingProduct product) {
        return readJson(positionKey(accountId, instrumentToken, product), TradingPosition.class);
    }

    @Override
    public void saveLastMargin(String accountId, MarginCheckResult marginCheckResult) {
        writeJson(marginLastKey(accountId), marginCheckResult, 0);
    }

    @Override
    public Optional<MarginCheckResult> getLastMargin(String accountId) {
        return readJson(marginLastKey(accountId), MarginCheckResult.class);
    }

    @Override
    public StrategyExecutionSettings saveStrategySettings(StrategyExecutionSettings settings) {
        writeJson(strategySettingsKey(settings.accountId(), settings.scriptId()), settings, 0);
        return settings;
    }

    @Override
    public Optional<StrategyExecutionSettings> getStrategySettings(String accountId, String scriptId) {
        return readJson(strategySettingsKey(accountId, scriptId), StrategyExecutionSettings.class);
    }

    @Override
    public List<StrategyExecutionSettings> listStrategySettings(String accountId) {
        Set<String> keys = redis.keys(strategySettingsPattern(accountId));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<StrategyExecutionSettings> out = new ArrayList<>();
        for (String key : keys) {
            readJsonRaw(key, StrategyExecutionSettings.class).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public FillApplyResult applyFillAtomically(FillApplyCommand fillApplyCommand) {
        long now = System.currentTimeMillis();
        String response = redis.execute(
                applyFillScript,
                List.of(
                        fillDedupeKey(fillApplyCommand.account().accountId()),
                        orderKey(fillApplyCommand.account().accountId(), fillApplyCommand.order().orderId()),
                        fillKey(fillApplyCommand.account().accountId(), fillApplyCommand.fill().fillId()),
                        fillByOrderKey(fillApplyCommand.account().accountId(), fillApplyCommand.order().orderId()),
                        positionKey(
                                fillApplyCommand.account().accountId(),
                                fillApplyCommand.order().instrumentToken(),
                                fillApplyCommand.order().product()),
                        fundsKey(fillApplyCommand.account().accountId()),
                        openOrdersKey(fillApplyCommand.account().accountId()),
                        orderByInstrumentKey(
                                fillApplyCommand.account().accountId(),
                                fillApplyCommand.order().instrumentToken())),
                fillApplyCommand.fill().fillId(),
                String.valueOf(fillApplyCommand.dedupeTtlSeconds()),
                toJson(fillApplyCommand.fill()),
                String.valueOf(now),
                String.valueOf(tradingProperties.getRetention().getOrderTtlSeconds()),
                String.valueOf(tradingProperties.getRetention().getFillTtlSeconds()),
                String.valueOf(fillApplyCommand.markPrice()),
                String.valueOf(fillApplyCommand.account().openingBalance()),
                String.valueOf(tradingProperties.getMargin().getMisPercent()));

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("fill_apply_script_failed empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.path("duplicate").asBoolean(false)) {
                return new FillApplyResult(true, fillApplyCommand.order(), null, null, fillApplyCommand.fill());
            }
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("fill_apply_script_failed: " + root.path("error").asText());
            }

            TradingOrder order = objectMapper.treeToValue(root.path("order"), TradingOrder.class);
            TradingPosition position = objectMapper.treeToValue(root.path("position"), TradingPosition.class);
            TradingFundsView funds = objectMapper.treeToValue(root.path("funds"), TradingFundsView.class);
            return new FillApplyResult(false, order, position, funds, fillApplyCommand.fill());
        } catch (Exception ex) {
            throw new IllegalStateException("fill_apply_script_parse_failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean registerIntentExecution(String accountId, String dedupeKey, int ttlSeconds) {
        Long added = redis.opsForSet().add(intentDedupeKey(accountId), dedupeKey);
        redis.expire(intentDedupeKey(accountId), java.time.Duration.ofSeconds(Math.max(60, ttlSeconds)));
        return added != null && added > 0;
    }

    @Override
    public long countOpenOrders(String accountId) {
        Set<String> ids = redis.opsForSet().members(openOrdersKey(accountId));
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        long count = 0L;
        for (String id : ids) {
            Boolean exists = redis.hasKey(orderKey(accountId, id));
            if (Boolean.TRUE.equals(exists)) {
                count++;
            } else {
                redis.opsForSet().remove(openOrdersKey(accountId), id);
            }
        }
        return count;
    }

    @Override
    public long incrementOrderRateCounter(String accountId, Instant at) {
        String bucket = RATE_BUCKET_FMT.format(at);
        String key = rateKey(accountId, bucket);
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, java.time.Duration.ofSeconds(120));
        return count == null ? 0L : count;
    }

    private <T> Optional<T> readJson(String key, Class<T> type) {
        return readJsonRaw(key, type);
    }

    private <T> Optional<T> readJsonRaw(String key, Class<T> type) {
        String raw = redis.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception ex) {
            throw new IllegalStateException("redis_json_read_failed key=" + key + " reason=" + ex.getMessage(), ex);
        }
    }

    private void writeJson(String key, Object value, int ttlSeconds) {
        String raw = toJson(value);
        redis.opsForValue().set(key, raw);
        if (ttlSeconds > 0) {
            redis.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json_write_failed reason=" + ex.getMessage(), ex);
        }
    }

    private static String accountKey(String accountId) {
        return "pta:acct:data:" + accountId;
    }

    private static String accountByUserKey(String userId) {
        return "pta:acct:byUser:" + userId;
    }

    private static String accountModeSet(TradingMode mode) {
        return "pta:acct:mode:" + mode.name();
    }

    private static String orderKey(String accountId, String orderId) {
        return "pta:ord:" + accountId + ":" + orderId;
    }

    private static String orderKeyPattern(String accountId) {
        return "pta:ord:" + accountId + ":*";
    }

    private static String openOrdersKey(String accountId) {
        return "pta:ord:open:" + accountId;
    }

    private static String orderByInstrumentKey(String accountId, long instrumentToken) {
        return "pta:ord:byInst:" + accountId + ":" + instrumentToken;
    }

    private static String orderMapKey(String accountId) {
        return "pta:ordmap:" + accountId;
    }

    private static String fillKey(String accountId, String fillId) {
        return "pta:fill:" + accountId + ":" + fillId;
    }

    private static String fillByOrderKey(String accountId, String orderId) {
        return "pta:fill:byOrd:" + accountId + ":" + orderId;
    }

    private static String positionKey(String accountId, long instrumentToken, TradingProduct product) {
        return "pta:pos:" + accountId + ":" + instrumentToken + ":" + product.name();
    }

    private static String positionKeyPattern(String accountId) {
        return "pta:pos:" + accountId + ":*";
    }

    private static String fundsKey(String accountId) {
        return "pta:funds:" + accountId;
    }

    private static String marginLastKey(String accountId) {
        return "pta:margin:last:" + accountId;
    }

    private static String strategySettingsKey(String accountId, String scriptId) {
        return "pta:strategy:" + accountId + ":" + scriptId;
    }

    private static String strategySettingsPattern(String accountId) {
        return "pta:strategy:" + accountId + ":*";
    }

    private static String fillDedupeKey(String accountId) {
        return "pta:dedupe:fill:" + accountId;
    }

    private static String intentDedupeKey(String accountId) {
        return "pta:dedupe:intent:" + accountId;
    }

    private static String rateKey(String accountId, String bucket) {
        return "pta:rate:" + accountId + ":" + bucket;
    }
}
