package com.readywealth.trading.trade_engine.engine.domain.script.examples.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrimitiveDrawingPayloads {

    private PrimitiveDrawingPayloads() {
    }

    public static Map<String, Object> point(int index, double price) {
        return Map.of(
                "index", index,
                "price", price);
    }

    public static Map<String, Object> stroke(String color, double width) {
        return Map.of(
                "color", color,
                "width", width);
    }

    public static Map<String, Object> stroke(String color, double width, List<Integer> dash) {
        return Map.of(
                "color", color,
                "width", width,
                "dash", List.copyOf(dash));
    }

    public static Map<String, Object> fill(String color) {
        return Map.of("color", color);
    }

    public static Map<String, Object> label(String text) {
        return Map.of("text", text);
    }

    public static Map<String, Object> label(String text, Map<String, Object> extras) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("text", text);
        if (extras != null && !extras.isEmpty()) {
            out.putAll(extras);
        }
        return Map.copyOf(out);
    }

    public static Map<String, Object> line(
            Map<String, Object> p1,
            Map<String, Object> p2,
            Map<String, Object> stroke,
            Map<String, Object> label) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "line");
        out.put("p1", p1);
        out.put("p2", p2);
        out.put("stroke", stroke);
        if (label != null) {
            out.put("label", label);
        }
        return Map.copyOf(out);
    }

    public static Map<String, Object> rect(
            Map<String, Object> p1,
            Map<String, Object> p2,
            Map<String, Object> fill,
            Map<String, Object> stroke,
            Map<String, Object> label) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "rect");
        out.put("p1", p1);
        out.put("p2", p2);
        out.put("fill", fill);
        out.put("stroke", stroke);
        if (label != null) {
            out.put("label", label);
        }
        return Map.copyOf(out);
    }

    public static Map<String, Object> text(
            Map<String, Object> point,
            Map<String, Object> label) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "text");
        out.put("p", point);
        out.put("label", label);
        return Map.copyOf(out);
    }

    public static Map<String, Object> marker(
            Map<String, Object> point,
            String shape,
            double size,
            String text) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "marker");
        out.put("point", point);
        out.put("shape", shape);
        out.put("size", size);
        if (text != null && !text.isBlank()) {
            out.put("text", text);
        }
        return Map.copyOf(out);
    }
}
