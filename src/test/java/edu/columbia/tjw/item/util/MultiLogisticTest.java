package edu.columbia.tjw.item.util;

import com.sun.rowset.internal.Row;
import edu.columbia.tjw.item.util.random.RandomTool;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;

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

    @Test
    void equivalenceTest() throws Exception
    {
        final int outputSize = 2;
        final int xSize = 1;
        final int observationCount = 1000;
        final double h = 0.00001;
        final RandomGenerator gen = RandomTool.getRandomGenerator();

        final double[][] x = new double[observationCount][xSize];

        for (int i = 0; i < observationCount; i++)
        {
            //x[i][0] = 1.0;

            for (int k = 0; k < xSize; k++)
            {
                x[i][k] = gen.nextDouble();
            }
        }

        final double[][] theta = new double[outputSize][xSize];
        final int thetaCount = (outputSize - 1) * xSize; // First row is always zero.
        final int xCount = xSize; // First element is always 1.

        for (int w = 1; w < outputSize; w++)
        {
            for (int k = 0; k < xCount; k++)
            {
                theta[w][k] = gen.nextDouble();
            }
        }

        final int[] y = new int[observationCount];
        final double[] modelProb = new double[outputSize];

        for (int i = 0; i < outputSize; i++)
        {
            y[i] = i;
        }

        for (int i = outputSize; i < observationCount; i++)
        {
            final double[] powerScores = powerScores(x[i], theta);
            //y[i] = gen.nextInt(outputSize);
            MultiLogistic.multiLogisticFunction(powerScores, modelProb);
            y[i] = MultiLogistic.chooseOne(modelProb, gen);
        }

        final double[] gradientX = new double[xCount];
        final double[][] hessianX = new double[xCount][xCount];
        final double[][] allGradientsX = new double[observationCount][];
        final double[][][] allHessX = new double[observationCount][][];

        final double[] gradientT = new double[thetaCount];
        final double[][] hessianT = new double[thetaCount][thetaCount];
        final double[][] allGradientsT = new double[observationCount][];
        final double[][][] allHessT = new double[observationCount][][];


        for (int i = 0; i < observationCount; i++)
        {
            final double[] powerScores = powerScores(x[i], theta);

            final double[] gradientS = new double[outputSize];
            final double[][] hessianS = new double[outputSize][outputSize];

            MultiLogistic.multiLogisticFunction(powerScores, modelProb);
            MultiLogistic.powerScoreEntropyGradient(modelProb, y[i], gradientS);
            MultiLogistic.powerScoreEntropyHessian(modelProb, hessianS);
            final double baseEntropy = crossEntropy(x[i], theta, y[i]);

            // Fill the gradient with respect to X and theta.
            final double[] nextGradX = gradientX(x[i], theta, gradientS);
            final double[] nextGradT = gradientT(x[i], theta, gradientS);

            final double[] testGradX = new double[nextGradX.length];

            // Test X gradient
            for (int k = 0; k < xSize; k++)
            {
                final double[] x2 = x[i].clone();
                x2[k] += h;
                final double nextEntropy = crossEntropy(x2, theta, y[i]);

                testGradX[k] = (nextEntropy - baseEntropy) / h;
            }

            Assertions.assertArrayEquals(nextGradX, testGradX, 0.001, "X gradient failed");

            final double[] testGradT = new double[nextGradT.length];

            // Test Theta Gradient
            for (int w = 1; w < outputSize; w++)
            {
                final double[][] theta2 = theta.clone();

                for (int k = 0; k < xSize; k++)
                {
                    theta2[w] = theta[w].clone();
                    theta2[w][k] += h;
                    final double nextEntropy = crossEntropy(x[i], theta2, y[i]);

                    final int index = packIndex(w, k, theta);
                    testGradT[index] = (nextEntropy - baseEntropy) / h;
                }
            }

            Assertions.assertArrayEquals(nextGradT, testGradT, 0.001, "Theta gradient failed");

            // Fill the hessian with respecc to X and theta .
            final double[][] nextHessX = hessianX(x[i], theta, hessianS);
            final double[][] nextHessT = hessianT(x[i], theta, hessianS);

            final double[][] testHessX = new double[nextHessX.length][nextHessX.length];

            // Test X hessian.
            for (int k = 0; k < xSize; k++)
            {
                final double[] x2 = x[i].clone();
                x2[k] += h;

                final double[] p2 = powerScores(x2, theta);
                final double[] m2 = new double[p2.length];
                final double[] g2S = new double[outputSize];

                MultiLogistic.multiLogisticFunction(p2, m2);
                MultiLogistic.powerScoreEntropyGradient(m2, y[i], g2S);
                final double[] shiftGrad = gradientX(x2, theta, g2S);

                for (int k2 = 0; k2 < xSize; k2++)
                {
                    testHessX[k][k2] = (shiftGrad[k2] - nextGradX[k2]) / h;
                }
            }

            for(int k = 0; k < xSize; k++) {
                Assertions.assertArrayEquals(nextHessX[k], testHessX[k], 0.001, "X hessian failed: " + k);
            }

            final double[][] testHessT = new double[thetaCount][thetaCount];

            // Test theta hessian.
            for (int w = 1; w < outputSize; w++)
            {
                final double[][] theta2 = theta.clone();

                for (int k = 0; k < xSize; k++)
                {
                    theta2[w] = theta[w].clone();
                    theta2[w][k] += h;

                    final double[] p2 = powerScores(x[i], theta2);
                    final double[] m2 = new double[p2.length];
                    final double[] g2S = new double[outputSize];

                    MultiLogistic.multiLogisticFunction(p2, m2);
                    MultiLogistic.powerScoreEntropyGradient(m2, y[i], g2S);
                    final double[] shiftGrad = gradientT(x[i], theta2, g2S);

                    final int index = packIndex(w, k, theta);

                    for(int z = 0; z < thetaCount; z++) {
                        testHessT[z][index] = (shiftGrad[z] - nextGradT[z]) / h;
                    }
                }
            }

            for(int z = 0; z < thetaCount; z++) {
                Assertions.assertArrayEquals(nextHessT[z], testHessT[z], 0.001, "Theta hessian failed: " + z);
            }

            for(int z = 0; z < thetaCount; z++) {
                gradientT[z] += nextGradT[z];

                for(int z2 = 0; z2 < thetaCount; z2++) {
                    hessianT[z][z2] += nextHessT[z][z2];
                }
            }

            for(int k = 0; k < xSize; k++) {
                gradientX[k] += nextGradX[k];

                for(int k2 = 0; k2 < xSize; k2++) {
                    hessianX[k][k2] += nextHessX[k][k2];
                }
            }

            allGradientsX[i] = nextGradX;
            allGradientsT[i] = nextGradT;
            allHessX[i] = nextHessX;
            allHessT[i] = nextHessT;
        }

        RealMatrix hessMatrixT = new Array2DRowRealMatrix(hessianT);
        final SingularValueDecomposition svd = new SingularValueDecomposition(hessMatrixT);

        System.out.println("Hess condition number: " + svd.getConditionNumber());
        System.out.println("Singular values: " + Arrays.toString(svd.getSingularValues()));

        final RealMatrix inverseHessT = MatrixUtils.inverse(hessMatrixT);

        RealMatrix hessMatrixX = new Array2DRowRealMatrix(hessianX);
        final SingularValueDecomposition svdX = new SingularValueDecomposition(hessMatrixX);

        System.out.println("Hess condition number[X]: " + svdX.getConditionNumber());
        System.out.println("Singular values[X]: " + Arrays.toString(svdX.getSingularValues()));

        final RealMatrix inverseHessX = MatrixUtils.inverse(hessMatrixX);

        System.out.println("Inverse matrix: " + inverseHessX);

        double termX = 0.0;
        double termT = 0.0;

        for(int i = 0; i < observationCount; i++) {
            final RealMatrix gradX = new Array2DRowRealMatrix(allGradientsX[i]);
            final RealMatrix gradT = new Array2DRowRealMatrix(allGradientsT[i]);

            final RealMatrix inverseHessX2 = MatrixUtils.inverse(new Array2DRowRealMatrix(allHessX[i]));
            final RealMatrix inverseHessT2 = MatrixUtils.inverse(new Array2DRowRealMatrix(allHessT[i]));

            final RealMatrix resX = gradX.multiply(inverseHessX2).multiply(gradX);
            final double tX = resX.getEntry(0, 0);

            final RealMatrix resT = gradT.multiply(inverseHessT2).multiply(gradT);
            final double tT = resT.getEntry(0, 0);

            termX += tX;
            termT += tT;
        }

        System.out.println("Done.");

    }


    private static double[] powerScores(final double[] x_, final double[][] theta_)
    {
        final int outputSize = theta_.length;
        final int xSize = x_.length;
        final double[] powerScores = new double[theta_.length];

        for (int w = 0; w < outputSize; w++)
        {
            double sum = 0.0;

            for (int k = 0; k < xSize; k++)
            {
                sum += theta_[w][k] * x_[k];
            }

            powerScores[w] = sum;
        }

        return powerScores;
    }

    private static double crossEntropy(final double[] x_, final double[][] theta_, final int y_)
    {
        final double[] powerScores = powerScores(x_, theta_);
        final double[] probabilities = new double[powerScores.length];
        MultiLogistic.multiLogisticFunction(powerScores, probabilities);
        final double crossEntropy = MultiLogistic.multiLogisticEntropy(probabilities, y_);
        return crossEntropy;
    }

    private static double[] gradientX(final double[] x_, final double[][] theta_, final double[] gradientS_)
    {
        final double[] output = new double[x_.length];

        for (int k = 0; k < x_.length; k++)
        {
            for (int w = 0; w < theta_.length; w++)
            {
                output[k] += gradientS_[w] * theta_[w][k];
            }
        }

        return output;
    }

    /**
     * Use the chain rule. We already have d2H/ds2, use the chain rule.
     * <p>
     * d2H/ds1 ds2 = (d2H / ds1 ds2) * (ds1/dz1) * (ds2/dz2) + otherTerm
     * <p>
     * This is complicated by the fact that we have a sum in there (double sum for second derivative)
     * because changing a single X changes each power score with a different theta.
     * <p>
     * The other term is actually zero, since d2s / dz1dz2 = 0 for our linear power scores.
     *
     * @param x_
     * @param theta_
     * @param hessianS_
     * @return
     */
    private static double[][] hessianX(final double[] x_, final double[][] theta_, final double[][] hessianS_)
    {
        final int outputSize = x_.length;
        final double[][] hessianX = new double[outputSize][outputSize];

        for (int k = 0; k < x_.length; k++)
        {
            for (int k2 = 0; k2 < x_.length; k2++)
            {
                for (int w = 0; w < theta_.length; w++)
                {
                    for (int w2 = 0; w2 < theta_.length; w2++)
                    {
                        hessianX[k][k2] += hessianS_[w][w2] * theta_[w][k] * theta_[w2][k2];
                    }
                }
            }
        }

        return hessianX;
    }


    private static int packIndex(final int w_, final int k_, double[][] theta_)
    {
        // First row of theta is always all zero, these are not enumerated parameters.
        return ((w_ - 1) * theta_[0].length) + k_;
    }

    private static double[] gradientT(final double[] x_, final double[][] theta_, final double[] gradientS_)
    {
        final int thetaCount = (theta_.length - 1) * x_.length;
        final double[] output = new double[thetaCount];

        for (int k = 0; k < x_.length; k++)
        {
            for (int w = 1; w < theta_.length; w++)
            {
                final int index = packIndex(w, k, theta_); // 1-D index from a 2-D param matrix.
                output[index] = gradientS_[w] * x_[k];
            }
        }

        return output;
    }

    private static double[][] hessianT(final double[] x_, final double[][] theta_, final double[][] hessianS_)
    {
        final int thetaCount = (theta_.length - 1) * x_.length;
        final double[][] hessianT = new double[thetaCount][thetaCount];

        for (int k = 0; k < x_.length; k++)
        {
            for (int k2 = 0; k2 < x_.length; k2++)
            {
                for (int w = 1; w < theta_.length; w++)
                {
                    for (int w2 = 1; w2 < theta_.length; w2++)
                    {
                        final int index1 = packIndex(w, k, theta_);
                        final int index2 = packIndex(w2, k2, theta_);
                        hessianT[index1][index2] += hessianS_[w][w2] * x_[k] * x_[k2];
                    }
                }
            }
        }

        return hessianT;
    }


}
