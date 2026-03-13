package com.readywealth.trading.trade_engine.engine.domain.module.swingpivots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import org.ta4j.core.num.Num;

import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.DecimalNum;

public class SwingPivotsModule implements com.readywealth.trading.trade_engine.engine.domain.module.Module {

    private static final Logger log = LoggerFactory.getLogger(SwingPivotsModule.class);

    private final int len;

    private final Deque<SwingPoint> all = new ArrayDeque<>();
    private final EnumMap<SwingLabel, Deque<SwingPoint>> byLabel = new EnumMap<>(SwingLabel.class);
    private final EnumMap<SwingSide, Deque<SwingPoint>> bySide = new EnumMap<>(SwingSide.class);

    private Num lastHighPivot = null;
    private Num lastLowPivot = null;
    private final List<Object> pendingEvents = new ArrayList<>();

    public SwingPivotsModule(int len) {
        this.len = len;
        for (SwingLabel l : SwingLabel.values())
            byLabel.put(l, new ArrayDeque<>());
        for (SwingSide s : SwingSide.values())
            bySide.put(s, new ArrayDeque<>());
    }

    // @Override
    // public List<?> drainEvents() {
    // if (pendingEvents.isEmpty())
    // return List.of();
    // List<Object> out = List.copyOf(pendingEvents);
    // pendingEvents.clear();
    // return out;
    // }

    public List<SwingPoint> query(SwingQuery q) {
        Deque<SwingPoint> base;
        if (q.label() != null)
            base = byLabel.get(q.label());
        else if (q.side() != null)
            base = bySide.get(q.side());
        else
            base = all;

        List<SwingPoint> out = new ArrayList<>();
        int lim = q.limit() == null ? Integer.MAX_VALUE : q.limit();

        for (var it = base.descendingIterator(); it.hasNext() && out.size() < lim;) {
            SwingPoint sp = it.next();
            if (q.side() != null && sp.side() != q.side())
                continue;
            if (q.label() != null && sp.label() != q.label())
                continue;
            if (q.fromBar() != null && sp.pivotIndex() < q.fromBar())
                continue;
            if (q.toBar() != null && sp.pivotIndex() > q.toBar())
                continue;
            out.add(sp);
        }
        return out;
    }

    public Optional<SwingPoint> lastHH() {
        var d = byLabel.get(SwingLabel.HH);
        return d.isEmpty() ? Optional.empty() : Optional.of(d.peekLast());
    }

    public Optional<SwingPoint> lastLL() {
        var d = byLabel.get(SwingLabel.LL);
        return d.isEmpty() ? Optional.empty() : Optional.of(d.peekLast());
    }

    @Override
    public void onBar(BarSeriesContext ctx, int index) {
        BarSeries s = ctx.series();
        if (s == null || s.isEmpty())
            return;

        int begin = s.getBeginIndex();
        int end = s.getEndIndex();
        if (index < begin || index > end)
            return;

        int p = index - len;
        if (p < begin)
            return;

        int left = p - len;
        int right = p + len;
        if (left < begin)
            return;
        if (right > end)
            return;

        Num pHigh = s.getBar(p).getHighPrice();
        Num pLow = s.getBar(p).getLowPrice();

        boolean isPivotHigh = true;
        boolean isPivotLow = true;

        for (int i = left; i <= right; i++) {
            if (i == p)
                continue;

            Num hi = s.getBar(i).getHighPrice();
            Num lo = s.getBar(i).getLowPrice();

            if (hi.isGreaterThan(pHigh))
                isPivotHigh = false;
            if (lo.isLessThan(pLow))
                isPivotLow = false;

            if (!isPivotHigh && !isPivotLow)
                break;
        }

        if (isPivotHigh) {
            SwingLabel label = (lastHighPivot == null || pHigh.isGreaterThan(lastHighPivot))
                    ? SwingLabel.HH
                    : SwingLabel.LH;
            lastHighPivot = pHigh;

            SwingPoint sp = new SwingPoint(
                    makeSwingId(len, SwingSide.HIGH, p, pHigh),
                    len,
                    p,
                    SwingSide.HIGH,
                    label,
                    pHigh.toString());
            insert(sp);
            pendingEvents.add(sp);

        }

        if (isPivotLow) {
            SwingLabel label = (lastLowPivot == null || pLow.isLessThan(lastLowPivot))
                    ? SwingLabel.LL
                    : SwingLabel.HL;
            lastLowPivot = pLow;

            SwingPoint sp = new SwingPoint(
                    makeSwingId(len, SwingSide.LOW, p, pLow),
                    len,
                    p,
                    SwingSide.LOW,
                    label,
                    pLow.toString());
            insert(sp);
            pendingEvents.add(sp);

        }
    }

    @Override
    public void onEvict(BarSeriesContext ctx, int newBeginIndex) {
        List<SwingPoint> removed = pruneAndCollect(all, newBeginIndex);
        for (var d : byLabel.values())
            prune(d, newBeginIndex);
        for (var d : bySide.values())
            prune(d, newBeginIndex);
        if (!removed.isEmpty()) {
            pendingEvents.add(new SwingPointInvalidation(
                    removed.stream().map(SwingPoint::id).toList()));
        }
    }

    @Override
    public void onInvalidateFrom(BarSeriesContext ctx, int index) {
        pendingEvents.clear();
        List<String> invalidated = new ArrayList<>();
        invalidated.addAll(removeFromDeque(all, index));
        for (var d : byLabel.values()) {
            removeFromDeque(d, index);
        }
        for (var d : bySide.values()) {
            removeFromDeque(d, index);
        }
        resetLastPivots();
        if (!invalidated.isEmpty()) {
            pendingEvents.add(new SwingPointInvalidation(invalidated));
        }
    }

    private void insert(SwingPoint sp) {
        all.addLast(sp);
        byLabel.get(sp.label()).addLast(sp);
        bySide.get(sp.side()).addLast(sp);
    }

    private static void prune(Deque<SwingPoint> d, int newBeginIndex) {
        while (!d.isEmpty() && d.peekFirst().pivotIndex() < newBeginIndex) {
            d.removeFirst();
        }
    }

    private static List<SwingPoint> pruneAndCollect(Deque<SwingPoint> d, int newBeginIndex) {
        List<SwingPoint> removed = new ArrayList<>();
        while (!d.isEmpty() && d.peekFirst().pivotIndex() < newBeginIndex) {
            removed.add(d.removeFirst());
        }
        return removed;
    }

    private static List<String> removeFromDeque(Deque<SwingPoint> d, int fromIndex) {
        List<String> removed = new ArrayList<>();
        d.removeIf(sp -> {
            if (sp.pivotIndex() >= fromIndex) {
                removed.add(sp.id());
                return true;
            }
            return false;
        });
        return removed;
    }

    private void resetLastPivots() {
        SwingPoint lastHigh = bySide.get(SwingSide.HIGH).peekLast();
        SwingPoint lastLow = bySide.get(SwingSide.LOW).peekLast();
        lastHighPivot = lastHigh == null ? null : DecimalNum.valueOf(lastHigh.price());
        lastLowPivot = lastLow == null ? null : DecimalNum.valueOf(lastLow.price());
    }

    private static String makeSwingId(int len, SwingSide side, int pivotIndex, Num price) {
        String priceStr = price == null ? "na" : price.toString();
        return "ms.swing:" + len + ":" + side.name().toLowerCase() + ":" + pivotIndex + ":" + priceStr;
    }

    private String moduleName() {
        return "ms.swing:" + len;
    }
}
