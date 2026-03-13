package com.readywealth.trading.trade_engine.session.api.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInstanceId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptKind;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParams;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptRuntime;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.AttachScriptResult;
import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptAttachmentState;
import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptBootstrapStatus;
import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptExecutionMode;
import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptSnapshotReadyEvent;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiReplaceScriptRequest;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiReplaceScriptResponse;
import com.readywealth.trading.trade_engine.session.application.SessionIdentityService;
import com.readywealth.trading.trade_engine.session.application.SessionSeriesCoordinator;
import com.readywealth.trading.trade_engine.session.domain.UiSession;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;

class UiSessionControllerReplaceScriptTest {

    @SuppressWarnings("unchecked")
    @Test
    void replaceReturnsAcceptedResponseForOwnedScript() {
        SessionSeriesCoordinator coordinator = mock(SessionSeriesCoordinator.class);
        SessionIdentityService identityService = mock(SessionIdentityService.class);
        SeriesRuntimeManager runtimeManager = mock(SeriesRuntimeManager.class);
        TicksWebSocketBroadcaster broadcaster = mock(TicksWebSocketBroadcaster.class);
        UiSessionController controller = new UiSessionController(
                coordinator,
                identityService,
                runtimeManager,
                broadcaster);

        String userId = "u1";
        String sessionId = "session-1";
        String oldScriptInstanceId = "script-old-1";
        String newScriptInstanceId = "script-new-1";
        SeriesKey seriesKey = new SeriesKey(256265L, IntervalKind.TIME_1M);
        String encodedSeriesKey = SeriesKeyCodec.encode(seriesKey);

        UiSession session = new UiSession(new UiSessionId(sessionId), userId, true);
        session.attach(seriesKey);

        ScriptAttachment oldAttachment = scriptAttachment(
                oldScriptInstanceId,
                "sample.indicator.close-plot",
                ScriptExecutionMode.ON_TICK,
                sessionId,
                userId);
        ScriptAttachment newAttachment = scriptAttachment(
                newScriptInstanceId,
                "sample.indicator.close-plot",
                ScriptExecutionMode.ON_TICK,
                sessionId,
                userId);

        when(identityService.requireCurrentUserId()).thenReturn(userId);
        when(coordinator.getSession(userId, new UiSessionId(sessionId))).thenReturn(session);
        when(runtimeManager.listScripts(seriesKey)).thenReturn(List.of(oldAttachment));
        when(runtimeManager.replaceScript(
                eq(seriesKey),
                eq(sessionId),
                eq(userId),
                eq(oldScriptInstanceId),
                eq(Map.of("every", 2)),
                eq(ScriptExecutionMode.ON_TICK),
                any(Consumer.class)))
                .thenAnswer(invocation -> {
                    Consumer<ScriptSnapshotReadyEvent> readyListener = invocation.getArgument(6);
                    readyListener.accept(new ScriptSnapshotReadyEvent(
                            sessionId,
                            encodedSeriesKey,
                            newScriptInstanceId,
                            "bootstrap-job-1",
                            100L,
                            1L,
                            2L,
                            ScriptBootstrapStatus.READY,
                            null));
                    return new AttachScriptResult(newAttachment, "bootstrap-job-1", 1234L);
                });

        ResponseEntity<UiReplaceScriptResponse> response = controller.replaceScript(
                sessionId,
                encodedSeriesKey,
                oldScriptInstanceId,
                new UiReplaceScriptRequest(Map.of("every", 2), "ON_TICK"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        UiReplaceScriptResponse body = response.getBody();
        assertTrue(body != null);
        assertEquals(oldScriptInstanceId, body.replacedScriptInstanceId());
        assertEquals(newScriptInstanceId, body.scriptInstanceId());
        assertEquals("sample.indicator.close-plot", body.scriptId());
        assertEquals("INDICATOR", body.kind());
        assertEquals("ON_TICK", body.executionMode());
        assertEquals(sessionId, body.sessionId());
        assertEquals(encodedSeriesKey, body.seriesKey());
        assertEquals("ACTIVE", body.lifecycleState());
        assertTrue(body.snapshotRequired());
        assertEquals(1234L, body.attachAcceptedAtEpochMs());
        assertEquals("bootstrap-job-1", body.bootstrapJobId());

        verify(runtimeManager, times(1)).replaceScript(
                eq(seriesKey),
                eq(sessionId),
                eq(userId),
                eq(oldScriptInstanceId),
                eq(Map.of("every", 2)),
                eq(ScriptExecutionMode.ON_TICK),
                any(Consumer.class));
        verify(broadcaster, times(1)).broadcastScriptSnapshotReadyToSession(any(UiSessionId.class), any());
    }

    @Test
    void replaceRejectsMissingParams() {
        UiSessionController controller = new UiSessionController(
                mock(SessionSeriesCoordinator.class),
                mock(SessionIdentityService.class),
                mock(SeriesRuntimeManager.class),
                mock(TicksWebSocketBroadcaster.class));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.replaceScript(
                "session-1",
                "NSE:TEST:TIME_1M",
                "script-1",
                new UiReplaceScriptRequest(null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void replaceRejectsScriptNotOwnedBySession() {
        SessionSeriesCoordinator coordinator = mock(SessionSeriesCoordinator.class);
        SessionIdentityService identityService = mock(SessionIdentityService.class);
        SeriesRuntimeManager runtimeManager = mock(SeriesRuntimeManager.class);
        UiSessionController controller = new UiSessionController(
                coordinator,
                identityService,
                runtimeManager,
                mock(TicksWebSocketBroadcaster.class));

        String userId = "u1";
        String sessionId = "session-1";
        SeriesKey seriesKey = new SeriesKey(256265L, IntervalKind.TIME_1M);
        String encodedSeriesKey = SeriesKeyCodec.encode(seriesKey);
        UiSession session = new UiSession(new UiSessionId(sessionId), userId, true);
        session.attach(seriesKey);

        when(identityService.requireCurrentUserId()).thenReturn(userId);
        when(coordinator.getSession(userId, new UiSessionId(sessionId))).thenReturn(session);
        when(runtimeManager.listScripts(seriesKey)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.replaceScript(
                sessionId,
                encodedSeriesKey,
                "missing-script-instance",
                new UiReplaceScriptRequest(Map.of("every", 2), null)));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(runtimeManager, Mockito.never()).replaceScript(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    private static ScriptAttachment scriptAttachment(
            String scriptInstanceId,
            String scriptId,
            ScriptExecutionMode mode,
            String ownerSessionId,
            String ownerUserId) {
        return new ScriptAttachment(
                new ScriptInstanceId(scriptInstanceId),
                new ScriptId(scriptId),
                ScriptKind.INDICATOR,
                new ScriptParams(Map.of("every", 1)),
                Map.of("every", 1),
                new ScriptRuntime() {
                    @Override
                    public void onBar(com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext ctx, int index) {
                    }
                },
                1L,
                mode,
                ScriptAttachmentState.ACTIVE,
                ownerSessionId,
                ownerUserId);
    }
}
