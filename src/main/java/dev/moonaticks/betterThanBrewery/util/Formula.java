package dev.moonaticks.betterThanBrewery.util;

import java.util.Locale;
import java.util.Map;

/** Small, sandboxed arithmetic expression evaluator for recipe formulas. */
public final class Formula {
    private final String source;
    private final Map<String, Double> variables;
    private int pos;

    private Formula(String source, Map<String, Double> variables) {
        this.source = source == null ? "0" : source;
        this.variables = variables;
    }

    public static double evaluate(String expression, Map<String, Double> variables, double fallback) {
        try {
            Formula parser = new Formula(expression, variables == null ? Map.of() : variables);
            double value = parser.expression();
            if (Double.isFinite(value) && parser.end()) return value;
        } catch (RuntimeException ignored) { }
        return fallback;
    }

    private boolean end() {
        skip();
        return pos >= source.length();
    }

    private double expression() {
        double value = term();
        while (true) {
            skip();
            if (take('+')) value += term();
            else if (take('-')) value -= term();
            else return value;
        }
    }

    private double term() {
        double value = power();
        while (true) {
            skip();
            if (take('*')) value *= power();
            else if (take('/')) {
                double divisor = power();
                if (Math.abs(divisor) < 1.0E-12) throw new IllegalArgumentException("division by zero");
                value /= divisor;
            } else if (take('%')) {
                double divisor = power();
                if (Math.abs(divisor) < 1.0E-12) throw new IllegalArgumentException("modulo by zero");
                value %= divisor;
            } else return value;
        }
    }

    private double power() {
        double value = unary();
        skip();
        if (take('^')) value = Math.pow(value, power());
        return value;
    }

    private double unary() {
        skip();
        if (take('+')) return unary();
        if (take('-')) return -unary();
        return primary();
    }

    private double primary() {
        skip();
        if (take('(')) {
            double value = expression();
            expect(')');
            return value;
        }
        if (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
            int start = pos++;
            while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || ".eE+-".indexOf(source.charAt(pos)) >= 0)) {
                char c = source.charAt(pos);
                if ((c == '+' || c == '-') && pos > start && source.charAt(pos - 1) != 'e' && source.charAt(pos - 1) != 'E') break;
                pos++;
            }
            return Double.parseDouble(source.substring(start, pos));
        }
        int start = pos;
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_' || source.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalArgumentException("expected value");
        String name = source.substring(start, pos).toLowerCase(Locale.ROOT);
        skip();
        if (!take('(')) return variables.getOrDefault(name, 0.0);
        java.util.ArrayList<Double> args = new java.util.ArrayList<>();
        skip();
        if (!take(')')) {
            do args.add(expression()); while (take(','));
            expect(')');
        }
        return function(name, args);
    }

    private double function(String name, java.util.List<Double> a) {
        return switch (name) {
            case "abs" -> Math.abs(arg(a, 0));
            case "floor" -> Math.floor(arg(a, 0));
            case "ceil" -> Math.ceil(arg(a, 0));
            case "round" -> Math.round(arg(a, 0));
            case "sqrt" -> Math.sqrt(Math.max(0, arg(a, 0)));
            case "min" -> a.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "max" -> a.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case "clamp" -> Math.max(arg(a, 1), Math.min(arg(a, 2), arg(a, 0)));
            case "if" -> arg(a, 0) > 0 ? arg(a, 1) : arg(a, 2);
            default -> 0;
        };
    }

    private double arg(java.util.List<Double> args, int index) {
        return index < args.size() ? args.get(index) : 0;
    }

    private boolean take(char expected) {
        skip();
        if (pos < source.length() && source.charAt(pos) == expected) { pos++; return true; }
        return false;
    }

    private void expect(char expected) {
        if (!take(expected)) throw new IllegalArgumentException("expected " + expected);
    }

    private void skip() { while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++; }
}
