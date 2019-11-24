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

        assertEquals(1.0, gaussian.transform(0.5));
        assertEquals(0.8516705072309603, gaussian.transform(2.2));

        ItemCurve<StandardCurveType> g2 = factory.generateCurve(StandardCurveType.GAUSSIAN,
                0, new double[]{0.5001, 3.0});

        final double d1 = gaussian.derivative(0, 2.2);
        final double approxD1 = (g2.transform(2.2) - gaussian.transform(2.2)) / 0.0001;
        final double errD1 = Math.abs(d1-approxD1) / Math.abs(d1);
        //assertEquals(0.0001, errD1);

        ItemCurve<StandardCurveType> g3 = factory.generateCurve(StandardCurveType.GAUSSIAN,
                0, new double[]{0.5, 3.0001});

        final double d2 = gaussian.derivative(0, 2.2);
        final double approxD2 = (g3.transform(2.2) - gaussian.transform(2.2)) / 0.0001;
        final double errD2 = Math.abs(d2-approxD2) / Math.abs(d2);
        assertEquals(0.0001, errD2);
    }

    @org.junit.jupiter.api.Test
    void testLogistic()
    {
        ItemCurveFactory<SimpleRegressor, StandardCurveType> factory = new StandardCurveFactory<>();

        ItemCurve<StandardCurveType> logistic = factory.generateCurve(StandardCurveType.LOGISTIC,
                0, new double[]{0.5, 3.0});

        assertEquals(0.5, logistic.transform(0.5));
        assertEquals(0.8581489350995121, logistic.transform(0.7));

        ItemCurve<StandardCurveType> g2 = factory.generateCurve(StandardCurveType.LOGISTIC,
                0, new double[]{0.5001, 3.0});

        final double d1 = logistic.derivative(0, 0.7);
        final double approxD1 = (g2.transform(0.7) - logistic.transform(0.7)) / 0.0001;
        final double errD1 = Math.abs(d1-approxD1) / Math.abs(d1);
        assertTrue( 0.001 > errD1);

        ItemCurve<StandardCurveType> g3 = factory.generateCurve(StandardCurveType.LOGISTIC,
                0, new double[]{0.5, 3.0001});

        final double d2 = logistic.derivative(0, 0.7);
        final double approxD2 = (g3.transform(0.7) - logistic.transform(0.7)) / 0.0001;
        final double errD2 = Math.abs(d2-approxD2) / Math.abs(d2);
        assertTrue( 0.001 > errD2);
    }
}