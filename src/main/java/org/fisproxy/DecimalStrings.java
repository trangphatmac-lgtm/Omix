package org.fisproxy;

import java.math.BigDecimal;

/** Keep service_point / nfa_coin as a decimal string. Never round via float math. */
final class DecimalStrings {
    private DecimalStrings() {
    }

    static String decimalStr(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (value instanceof Boolean) {
            throw new IllegalArgumentException("amount cannot be bool");
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        if (value instanceof String text) {
            String stripped = text.strip();
            if (stripped.isEmpty()) {
                throw new IllegalArgumentException("empty amount");
            }
            try {
                new BigDecimal(stripped);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid amount: " + value, exception);
            }
            return stripped;
        }
        if (value instanceof Double || value instanceof Float) {
            try {
                return new BigDecimal(value.toString()).toPlainString();
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid amount: " + value, exception);
            }
        }
        throw new IllegalArgumentException("unsupported amount type: " + value.getClass().getSimpleName());
    }
}

