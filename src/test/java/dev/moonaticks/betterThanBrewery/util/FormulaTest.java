package dev.moonaticks.betterThanBrewery.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormulaTest {
    @Test
    void evaluatesArithmeticWithVariables() {
        double result = Formula.evaluate("alcohol + age * 0.75", Map.of("alcohol", 8.0, "age", 4.0), -1);

        assertEquals(11.0, result, 0.000001);
    }

    @Test
    void supportsFunctionsAndPrecedence() {
        double result = Formula.evaluate("clamp(max(quality * potency, 10) + floor(age / 2), 0, 100)",
                Map.of("quality", 80.0, "potency", 0.75, "age", 5.0), -1);

        assertEquals(62.0, result, 0.000001);
    }

    @Test
    void supportsPowerAndUnaryOperators() {
        double result = Formula.evaluate("-(2 ^ 3) + 10 / 2", Map.of(), -1);

        assertEquals(-3.0, result, 0.000001);
    }

    @Test
    void invalidOrUnsafeExpressionsUseFallback() {
        assertEquals(42.0, Formula.evaluate("1 / (2 - 2)", Map.of(), 42.0), 0.000001);
        assertEquals(42.0, Formula.evaluate("this is not a formula", Map.of(), 42.0), 0.000001);
    }
}
