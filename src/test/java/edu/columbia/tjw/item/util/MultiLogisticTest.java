package edu.columbia.tjw.item.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class MultiLogisticTest
{


    public MultiLogisticTest()
    {
    }


    @Test
    void basicTest()
    {
        // Some not crazy power scores.
        final double[] powerScores = new double[]{100.0, 44.3, 21.6, 94.7};

        final double[] probs = new double[powerScores.length];

        MultiLogistic.multiLogisticFunction(powerScores, probs);

        Assertions.assertArrayEquals(probs, new double[]{0.9950331983497579, 9.311145511636196E-14, 9.311145511636196E-14, 0.00496680165005605}, 1.0e-14, "Logistic test failed.");
    }

    @Test
    void gradientTest()
    {
        final double h = 0.001;

        final double[] powerScore = new double[]{1.0, 0.9, 0.8, 0.7};
        final int size = powerScore.length;

        final double[] p1 = new double[size];
        MultiLogistic.multiLogisticFunction(powerScore, p1);

        for (int w = 0; w < size; w++)
        {
            final double[] p2 = new double[size];
            final double[] g1 = new double[size];
            final double[] g2 = new double[size];
            MultiLogistic.powerScoreEntropyGradient(p1, w, g1);
            final double baseEntropy = MultiLogistic.multiLogisticEntropy(p1, w);

            for (int i = 0; i < size; i++)
            {
                final double[] s2 = powerScore.clone();
                s2[i] += h;

                MultiLogistic.multiLogisticFunction(s2, p2);
                final double shiftEntropy = MultiLogistic.multiLogisticEntropy(p2, w);

                final double gradContribution = (shiftEntropy - baseEntropy) / h;
                g2[i] = gradContribution;
            }

            Assertions.assertArrayEquals(g1, g2, 1.0e-3, "Gradient mismatch.");
        }
    }

    @Test
    void hessianTest() throws Exception
    {
        final double h = 0.001;
        final double[] powerScore = new double[]{1.0, 0.9, 0.8, 0.7};
        final int size = powerScore.length;

        final double[] p1 = new double[size];
        final double[][] h1 = new double[size][size];
        MultiLogistic.multiLogisticFunction(powerScore, p1);
        MultiLogistic.powerScoreEntropyHessian(p1, h1);

        for (int w = 0; w < size; w++)
        {
            final double[] p2 = new double[size];
            final double[][] h2 = new double[size][size];

            final double[] baseGradient = new double[size];
            MultiLogistic.powerScoreEntropyGradient(p1, w, baseGradient);

            //final double baseEntropy = MultiLogistic.multiLogisticEntropy(p1, actualIndex);

            for (int i = 0; i < size; i++)
            {
                final double[] s2 = powerScore.clone();
                s2[i] += h;

                MultiLogistic.multiLogisticFunction(s2, p2);
                final double[] shiftGradient = new double[size];
                MultiLogistic.powerScoreEntropyGradient(p2, w, shiftGradient);

                for (int z = 0; z < size; z++)
                {
                    final double gradContribution = (shiftGradient[z] - baseGradient[z]) / h;
                    h2[i][z] = gradContribution;
                }
            }

            for (int z = 0; z < size; z++)
            {
                Assertions.assertArrayEquals(h1[z], h2[z], 1.0e-2, "Gradient mismatch.");
            }
        }
    }
}
