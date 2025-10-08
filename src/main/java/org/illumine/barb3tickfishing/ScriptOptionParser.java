package org.illumine.barb3tickfishing;

final class ScriptOptionParser {
    private ScriptOptionParser() {
    }

    static String asString(Object value, String fallback) {
        if (value instanceof String) {
            String s = ((String) value).trim();
            return s.isEmpty() ? fallback : s;
        }
        if (value != null) {
            String s = value.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return fallback;
    }

    static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) {
                return fallback;
            }
            return Boolean.parseBoolean(s);
        }
        if (value != null) {
            String s = value.toString().trim();
            if (!s.isEmpty()) {
                return Boolean.parseBoolean(s);
            }
        }
        return fallback;
    }

    static int asInt(Object value, int fallback, int minValue) {
        int parsed = fallback;
        if (value instanceof Number) {
            parsed = ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                parsed = Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        } else if (value != null) {
            try {
                parsed = Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (parsed < minValue) {
            parsed = minValue;
        }
        return parsed;
    }
}
