package edu.columbia.tjw.item.base;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveFactory;

import static org.junit.jupiter.api.Assertions.*;

class StandardCurveFactoryTest
{

    @org.junit.jupiter.api.Test
    void testGaussian()
    {
        ItemCurveFactory<SimpleRegressor, StandardCurveType> factory = new StandardCurveFactory<>();

        ItemCurve<StandardCurveType> gaussian = factory.generateCurve(StandardCurveType.GAUSSIAN,
                0, new double[]{0.5, 3.0});

        assertEquals(gaussian.transform(0.5), 1.0);
        assertEquals(gaussian.transform(2.2), 0.8516705072309603);

        ItemCurve<StandardCurveType> g2 = factory.generateCurve(StandardCurveType.GAUSSIAN,
                0, new double[]{0.5001, 3.0});

        final double d1 = gaussian.derivative(0, 2.2);
        final double approxD1 = (g2.transform(2.2) - gaussian.transform(2.2)) / 0.0001;
        final double errD1 = Math.abs(d1-approxD1) / d1;
        assertEquals(errD1, 0.0001);



    }
}