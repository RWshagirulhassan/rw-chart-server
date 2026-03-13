package com.readywealth.trading.trade_engine.engine.domain.script.instance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInstanceId;

public final class ScriptAttachmentManager {

    private final Map<ScriptInstanceId, ScriptAttachment> attachments = new LinkedHashMap<>();

    public ScriptAttachment attach(ScriptAttachment attachment) {
        attachments.put(attachment.instanceId(), attachment);
        return attachment;
    }

    public Optional<ScriptAttachment> detach(ScriptInstanceId instanceId) {
        return Optional.ofNullable(attachments.remove(instanceId));
    }

    public Optional<ScriptAttachment> get(ScriptInstanceId instanceId) {
        return Optional.ofNullable(attachments.get(instanceId));
    }

    public ScriptAttachment replace(ScriptAttachment attachment) {
        attachments.put(attachment.instanceId(), attachment);
        return attachment;
    }

    public List<ScriptAttachment> orderedAttachments() {
        List<ScriptAttachment> out = new ArrayList<>(attachments.values());
        out.sort(Comparator
                .comparingLong(ScriptAttachment::attachSequence)
                .thenComparing(a -> a.instanceId().value()));
        return out;
    }

    public int size() {
        return attachments.size();
    }

    public void clear() {
        attachments.clear();
    }
}
