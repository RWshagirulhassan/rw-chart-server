package com.readywealth.trading.trade_engine.engine.domain.module.boschoch;

import com.readywealth.trading.trade_engine.engine.domain.module.swingpivots.SwingPivotsModule;
import com.readywealth.trading.trade_engine.engine.domain.module.swingpivots.SwingPoint;
import com.readywealth.trading.trade_engine.engine.domain.module.swingpivots.SwingQuery;
import com.readywealth.trading.trade_engine.engine.domain.module.swingpivots.SwingSide;
import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public class BosChochModule implements com.readywealth.trading.trade_engine.engine.domain.module.Module {

    private static final Logger log = LoggerFactory.getLogger(BosChochModule.class);

    private final int len;
    private final SwingPivotsModule swings;

    private final Deque<BreakEvent> all = new ArrayDeque<>();
    private final EnumMap<BreakClass, Deque<BreakEvent>> byClass = new EnumMap<>(BreakClass.class);
    private final EnumMap<BreakDir, Deque<BreakEvent>> byDir = new EnumMap<>(BreakDir.class);

    private BreakDir lastBreakDir = null;
    private String lastUpBrokenSwingId = null;
    private String lastDownBrokenSwingId = null;

    public BosChochModule(int len, SwingPivotsModule swings) {
        this.len = len;
        this.swings = swings;
        for (BreakClass c : BreakClass.values())
            byClass.put(c, new ArrayDeque<>());
        for (BreakDir d : BreakDir.values())
            byDir.put(d, new ArrayDeque<>());
    }

    public List<BreakEvent> query(BreakQuery q) {
        Deque<BreakEvent> base;
        if (q.cls() != null)
            base = byClass.get(q.cls());
        else if (q.dir() != null)
            base = byDir.get(q.dir());
        else
            base = all;

        List<BreakEvent> out = new ArrayList<>();
        int lim = q.limit() == null ? Integer.MAX_VALUE : q.limit();

        for (var it = base.descendingIterator(); it.hasNext() && out.size() < lim;) {
            BreakEvent ev = it.next();
            if (q.cls() != null && ev.cls() != q.cls())
                continue;
            if (q.dir() != null && ev.dir() != q.dir())
                continue;
            if (q.fromBar() != null && ev.barIndex() < q.fromBar())
                continue;
            if (q.toBar() != null && ev.barIndex() > q.toBar())
                continue;
            out.add(ev);
        }
        return out;
    }

    private void insert(BreakEvent ev) {
        all.addLast(ev);
        byClass.get(ev.cls()).addLast(ev);
        byDir.get(ev.dir()).addLast(ev);
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
        if (index == begin)
            return;

        SwingPoint lastHigh = firstOrNull(swings.query(SwingQuery.all().side(SwingSide.HIGH).limit(1)));
        SwingPoint lastLow = firstOrNull(swings.query(SwingQuery.all().side(SwingSide.LOW).limit(1)));

        if (lastHigh == null && lastLow == null)
            return;

        Num close = s.getBar(index).getClosePrice();
        Num prevClose = s.getBar(index - 1).getClosePrice();
        var nf = s.numFactory();

        if (lastHigh != null) {
            Num level = nf.numOf(lastHigh.price());
            boolean crossedUp = prevClose.isLessThanOrEqual(level) && close.isGreaterThan(level);

            if (crossedUp && !Objects.equals(lastUpBrokenSwingId, lastHigh.id())) {
                BreakDir dir = BreakDir.UP;
                BreakClass cls = (lastBreakDir != null && lastBreakDir != dir) ? BreakClass.CHOCH : BreakClass.BOS;

                BreakEvent ev = new BreakEvent(
                        index,
                        len,
                        cls,
                        dir,
                        lastHigh.id(),
                        lastHigh.pivotIndex(),
                        lastHigh.price());
                insert(ev);

                lastBreakDir = dir;
                lastUpBrokenSwingId = lastHigh.id();

                return;
            }
        }

        if (lastLow != null) {
            Num level = nf.numOf(lastLow.price());
            boolean crossedDown = prevClose.isGreaterThanOrEqual(level) && close.isLessThan(level);

            if (crossedDown && !Objects.equals(lastDownBrokenSwingId, lastLow.id())) {
                BreakDir dir = BreakDir.DOWN;
                BreakClass cls = (lastBreakDir != null && lastBreakDir != dir) ? BreakClass.CHOCH : BreakClass.BOS;

                BreakEvent ev = new BreakEvent(
                        index,
                        len,
                        cls,
                        dir,
                        lastLow.id(),
                        lastLow.pivotIndex(),
                        lastLow.price());
                insert(ev);

                lastBreakDir = dir;
                lastDownBrokenSwingId = lastLow.id();

            }
        }
    }

    @Override
    public void onEvict(BarSeriesContext ctx, int newBeginIndex) {
        pruneBreak(all, newBeginIndex);
        for (var d : byClass.values())
            pruneBreak(d, newBeginIndex);
        for (var d : byDir.values())
            pruneBreak(d, newBeginIndex);
    }

    private static void pruneBreak(Deque<BreakEvent> d, int newBeginIndex) {
        while (!d.isEmpty() && d.peekFirst().barIndex() < newBeginIndex) {
            d.removeFirst();
        }
    }

    private static <T> T firstOrNull(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    private String moduleName() {
        return "ms.break:" + len;
    }
}
