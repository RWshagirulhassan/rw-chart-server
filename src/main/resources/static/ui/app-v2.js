(() => {
  const $ = (id) => document.getElementById(id);
  const state = {
    ws: null,
    auth: null,
    currentSessionId: null,
    maxCandles: 200,
    maxScriptDeltas: 300,
    scriptPendingById: new Set()
  };

  function scriptLog() {}
  function sessionLog() {}

  const el = {
    authState: $("auth-state"),
    authJson: $("auth-json"),
    sessionMsg: $("session-msg"),
    sessionJson: $("session-json"),
    wsState: $("ws-state"),
    errors: $("errors"),
    candles: $("candles"),
    sessionSelect: $("session-select"),
    sessionId: $("session-id"),
    seriesKey: $("series-key"),
    destroyOnClose: $("destroy-on-close"),
    maxBars: $("max-bars"),
    tickToken: $("tick-token"),
    tickPrice: $("tick-price"),
    tickMode: $("tick-mode"),
    tickTime: $("tick-time"),
    scriptId: $("script-id"),
    scriptEvery: $("script-every"),
    scriptAlertAbove: $("script-alert-above"),
    scriptExecutionMode: $("script-execution-mode"),
    scriptInstanceId: $("script-instance-id"),
    scriptJson: $("script-json"),
    scriptDeltas: $("script-deltas")
  };

  function bind(id, fn) {
    const node = $(id);
    if (!node) return;
    node.addEventListener("click", fn);
  }

  function addError(msg) {
    if (!el.errors) return;
    const li = document.createElement("li");
    li.textContent = `[${new Date().toLocaleTimeString()}] ${msg}`;
    el.errors.prepend(li);
    while (el.errors.children.length > 20) el.errors.removeChild(el.errors.lastChild);
  }

  function setWsStatus(text) {
    if (el.wsState) el.wsState.textContent = text;
  }

  async function req(path, method = "GET", body) {
    const res = await fetch(path, {
      method,
      headers: { "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined
    });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error(data?.error || data?.message || `${method} ${path} failed (${res.status})`);
    return data;
  }

  function parseSeriesKeys() {
    const raw = (el.seriesKey?.value || "").trim();
    if (!raw) throw new Error("Series key is required");
    const keys = raw.split(",").map(v => v.trim()).filter(Boolean);
    if (!keys.length) throw new Error("Series key is required");
    return [...new Set(keys)];
  }

  function parseSingleSeriesKey(strict = true) {
    const raw = (el.seriesKey?.value || "").trim();
    if (!raw) {
      if (strict) throw new Error("Series key is required");
      return null;
    }
    const keys = raw.split(",").map(v => v.trim()).filter(Boolean);
    if (!keys.length) {
      if (strict) throw new Error("Series key is required");
      return null;
    }
    return keys[0];
  }

  function requireSessionId() {
    const id = (el.sessionId?.value || "").trim();
    if (!id) throw new Error("Session ID is required");
    return id;
  }

  function requireScriptInstanceId() {
    const id = (el.scriptInstanceId?.value || "").trim();
    if (!id) throw new Error("Script instance ID is required");
    return id;
  }

  function requireAuth() {
    if (!state.auth) throw new Error("Click Refresh Auth first");
    if (!state.auth.linked || state.auth.expired || !state.auth.userId) {
      throw new Error("Not authenticated. Login from /kite/login and refresh auth.");
    }
  }

  function scriptParamsFromForm() {
    const everyRaw = Number(el.scriptEvery?.value ?? 1);
    const every = Number.isFinite(everyRaw) ? Math.max(1, Math.trunc(everyRaw)) : 1;
    const alertAboveText = (el.scriptAlertAbove?.value || "").trim();
    const params = { every };
    if (alertAboveText) {
      const parsed = Number(alertAboveText);
      if (!Number.isFinite(parsed)) {
        throw new Error("alertAbove must be numeric");
      }
      params.alertAbove = parsed;
    } else {
      params.alertAbove = Number.MAX_VALUE;
    }
    return params;
  }

  function scriptIdFromForm() {
    const scriptId = (el.scriptId?.value || "").trim();
    if (!scriptId) throw new Error("Script ID is required");
    return scriptId;
  }

  function scriptExecutionModeFromForm() {
    const mode = (el.scriptExecutionMode?.value || "ON_CANDLE_CLOSE").trim();
    return mode === "ON_TICK" ? "ON_TICK" : "ON_CANDLE_CLOSE";
  }

  function scriptSummary(data) {
    if (!data) return "-";
    const payload = data.payload;
    const point = payload?.payload;
    if (data.eventType === "PLOT_POINT_ADD" && point) {
      const idx = point.index ?? "?";
      const close = point.close ?? "?";
      return `plot=${payload.plotId || "close"} idx=${idx} close=${close}`;
    }
    if (data.eventType === "ALERT_ADD") {
      return `alert=${JSON.stringify(payload?.payload ?? payload)}`;
    }
    if (data.eventType === "INTENT_ADD") {
      return `intent=${JSON.stringify(payload)}`;
    }
    if (data.snapshotCursorSeq != null) {
      return `snapshot ready cursor=${data.snapshotCursorSeq}`;
    }
    return JSON.stringify(payload ?? data);
  }

  function renderScriptDelta(data) {
    if (!data || !el.scriptDeltas) return;
    if (data.sessionId && state.currentSessionId && data.sessionId !== state.currentSessionId) return;

    const selectedSeries = parseSingleSeriesKey(false);
    if (data.seriesKey && selectedSeries && data.seriesKey !== selectedSeries) return;

    const sid = data.scriptInstanceId || "";
    const lifecycleEvent = data.eventType === "SCRIPT_LOADING"
      || data.eventType === "SCRIPT_SNAPSHOT_READY"
      || data.eventType === "SCRIPT_SNAPSHOT_ACKED";
    if (sid && state.scriptPendingById.has(sid) && !lifecycleEvent) {
      return;
    }

    const tr = document.createElement("tr");
    tr.innerHTML = [
      new Date().toLocaleTimeString(),
      data.eventType || data.type || "script_event",
      data.seq ?? "",
      data.seriesKey || "",
      data.scriptInstanceId || "",
      data.registryType || "",
      scriptSummary(data)
    ].map(v => `<td>${v}</td>`).join("");

    el.scriptDeltas.prepend(tr);
    while (el.scriptDeltas.children.length > state.maxScriptDeltas) {
      el.scriptDeltas.removeChild(el.scriptDeltas.lastChild);
    }
    if (el.scriptJson) {
      el.scriptJson.textContent = JSON.stringify(data, null, 2);
    }
  }

  async function refreshAuth() {
    state.auth = await req("/api/session");
    if (el.authJson) el.authJson.textContent = JSON.stringify(state.auth, null, 2);
    if (el.authState) {
      el.authState.textContent = `linked=${!!state.auth.linked}, expired=${!!state.auth.expired}, userId=${state.auth.userId || ""}`;
    }
  }

  async function listSessions() {
    requireAuth();
    const sessions = await req("/engine/ui-sessions");
    if (el.sessionJson) el.sessionJson.textContent = JSON.stringify(sessions, null, 2);
    if (!el.sessionSelect) return sessions;

    el.sessionSelect.innerHTML = "";
    const first = document.createElement("option");
    first.value = "";
    first.textContent = "-- select --";
    el.sessionSelect.appendChild(first);

    sessions.forEach(s => {
      const op = document.createElement("option");
      op.value = s.sessionId;
      op.textContent = `${s.sessionId} | ${s.state} | keys=${(s.seriesKeys || []).length}`;
      el.sessionSelect.appendChild(op);
    });

    return sessions;
  }

  function attachSelectSync() {
    if (!el.sessionSelect || !el.sessionId) return;
    el.sessionSelect.addEventListener("change", () => {
      if (!el.sessionSelect.value) return;
      el.sessionId.value = el.sessionSelect.value;
      state.currentSessionId = el.sessionSelect.value;
    });
  }

  async function createSession() {
    requireAuth();
    const payload = {
      seriesKeys: parseSeriesKeys(),
      maxBarCount: Number(el.maxBars?.value || 5000),
      destroyOnClose: !!el.destroyOnClose?.checked
    };
    sessionLog("create_request", payload);
    const data = await req("/engine/ui-sessions", "POST", payload);
    sessionLog("create_response", data);
    state.currentSessionId = data.sessionId;
    if (el.sessionId) el.sessionId.value = data.sessionId;
    if (el.sessionMsg) el.sessionMsg.textContent = `Session created: ${data.sessionId}`;
    if (el.sessionJson) el.sessionJson.textContent = JSON.stringify(data, null, 2);
    await listSessions();
  }

  async function closeSession() {
    requireAuth();
    const sessionId = requireSessionId();
    const data = await req(`/engine/ui-sessions/${sessionId}`, "DELETE");
    if (el.sessionMsg) el.sessionMsg.textContent = `Session closed: ${sessionId}`;
    if (el.sessionJson) el.sessionJson.textContent = JSON.stringify(data, null, 2);
    if (state.currentSessionId === sessionId) {
      disconnectWs();
      state.currentSessionId = null;
    }
    await listSessions();
  }

  async function snapshotSession() {
    requireAuth();
    const sessionId = requireSessionId();
    const seriesKeys = parseSeriesKeys();
    sessionLog("snapshot_request", { sessionId, seriesKeys });
    const snapshots = [];
    for (const seriesKey of seriesKeys) {
      const encodedSeriesKey = encodeURIComponent(seriesKey);
      const data = await req(`/engine/ui-sessions/${encodeURIComponent(sessionId)}/series/${encodedSeriesKey}/snapshot`);
      sessionLog("snapshot_response", {
        sessionId,
        seriesKey,
        snapshotCursor: data?.snapshotCursor,
        lastSeq: data?.lastSeq,
        beginIndex: data?.beginIndex,
        endIndex: data?.endIndex,
        bars: Array.isArray(data?.bars) ? data.bars.length : 0
      });
      snapshots.push({ seriesKey, snapshot: data });
    }
    if (el.sessionMsg) {
      el.sessionMsg.textContent = `Snapshot loaded for ${snapshots.length} series key(s).`;
    }
    if (el.sessionJson) {
      el.sessionJson.textContent = JSON.stringify(snapshots, null, 2);
    }
  }

  async function attachScript() {
    requireAuth();
    const sessionId = requireSessionId();
    const seriesKey = parseSingleSeriesKey();
    const payload = {
      scriptId: scriptIdFromForm(),
      params: scriptParamsFromForm(),
      executionMode: scriptExecutionModeFromForm()
    };
    scriptLog("attach_request", { sessionId, seriesKey, payload });
    const data = await req(
      `/engine/ui-sessions/${encodeURIComponent(sessionId)}/series/${encodeURIComponent(seriesKey)}/scripts/attach`,
      "POST",
      payload
    );
    scriptLog("attach_ack", data);
    if (el.scriptInstanceId) {
      el.scriptInstanceId.value = data.scriptInstanceId || "";
    }
    if (data.scriptInstanceId) {
      state.scriptPendingById.add(data.scriptInstanceId);
      renderScriptDelta({
        sessionId,
        seriesKey,
        scriptInstanceId: data.scriptInstanceId,
        eventType: "SCRIPT_LOADING",
        seq: "",
        registryType: "",
        payload: data
      });
    }
    if (el.scriptJson) {
      el.scriptJson.textContent = JSON.stringify(data, null, 2);
    }
  }

  async function listScripts() {
    requireAuth();
    const sessionId = requireSessionId();
    const seriesKey = parseSingleSeriesKey();
    scriptLog("list_scripts_request", { sessionId, seriesKey });
    const data = await req(
      `/engine/ui-sessions/${encodeURIComponent(sessionId)}/series/${encodeURIComponent(seriesKey)}/scripts`
    );
    scriptLog("list_scripts_response", data);
    state.scriptPendingById.clear();
    if (Array.isArray(data)) {
      data.forEach((item) => {
        if (item?.scriptInstanceId && item?.lifecycleState === "LOADING") {
          state.scriptPendingById.add(item.scriptInstanceId);
        }
      });
    }
    if (Array.isArray(data) && data.length > 0 && el.scriptInstanceId && !el.scriptInstanceId.value) {
      el.scriptInstanceId.value = data[0].scriptInstanceId;
    }
    if (el.scriptJson) {
      el.scriptJson.textContent = JSON.stringify(data, null, 2);
    }
  }

  async function detachScript() {
    requireAuth();
    const sessionId = requireSessionId();
    const seriesKey = parseSingleSeriesKey();
    const scriptInstanceId = requireScriptInstanceId();
    scriptLog("detach_request", { sessionId, seriesKey, scriptInstanceId });
    const data = await req(
      `/engine/ui-sessions/${encodeURIComponent(sessionId)}/series/${encodeURIComponent(seriesKey)}/scripts/${encodeURIComponent(scriptInstanceId)}/detach`,
      "POST"
    );
    scriptLog("detach_response", data);
    state.scriptPendingById.delete(scriptInstanceId);
    if (el.scriptJson) {
      el.scriptJson.textContent = JSON.stringify(data, null, 2);
    }
  }

  async function handleScriptSnapshotReady(evt) {
    if (!evt?.scriptInstanceId) return;
    scriptLog("snapshot_ready_event", evt);
    const sessionId = evt.sessionId || requireSessionId();
    const seriesKey = evt.seriesKey || parseSingleSeriesKey();
    const scriptInstanceId = evt.scriptInstanceId;
    state.scriptPendingById.add(scriptInstanceId);

    renderScriptDelta({
      ...evt,
      eventType: "SCRIPT_SNAPSHOT_READY"
    });

    if (evt.status === "FAILED") {
      scriptLog("snapshot_ready_failed", {
        scriptInstanceId,
        error: evt.error || "script_bootstrap_failed"
      });
      addError(`Script bootstrap failed for ${scriptInstanceId}: ${evt.error || "script_bootstrap_failed"}`);
      if (el.scriptJson) {
        el.scriptJson.textContent = JSON.stringify({ ready: evt }, null, 2);
      }
      return;
    }

    const base = `/engine/ui-sessions/${encodeURIComponent(sessionId)}/series/${encodeURIComponent(seriesKey)}/scripts/${encodeURIComponent(scriptInstanceId)}`;
    scriptLog("snapshot_fetch_start", { sessionId, seriesKey, scriptInstanceId, base });
    const plotSnapshot = await req(`${base}/registries/plot/snapshot`);
    scriptLog("plot_snapshot_loaded", {
      scriptInstanceId,
      plotKeys: Object.keys(plotSnapshot || {})
    });
    let drawingSnapshot = {};
    try {
      drawingSnapshot = await req(`${base}/registries/drawing/snapshot`);
      scriptLog("drawing_snapshot_loaded", {
        scriptInstanceId,
        drawingKeys: Object.keys(drawingSnapshot || {})
      });
    } catch (_) {
      drawingSnapshot = {};
      scriptLog("drawing_snapshot_skipped_or_empty", { scriptInstanceId });
    }
    scriptLog("snapshot_ack_request", {
      scriptInstanceId,
      snapshotCursorSeq: evt.snapshotCursorSeq
    });
    const ack = await req(`${base}/snapshot-ack`, "POST", {
      snapshotCursorSeq: evt.snapshotCursorSeq
    });
    scriptLog("snapshot_ack_response", ack);
    if (ack?.activated) {
      state.scriptPendingById.delete(scriptInstanceId);
    } else {
      scriptLog("snapshot_ack_not_activated", {
        scriptInstanceId,
        ack
      });
      addError(`Script snapshot ack did not activate ${scriptInstanceId}`);
    }
    renderScriptDelta({
      ...evt,
      eventType: "SCRIPT_SNAPSHOT_ACKED",
      payload: ack
    });
    if (el.scriptJson) {
      el.scriptJson.textContent = JSON.stringify({
        ready: evt,
        plotSnapshot,
        drawingSnapshot,
        ack
      }, null, 2);
    }
  }

  function connectWs() {
    const sessionId = requireSessionId();
    disconnectWs();

    const proto = window.location.protocol === "https:" ? "wss" : "ws";
    const url = `${proto}://${window.location.host}/ticks/ws?sessionId=${encodeURIComponent(sessionId)}`;
    sessionLog("ws_connect_request", { sessionId, url });
    state.ws = new WebSocket(url);
    state.currentSessionId = sessionId;
    setWsStatus("Connecting...");

    state.ws.onopen = () => {
      sessionLog("ws_open", { sessionId });
      setWsStatus(`Connected (${sessionId})`);
    };
    state.ws.onclose = () => {
      sessionLog("ws_close", { sessionId });
      setWsStatus("Disconnected");
      state.ws = null;
    };
    state.ws.onerror = () => {
      sessionLog("ws_error", { sessionId });
      addError("WebSocket error");
    };

    state.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === "meta" && msg.data?.sessionId) {
          sessionLog("ws_meta", msg.data);
          state.currentSessionId = msg.data.sessionId;
          if (el.sessionId) el.sessionId.value = msg.data.sessionId;
        }
        if (msg.type === "tick") renderTick(msg.data);
        if (msg.type === "candle_appended") renderCandle(msg.data);
        if (msg.type === "candle_live_upsert") renderCandle(msg.data);
        if (msg.type === "script_registry_delta") renderScriptDelta(msg.data);
        if (msg.type === "intent_emitted") renderScriptDelta(msg.data);
        if (msg.type === "script_snapshot_ready") {
          scriptLog("ws_script_snapshot_ready", msg.data);
          if (msg.data?.sessionId && state.currentSessionId && msg.data.sessionId !== state.currentSessionId) {
            return;
          }
          const selectedSeries = parseSingleSeriesKey(false);
          if (msg.data?.seriesKey && selectedSeries && msg.data.seriesKey !== selectedSeries) {
            return;
          }
          if (msg.data?.scriptInstanceId && el.scriptInstanceId) {
            el.scriptInstanceId.value = msg.data.scriptInstanceId;
          }
          handleScriptSnapshotReady(msg.data).catch((e) => addError(`script snapshot flow failed: ${e.message}`));
        }
        if (msg.type === "error") addError(msg.data?.message || "stream error");
      } catch (e) {
        addError(`WS parse error: ${e.message}`);
      }
    };
  }

  function disconnectWs() {
    if (state.ws) {
      try { state.ws.close(); } catch (_) {}
      state.ws = null;
    }
    setWsStatus("Disconnected");
  }

  function renderTick(data) {
    if (!data) return;
    if (data.sessionId && state.currentSessionId && data.sessionId !== state.currentSessionId) return;

    const q = data.tickQuote || data.indexQuote;
    if (!q) return;
    if (el.tickToken) el.tickToken.textContent = String(q.instrumentToken ?? "-");
    if (el.tickPrice) el.tickPrice.textContent = String(q.lastTradedPrice ?? "-");
    if (el.tickMode) el.tickMode.textContent = String(q.mode ?? "-");
    if (el.tickTime) el.tickTime.textContent = new Date().toLocaleTimeString();
  }

  function renderCandle(data) {
    if (!data?.bar || !el.candles) return;
    if (data.sessionId && state.currentSessionId && data.sessionId !== state.currentSessionId) return;

    const b = data.bar;
    const endIndex = data?.meta?.endIndex;
    const expectedIndex = typeof endIndex === "number"
      ? (b.mutation === "REPLACE" ? endIndex : endIndex - 1)
      : null;
    const aligned = typeof b.barIndex === "number" && typeof expectedIndex === "number"
      ? b.barIndex === expectedIndex
      : null;
    const tr = document.createElement("tr");
    tr.innerHTML = [
      b.beginTime || "",
      b.endTime || "",
      data.seriesKey || "",
      data.seq || "",
      b.open || "",
      b.high || "",
      b.low || "",
      b.close || "",
      b.volume || ""
    ].map(v => `<td>${v}</td>`).join("");

    el.candles.prepend(tr);
    while (el.candles.children.length > state.maxCandles) {
      el.candles.removeChild(el.candles.lastChild);
    }
  }

  bind("btn-auth", async () => {
    try { await refreshAuth(); } catch (e) { addError(e.message); }
  });
  bind("btn-list", async () => {
    try { await listSessions(); } catch (e) { addError(e.message); }
  });
  bind("btn-create", async () => {
    try { await createSession(); } catch (e) { addError(e.message); }
  });
  bind("btn-close", async () => {
    try { await closeSession(); } catch (e) { addError(e.message); }
  });
  bind("btn-snapshot", async () => {
    try { await snapshotSession(); } catch (e) { addError(e.message); }
  });
  bind("btn-script-attach", async () => {
    try { await attachScript(); } catch (e) { addError(e.message); }
  });
  bind("btn-script-list", async () => {
    try { await listScripts(); } catch (e) { addError(e.message); }
  });
  bind("btn-script-detach", async () => {
    try { await detachScript(); } catch (e) { addError(e.message); }
  });
  bind("btn-ws-connect", () => {
    try { connectWs(); } catch (e) { addError(e.message); }
  });
  bind("btn-ws-disconnect", () => disconnectWs());

  attachSelectSync();
  refreshAuth().catch(() => {});
})();
