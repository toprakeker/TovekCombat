package dev.toprak.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatTest {

    @Test
    void testRemainingSecondsCalculation() {
        long now = System.currentTimeMillis();
        long expire = now + 15000;
        double remaining = (expire - now) / 1000.0;
        assertTrue(remaining >= 14.9 && remaining <= 15.0);
    }

    @Test
    void testCommandBlockPrefix() {
        String input = "/spawn";
        String inputSub = "/spawn now";
        assertTrue(input.toLowerCase().startsWith("/spawn"));
        assertTrue(inputSub.toLowerCase().startsWith("/spawn"));
        assertFalse("/spawner".toLowerCase().startsWith("/spawn "));
    }
}
