package edu.columbia.tjw.item.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GKQuantilesTest
{

    @org.junit.jupiter.api.Test
    void testQuantiles()
    {
        GKQuantiles quantiles = new GKQuantiles(0.001);

        quantiles.offer(0.0);
        quantiles.offer(1.0);

        assertEquals(0.0, quantiles.getQuantile(0.1));
        assertEquals(1.0, quantiles.getQuantile(0.99));

        for (int i = 0; i < 100; i++)
        {
            quantiles.offer((0.1 * i) - 1.0);
        }

        assertEquals(-1.0, quantiles.getQuantile(0.001));
        assertEquals(-0.09999999999999998, quantiles.getQuantile(0.1));
        assertEquals(7.800000000000001, quantiles.getQuantile(0.9));

    }
}
