package com.readywealth.trading.trade_engine.execution.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroup;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroupStatus;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerFireResult;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisTriggerStore implements TriggerStorePort {

    private static final String LUA_FIRE_TRIGGER_OCO = """
            local triggerRaw = redis.call('GET', KEYS[1])
            if not triggerRaw then
              return cjson.encode({fired=false,reason='trigger_not_found'})
            end

            local trigger = cjson.decode(triggerRaw)
            if trigger.status ~= 'ACTIVE' then
              return cjson.encode({fired=false,reason='not_active'})
            end

            trigger.status = 'TRIGGERED'
            trigger.lastObservedLtp = tonumber(ARGV[1])
            trigger.firedAtEpochMs = tonumber(ARGV[2])
            trigger.updatedAtEpochMs = tonumber(ARGV[2])
            redis.call('SET', KEYS[1], cjson.encode(trigger))
            redis.call('SREM', KEYS[2], trigger.triggerId)
            redis.call('SREM', KEYS[3], trigger.triggerId)

            local siblingCancelled = false
            local sibling = cjson.null
            local oco = cjson.null

            if trigger.ocoGroupId and trigger.ocoGroupId ~= cjson.null then
              local ocoKey = ARGV[4] .. trigger.ocoGroupId
              local ocoRaw = redis.call('GET', ocoKey)
              if ocoRaw then
                oco = cjson.decode(ocoRaw)
                local siblingId
                if oco.triggerAId == trigger.triggerId then
                  siblingId = oco.triggerBId
                else
                  siblingId = oco.triggerAId
                end

                if siblingId then
                  local siblingKey = ARGV[5] .. siblingId
                  local siblingRaw = redis.call('GET', siblingKey)
                  if siblingRaw then
                    sibling = cjson.decode(siblingRaw)
                    if sibling.status == 'ACTIVE' then
                      sibling.status = 'CANCELLED'
                      sibling.updatedAtEpochMs = tonumber(ARGV[2])
                      redis.call('SET', siblingKey, cjson.encode(sibling))
                      redis.call('SREM', KEYS[2], sibling.triggerId)
                      local siblingInstSet = ARGV[6] .. sibling.instrumentToken
                      redis.call('SREM', siblingInstSet, sibling.triggerId)
                      siblingCancelled = true
                    end
                  end
                end

                oco.status = 'ONE_TRIGGERED'
                oco.triggeredTriggerId = trigger.triggerId
                oco.updatedAtEpochMs = tonumber(ARGV[2])
                redis.call('SET', ocoKey, cjson.encode(oco))
              end
            end

            return cjson.encode({fired=true,trigger=trigger,siblingCancelled=siblingCancelled,sibling=sibling,oco=oco})
            """;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> fireTriggerScript;

    public RedisTriggerStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.fireTriggerScript = new DefaultRedisScript<>(LUA_FIRE_TRIGGER_OCO, String.class);
    }

    @Override
    public ExitPlan saveExitPlan(ExitPlan exitPlan) {
        writeJson(exitPlanKey(exitPlan.accountId(), exitPlan.exitPlanId()), exitPlan, 0);
        if (exitPlan.entryOrderId() != null && !exitPlan.entryOrderId().isBlank()) {
            redis.opsForSet().add(exitPlanByEntryKey(exitPlan.accountId(), exitPlan.entryOrderId()), exitPlan.exitPlanId());
        }
        return exitPlan;
    }

    @Override
    public Optional<ExitPlan> findExitPlan(String accountId, String exitPlanId) {
        return readJson(exitPlanKey(accountId, exitPlanId), ExitPlan.class);
    }

    @Override
    public List<ExitPlan> listExitPlans(String accountId, ExitPlanStatus status) {
        Set<String> keys = redis.keys(exitPlanPattern(accountId));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<ExitPlan> plans = new ArrayList<>();
        for (String key : keys) {
            readJsonRaw(key, ExitPlan.class).ifPresent(plans::add);
        }
        if (status != null) {
            plans = plans.stream().filter(item -> item.status() == status).toList();
        }
        return plans.stream().sorted(Comparator.comparingLong(ExitPlan::createdAtEpochMs).reversed()).toList();
    }

    @Override
    public List<ExitPlan> findPendingExitPlansByEntryOrder(String accountId, String entryOrderId) {
        Set<String> ids = redis.opsForSet().members(exitPlanByEntryKey(accountId, entryOrderId));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ExitPlan> out = new ArrayList<>();
        for (String id : ids) {
            findExitPlan(accountId, id)
                    .filter(item -> item.status() == ExitPlanStatus.PENDING)
                    .ifPresent(out::add);
        }
        return out;
    }

    @Override
    public Trigger saveTrigger(Trigger trigger) {
        writeJson(triggerKey(trigger.accountId(), trigger.triggerId()), trigger, 0);
        if (trigger.status() == TriggerStatus.ACTIVE) {
            redis.opsForSet().add(triggerActiveSet(trigger.accountId()), trigger.triggerId());
            redis.opsForSet().add(triggerByInstrumentSet(trigger.accountId(), trigger.instrumentToken()), trigger.triggerId());
        } else {
            redis.opsForSet().remove(triggerActiveSet(trigger.accountId()), trigger.triggerId());
            redis.opsForSet().remove(triggerByInstrumentSet(trigger.accountId(), trigger.instrumentToken()), trigger.triggerId());
        }
        return trigger;
    }

    @Override
    public List<Trigger> saveTriggers(List<Trigger> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return List.of();
        }
        List<Trigger> out = new ArrayList<>(triggers.size());
        for (Trigger trigger : triggers) {
            out.add(saveTrigger(trigger));
        }
        return out;
    }

    @Override
    public Optional<Trigger> findTrigger(String accountId, String triggerId) {
        return readJson(triggerKey(accountId, triggerId), Trigger.class);
    }

    @Override
    public List<Trigger> listTriggers(String accountId, TriggerStatus status) {
        Set<String> keys = redis.keys(triggerPattern(accountId));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<Trigger> out = new ArrayList<>();
        for (String key : keys) {
            readJsonRaw(key, Trigger.class).ifPresent(out::add);
        }
        if (status != null) {
            out = out.stream().filter(item -> item.status() == status).toList();
        }
        return out.stream().sorted(Comparator.comparingLong(Trigger::createdAtEpochMs).reversed()).toList();
    }

    @Override
    public List<Trigger> listActiveTriggers(String accountId) {
        Set<String> ids = redis.opsForSet().members(triggerActiveSet(accountId));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Trigger> out = new ArrayList<>();
        for (String id : ids) {
            findTrigger(accountId, id).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public List<Trigger> listActiveTriggersByInstrument(String accountId, long instrumentToken) {
        Set<String> ids = redis.opsForSet().members(triggerByInstrumentSet(accountId, instrumentToken));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Trigger> out = new ArrayList<>();
        for (String id : ids) {
            findTrigger(accountId, id)
                    .filter(item -> item.status() == TriggerStatus.ACTIVE)
                    .ifPresent(out::add);
        }
        return out;
    }

    @Override
    public void updateTriggerLastObservedLtp(String accountId, String triggerId, double ltp, long updatedAtEpochMs) {
        findTrigger(accountId, triggerId).ifPresent(trigger -> saveTrigger(trigger.withLastObservedLtp(ltp, updatedAtEpochMs)));
    }

    @Override
    public TriggerFireResult fireTriggerAtomically(String accountId, String triggerId, double ltp, long firedAtEpochMs) {
        Trigger existing = findTrigger(accountId, triggerId).orElse(null);
        if (existing == null) {
            return TriggerFireResult.noop();
        }
        String response = redis.execute(
                fireTriggerScript,
                List.of(
                        triggerKey(accountId, triggerId),
                        triggerActiveSet(accountId),
                        triggerByInstrumentSet(accountId, existing.instrumentToken())),
                String.valueOf(ltp),
                String.valueOf(firedAtEpochMs),
                accountId,
                ocoPrefix(accountId),
                triggerPrefix(accountId),
                triggerByInstrumentPrefix(accountId));

        if (response == null || response.isBlank()) {
            return TriggerFireResult.noop();
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.path("fired").asBoolean(false)) {
                return TriggerFireResult.noop();
            }
            Trigger trigger = objectMapper.treeToValue(root.path("trigger"), Trigger.class);
            Trigger sibling = root.hasNonNull("sibling") && root.path("sibling").isObject()
                    ? objectMapper.treeToValue(root.path("sibling"), Trigger.class)
                    : null;
            OcoGroup oco = root.hasNonNull("oco") && root.path("oco").isObject()
                    ? objectMapper.treeToValue(root.path("oco"), OcoGroup.class)
                    : null;
            return new TriggerFireResult(true, trigger, sibling, oco);
        } catch (Exception ex) {
            throw new IllegalStateException("trigger_fire_parse_failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Trigger markTriggerOrderAttempt(
            String accountId,
            String triggerId,
            String orderId,
            TriggerStatus status,
            String rejectionReason,
            long updatedAtEpochMs) {
        Trigger trigger = findTrigger(accountId, triggerId)
                .orElseThrow(() -> new IllegalArgumentException("trigger not found: " + triggerId));
        Trigger updated = trigger.withLinkedOrder(orderId, status, rejectionReason, updatedAtEpochMs);
        return saveTrigger(updated);
    }

    @Override
    public Trigger markTriggerCompleted(String accountId, String triggerId, long updatedAtEpochMs) {
        Trigger trigger = findTrigger(accountId, triggerId)
                .orElseThrow(() -> new IllegalArgumentException("trigger not found: " + triggerId));
        Trigger updated = trigger.withStatus(TriggerStatus.COMPLETED, trigger.firedAtEpochMs(), trigger.rejectionReason(),
                updatedAtEpochMs);
        updated = saveTrigger(updated);

        if (updated.ocoGroupId() != null && !updated.ocoGroupId().isBlank()) {
            findOcoGroup(accountId, updated.ocoGroupId()).ifPresent(oco -> {
                if (oco.status() == OcoGroupStatus.ONE_TRIGGERED || oco.status() == OcoGroupStatus.ACTIVE) {
                    saveOcoGroup(oco.withStatus(OcoGroupStatus.COMPLETED, oco.triggeredTriggerId(), updatedAtEpochMs));
                }
            });
        }
        return updated;
    }

    @Override
    public OcoGroup saveOcoGroup(OcoGroup ocoGroup) {
        writeJson(ocoKey(ocoGroup.accountId(), ocoGroup.ocoGroupId()), ocoGroup, 0);
        return ocoGroup;
    }

    @Override
    public Optional<OcoGroup> findOcoGroup(String accountId, String ocoGroupId) {
        return readJson(ocoKey(accountId, ocoGroupId), OcoGroup.class);
    }

    @Override
    public void cancelExitPlan(String accountId, String exitPlanId, long updatedAtEpochMs, String reason) {
        ExitPlan plan = findExitPlan(accountId, exitPlanId)
                .orElseThrow(() -> new IllegalArgumentException("exit plan not found: " + exitPlanId));
        saveExitPlan(plan.withStatus(ExitPlanStatus.CANCELLED, null, reason, updatedAtEpochMs));

        for (Trigger trigger : listTriggers(accountId, null)) {
            if (!exitPlanId.equals(trigger.exitPlanId())) {
                continue;
            }
            if (trigger.status() == TriggerStatus.ACTIVE || trigger.status() == TriggerStatus.TRIGGERED
                    || trigger.status() == TriggerStatus.ORDER_PLACED) {
                saveTrigger(trigger.withStatus(TriggerStatus.CANCELLED, trigger.firedAtEpochMs(), reason, updatedAtEpochMs));
            }
        }

        if (plan.ocoGroupId() != null && !plan.ocoGroupId().isBlank()) {
            findOcoGroup(accountId, plan.ocoGroupId())
                    .ifPresent(oco -> saveOcoGroup(oco.withStatus(OcoGroupStatus.CANCELLED, oco.triggeredTriggerId(),
                            updatedAtEpochMs)));
        }
    }

    @Override
    public void markExitPlanRearmRequired(String accountId, String exitPlanId, int remainingQty, String reason,
            long updatedAtEpochMs) {
        ExitPlan plan = findExitPlan(accountId, exitPlanId)
                .orElseThrow(() -> new IllegalArgumentException("exit plan not found: " + exitPlanId));
        saveExitPlan(plan.withStatus(ExitPlanStatus.REARM_REQUIRED, remainingQty, reason, updatedAtEpochMs));
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
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json_write_failed reason=" + ex.getMessage(), ex);
        }
    }

    private static String exitPlanKey(String accountId, String exitPlanId) {
        return "pta:exit:plan:" + accountId + ":" + exitPlanId;
    }

    private static String exitPlanByEntryKey(String accountId, String entryOrderId) {
        return "pta:exit:plan:byEntry:" + accountId + ":" + entryOrderId;
    }

    private static String exitPlanPattern(String accountId) {
        return "pta:exit:plan:" + accountId + ":*";
    }

    private static String triggerKey(String accountId, String triggerId) {
        return triggerPrefix(accountId) + triggerId;
    }

    private static String triggerPrefix(String accountId) {
        return "pta:trg:" + accountId + ":";
    }

    private static String triggerPattern(String accountId) {
        return triggerPrefix(accountId) + "*";
    }

    private static String triggerActiveSet(String accountId) {
        return "pta:trg:active:" + accountId;
    }

    private static String triggerByInstrumentSet(String accountId, long instrumentToken) {
        return triggerByInstrumentPrefix(accountId) + instrumentToken;
    }

    private static String triggerByInstrumentPrefix(String accountId) {
        return "pta:trg:byInst:" + accountId + ":";
    }

    private static String ocoKey(String accountId, String ocoGroupId) {
        return ocoPrefix(accountId) + ocoGroupId;
    }

    private static String ocoPrefix(String accountId) {
        return "pta:oco:" + accountId + ":";
    }
}
