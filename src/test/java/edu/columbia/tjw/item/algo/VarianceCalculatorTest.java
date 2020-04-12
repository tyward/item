package edu.columbia.tjw.item.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VarianceCalculatorTest
{

    @org.junit.jupiter.api.Test
    void testVariance()
    {
        VarianceCalculator calc = new VarianceCalculator();

        calc.update(1.0);
        calc.update(1.0);

        assertEquals(1.0, calc.getMean());
        assertEquals(0.0, calc.getVariance());

        calc.update(4.0);
        assertEquals(2.0, calc.getMean());
        assertEquals(3.0, calc.getVariance());

        calc.update(1.8);
        calc.update(0.3);
        calc.update(9.9);
        calc.update(0.01);

        assertEquals(2.572857142857143, calc.getMean());
        assertEquals(12.167157142857144, calc.getVariance());
    }
}
