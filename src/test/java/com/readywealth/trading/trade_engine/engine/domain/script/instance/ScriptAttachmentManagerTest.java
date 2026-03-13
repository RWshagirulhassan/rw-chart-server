package com.readywealth.trading.trade_engine.engine.domain.script.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInstanceId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptKind;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParams;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptRuntime;

class ScriptAttachmentManagerTest {

    @Test
    void allowsSameScriptIdWithDifferentParams() {
        ScriptAttachmentManager manager = new ScriptAttachmentManager();

        manager.attach(new ScriptAttachment(
                new ScriptInstanceId("a"),
                new ScriptId("same.script"),
                ScriptKind.INDICATOR,
                new ScriptParams(Map.of("len", 20)),
                Map.of("len", 20),
                new NoopRuntime(),
                1));

        manager.attach(new ScriptAttachment(
                new ScriptInstanceId("b"),
                new ScriptId("same.script"),
                ScriptKind.INDICATOR,
                new ScriptParams(Map.of("len", 50)),
                Map.of("len", 50),
                new NoopRuntime(),
                2));

        assertEquals(2, manager.size());
        assertTrue(manager.detach(new ScriptInstanceId("a")).isPresent());
        assertEquals(1, manager.size());
    }

    private static final class NoopRuntime implements ScriptRuntime {
        @Override
        public void onBar(com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext ctx, int index) {
        }
    }
}
