package edu.columbia.tjw.item.base;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.util.MathTools;
import edu.columbia.tjw.item.util.random.PrngType;
import edu.columbia.tjw.item.util.random.RandomTool;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StandardCurveFactoryTest
{

    private double testCurve(final StandardCurveType type_,
                             final ItemCurveFactory<SimpleRegressor, StandardCurveType> factory_,
                             final Random rand_)
    {
        final double center = rand_.nextDouble();
        final double slope = 0.1 + rand_.nextDouble();

        final ItemCurve<StandardCurveType> base = factory_.generateCurve(type_,
                0, new double[]{center, slope});

        final double x = rand_.nextDouble();
        final double h = 0.001;
        final double split = rand_.nextDouble();

        final double h1 = split * h;
        final double h2 = (1.0 - split) * h;

        final ItemCurve<StandardCurveType> c2 = factory_.generateCurve(type_,
                0, new double[]{center + h1, slope + h2});

        final double v1 = base.transform(x);
        final double v2 = c2.transform(x);
        final double fd = (v2 - v1);

        final double g1 = base.derivative(0, x);
        final double g2 = base.derivative(1, x);
        final double derivative = h1 * g1 + h2 * g2;

        // When val is close to 1, we get lots of round off error here.
        final double skew = Math.abs(derivative - fd) / (Math.abs(fd) + 0.001);
        assertTrue(skew < 0.05);

        final double s2 = Math.abs(base.secondDerivative(0, 1, x) - base.secondDerivative(1, 0, x));
        assertTrue(s2 < 1.0e-8);

        final double a = base.secondDerivative(0, 0, x);
        final double b = base.secondDerivative(0, 1, x);
        final double c = base.secondDerivative(1, 1, x);

        final double g1a = c2.derivative(0, x);
        final double g2a = c2.derivative(1, x);

        final double g1Act = (g1a - g1);
        final double g1Exp = h1 * a + h2 * b;

        final double g2Act = (g2a - g2);
        final double g2Exp = h1 * b + h2 * c;

        final double cos = MathTools.cos(new double[]{g1Act, g2Act}, new double[]{g1Exp, g2Exp});
        assertTrue(cos > 0.99);

        return Math.max(skew, 1.0 - cos);
    }

    private double testSecondDerivativeIsolated(final StandardCurveType type_,
                                                final ItemCurveFactory<SimpleRegressor, StandardCurveType> factory_,
                                                final Random rand_, int index_)
    {
        final double center = rand_.nextDouble();
        final double slope = 0.1 + rand_.nextDouble();
        final double[] orig = new double[]{center, slope};

        final ItemCurve<StandardCurveType> base = factory_.generateCurve(type_,
                0, orig);

        final double x = rand_.nextDouble();
        final double h = 0.001;
        final double[] shift = orig.clone();
        shift[index_] += h;

        final ItemCurve<StandardCurveType> c1 = factory_.generateCurve(type_,
                0, shift);

        final double s2 = Math.abs(base.secondDerivative(0, 1, x) - base.secondDerivative(1, 0, x));
        assertTrue(s2 < 1.0e-8);

        final double[] gBase = new double[]{base.derivative(0, x), base.derivative(1, x)};
        final double[] gShift = new double[]{c1.derivative(0, x), c1.derivative(1, x)};

        final double[] diff = new double[2];
        diff[0] = gShift[0] - gBase[0];
        diff[1] = gShift[1] - gBase[1];

        final double[] expected = new double[2];
        expected[0] = h * base.secondDerivative(0, index_, x);
        expected[1] = h * base.secondDerivative(1, index_, x);

        final double cos = MathTools.cos(expected, diff);
        assertTrue(cos >= 0.99);
        return 1.0 - cos;
    }

    @org.junit.jupiter.api.Test
    void testSecondDerivatives()
    {
        ItemCurveFactory<SimpleRegressor, StandardCurveType> factory = new StandardCurveFactory<>();

        final byte[] seed = RandomTool.getStrong(32);
        System.out.println("Seed: " + Arrays.toString(seed));
        final Random rand = RandomTool.getRandom(PrngType.SECURE, seed);

        double maxSkew = 0.0;

        for (StandardCurveType type : StandardCurveType.values())
        {
            for (int i = 0; i < 100; i++)
            {
                final double s1 = this.testSecondDerivativeIsolated(type, factory, rand, 0);
                final double s2 = this.testSecondDerivativeIsolated(type, factory, rand, 1);

                maxSkew = Math.max(maxSkew, Math.max(s1, s2));
            }
        }

        System.out.println("Max 2nd derivative skew: " + maxSkew);
    }

    @org.junit.jupiter.api.Test
    void testAllDerivatives()
    {
        ItemCurveFactory<SimpleRegressor, StandardCurveType> factory = new StandardCurveFactory<>();

        final byte[] seed = RandomTool.getStrong(32);
        System.out.println("Seed: " + Arrays.toString(seed));
        final Random rand = RandomTool.getRandom(PrngType.SECURE, seed);

        double maxSkew = 0.0;

        for (StandardCurveType type : StandardCurveType.values())
        {
            for (int i = 0; i < 1000; i++)
            {
                double thisSkew = testCurve(type, factory, rand);
                maxSkew = Math.max(thisSkew, maxSkew);
            }
        }

        System.out.println("Max skew: " + maxSkew);
    }


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
        final double errD1 = Math.abs(d1 - approxD1) / Math.abs(d1);
        assertTrue(0.0001 > errD1);

        final double h = 0.001;
        ItemCurve<StandardCurveType> g3 = factory.generateCurve(StandardCurveType.GAUSSIAN,
                0, new double[]{0.5, 3.0 + h});

        final double d2 = gaussian.derivative(1, 2.2);
        final double b = g3.transform(2.2);
        final double a = gaussian.transform(2.2);
        final double approxD2 = (b - a) / h;
        final double errD2 = Math.abs(d2 - approxD2) / Math.abs(d2);
        assertTrue(0.001 > errD2);
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
        final double errD1 = Math.abs(d1 - approxD1) / Math.abs(d1);
        assertTrue(0.001 > errD1);

        final double h = 0.00001;
        ItemCurve<StandardCurveType> g3 = factory.generateCurve(StandardCurveType.LOGISTIC,
                0, new double[]{0.5, 3.0 + h});

        final double d2 = logistic.derivative(1, 0.7);
        final double b = g3.transform(0.7);
        final double a = logistic.transform(0.7);
        final double approxD2 = (b - a) / h;
        final double errD2 = Math.abs(d2 - approxD2) / Math.abs(d2);
        assertTrue(0.001 > errD2);
    }
}