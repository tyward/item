package edu.columbia.tjw.item;

import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.base.raw.RawFittingGrid;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.EntropyCalculator;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.calculator.FitPoint;
import edu.columbia.tjw.item.fit.calculator.FitPointAnalyzer;
import edu.columbia.tjw.item.optimize.OptimizationTarget;
import edu.columbia.tjw.item.util.MathTools;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;

class ItemModelTest
{
    private static final double EPSILON = Math.ulp(1.0);

    private final ItemFittingGrid<SimpleStatus, SimpleRegressor> _rawData;

    public ItemModelTest()
    {
        try (final InputStream iStream = ItemModelTest.class.getResourceAsStream("/raw_data.dat"))
        {
            _rawData = RawFittingGrid.readFromStream(iStream, SimpleStatus.class, SimpleRegressor.class);
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    @BeforeEach
    void setUp()
    {

    }

    /**
     * See if a is approximately equal to b, return the largest singular value of (a-b) divided by the average of the
     * largest singular values of a and b.
     */
    private double checkEquality(final double[][] a, final double[][] b)
    {
        final RealMatrix aMatrix = new Array2DRowRealMatrix(a);
        final RealMatrix bMatrix = new Array2DRowRealMatrix(b);

        final RealMatrix diffMatrix = aMatrix.subtract(bMatrix);

        final SingularValueDecomposition aDecomp = new SingularValueDecomposition(aMatrix);
        final SingularValueDecomposition bDecomp = new SingularValueDecomposition(bMatrix);
        final SingularValueDecomposition diffDecomp = new SingularValueDecomposition(diffMatrix);

        final double aVal = aDecomp.getSingularValues()[0];
        final double bVal = bDecomp.getSingularValues()[0];
        final double diffVal = diffDecomp.getSingularValues()[0];

        final double denominator = 0.5 * (aVal + bVal);
        final double valRatio = diffVal / denominator;

        return valRatio;
    }

    private double checkSymmetry(final double[][] matrix)
    {
        RealMatrix wrapped = new Array2DRowRealMatrix(matrix);
        RealMatrix residual = wrapped.subtract(wrapped.transpose());

        SingularValueDecomposition wrappedDecomp = new SingularValueDecomposition(wrapped);
        SingularValueDecomposition residualDecomp = new SingularValueDecomposition(residual);

        final double wrappedMax = wrappedDecomp.getSingularValues()[0];
        final double residMax = residualDecomp.getSingularValues()[0];

        final double valRatio = residMax / wrappedMax;

        if (valRatio > 1.0e-4)
        {
            System.out.println("Major value fail: " + valRatio);
        }

        return valRatio;
    }

    private void testIceGradients(ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params)
    {
        PackedParameters<SimpleStatus, SimpleRegressor, StandardCurveType> origPacked = params.generatePacked();
        ParamFittingGrid<SimpleStatus, SimpleRegressor, StandardCurveType> paramGrid = new ParamFittingGrid<>(params,
                _rawData);

        final double[] beta = origPacked.getPacked();
        final ItemModel<SimpleStatus, SimpleRegressor, StandardCurveType> orig = new ItemModel<>(params);

        ItemSettings settings = new ItemSettings();

        final EntropyCalculator<SimpleStatus, SimpleRegressor, StandardCurveType> calculator =
                new EntropyCalculator<>(paramGrid);
        final FitPointAnalyzer entropyAnalyzer = new FitPointAnalyzer(settings.getBlockSize(), 5.0,
                OptimizationTarget.ENTROPY);
        final FitPointAnalyzer ticAnalyzer = new FitPointAnalyzer(settings.getBlockSize(), 5.0,
                OptimizationTarget.TIC);
        final FitPointAnalyzer iceAnalyzer = new FitPointAnalyzer(settings.getBlockSize(), 5.0,
                OptimizationTarget.ICE);
        final FitPointAnalyzer ice2Analyzer = new FitPointAnalyzer(settings.getBlockSize(), 5.0,
                OptimizationTarget.ICE2);

        final FitPoint point = calculator.generatePoint(params);

        final double entropy = entropyAnalyzer.computeObjective(point, point.getBlockCount());
        final double tic = ticAnalyzer.computeObjective(point, point.getBlockCount());
        final double ice = iceAnalyzer.computeObjective(point, point.getBlockCount());
        final double ice2 = ice2Analyzer.computeObjective(point, point.getBlockCount());

        final double[] grad = entropyAnalyzer.getDerivative(point);
        final double[] ticGrad = ticAnalyzer.getDerivative(point);
        final double[] iceGrad = iceAnalyzer.getDerivative(point);
        final double[] ice2Grad = ice2Analyzer.getDerivative(point);

        final int dimension = grad.length;

        final double[] fdGrad = new double[dimension];
        final double[] fdTicGrad = new double[dimension];
        final double[] fdIceGrad = new double[dimension];
        final double[] fdIce2Grad = new double[dimension];

        //final FitPoint[] shiftPoints = new FitPoint[dimension];
        //final double[] shiftSizes = new double[dimension];
        final double gradEpsilon = EPSILON * MathTools.maxAbsElement(grad);

        for (int i = 0; i < dimension; i++)
        {
            final double h = 1.0e-8;
            final double absGrad = Math.abs(grad[i]);
            final double shiftSize;

            if (absGrad > gradEpsilon)
            {
                // Generate an appropriate level of
                shiftSize = h / absGrad;
            }
            else
            {
                // Doesn't really matter what we put here...
                shiftSize = h;
            }

            final PackedParameters<SimpleStatus, SimpleRegressor, StandardCurveType> repacked = origPacked.clone();
            repacked.setParameter(i, beta[i] + shiftSize);
            final FitPoint shiftPoint = calculator.generatePoint(repacked.generateParams());

            final double shiftEntropy = entropyAnalyzer.computeObjective(shiftPoint, shiftPoint.getBlockCount());
            final double shiftTic = ticAnalyzer.computeObjective(shiftPoint, shiftPoint.getBlockCount());
            final double shiftIce = iceAnalyzer.computeObjective(shiftPoint, shiftPoint.getBlockCount());
            final double shiftIce2 = ice2Analyzer.computeObjective(shiftPoint, shiftPoint.getBlockCount());

            fdGrad[i] = (shiftEntropy - entropy) / shiftSize;
            fdTicGrad[i] = (shiftTic - tic) / shiftSize;
            fdIceGrad[i] = (shiftIce - ice) / shiftSize;
            fdIce2Grad[i] = (shiftIce2 - ice2) / shiftSize;
        }

        System.out.println("ICE Cos: " + MathTools.cos(iceGrad, grad));
        System.out.println("ICE2 Cos: " + MathTools.cos(ice2Grad, grad));

        System.out.println("Entropy FD Cos: " + MathTools.cos(grad, fdGrad));
        System.out.println("ICE FD Cos: " + MathTools.cos(iceGrad, fdIceGrad));
        System.out.println("ICE2 FD Cos: " + MathTools.cos(ice2Grad, fdIce2Grad));

        final double[] fdTicAdj = new double[dimension];
        final double[] fdIceAdj = new double[dimension];
        final double[] fdIce2Adj = new double[dimension];

        final double[] ticAdj = new double[dimension];
        final double[] iceAdj = new double[dimension];
        final double[] ice2Adj = new double[dimension];

        for (int i = 0; i < dimension; i++)
        {
            fdTicAdj[i] = fdTicGrad[i] - fdGrad[i];
            fdIceAdj[i] = fdIceGrad[i] - fdGrad[i];
            fdIce2Adj[i] = fdIce2Grad[i] - fdGrad[i];

            ticAdj[i] = ticGrad[i] - grad[i];
            iceAdj[i] = iceGrad[i] - grad[i];
            ice2Adj[i] = ice2Grad[i] - grad[i];
        }

        System.out.println("TIC FD Adj Cos: " + MathTools.cos(fdTicAdj, ticAdj));
        System.out.println("ICE FD Adj Cos: " + MathTools.cos(fdIceAdj, iceAdj));
        System.out.println("ICE2 FD Adj Cos: " + MathTools.cos(fdIce2Adj, ice2Adj));

        System.out.println("Done!");
    }


    private void testGradients(ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params)
    {
        final PackedParameters<SimpleStatus, SimpleRegressor, StandardCurveType> origPacked = params.generatePacked();
        final ParamFittingGrid<SimpleStatus, SimpleRegressor, StandardCurveType> paramGrid =
                new ParamFittingGrid<>(params,
                        _rawData);
        final double[] beta = origPacked.getPacked();
        final ItemModel<SimpleStatus, SimpleRegressor, StandardCurveType> orig = new ItemModel<>(params);

        final PackedParameters<SimpleStatus, SimpleRegressor, StandardCurveType> repacked = origPacked.clone();
        final int paramCount = beta.length;
        final double[] gradient = new double[paramCount];
        final double[] fdGradient = new double[paramCount];
        final double[] jDiag = new double[paramCount];
        final double[][] secondDerivative = new double[paramCount][paramCount];
        final double[][] fdSecondDerivative = new double[paramCount][paramCount];

        double maxEpsilon = Double.MIN_VALUE;

        for (int k = 0; k < Math.min(10000, paramGrid.size()); k++)
        {
            orig.computeGradient(paramGrid, k, gradient, jDiag, secondDerivative);

            final double sdSymmEpsilon = checkSymmetry(secondDerivative);

            final double gradEpsilon = EPSILON * MathTools.maxAbsElement(gradient);

            // Roughly speaking, we want this less than sqrt(machineEpsilon).
            Assertions.assertTrue(sdSymmEpsilon < 1.0e-8);

            for (int i = 0; i < paramCount; i++)
            {
                final double[] testBeta = beta.clone();
                //final double h = Math.abs(testBeta[i] * 0.00001) + 1.0e-8;
                final double h = 1.0e-8;
                final double absGrad = Math.abs(gradient[i]);
                final double shiftSize;

                if (absGrad > gradEpsilon)
                {
                    // Generate an appropriate level of
                    shiftSize = h / absGrad;
                }
                else
                {
                    // Doesn't really matter what we put here...
                    shiftSize = h;
                }

                testBeta[i] = testBeta[i] + shiftSize;

                repacked.updatePacked(testBeta);

                ItemModel<SimpleStatus, SimpleRegressor, StandardCurveType> adjusted = new ItemModel<>(
                        repacked);

                final double origLL = orig.logLikelihood(paramGrid, k);
                final double shiftLL = adjusted.logLikelihood(paramGrid, k);

                final double fdd = (shiftLL - origLL) / shiftSize;

                fdGradient[i] = fdd;

                // Now same for gradient.
                adjusted.computeGradient(paramGrid, k, fdSecondDerivative[i], jDiag,
                        null);

                for (int z = 0; z < paramCount; z++)
                {
                    fdSecondDerivative[i][z] = (fdSecondDerivative[i][z] - gradient[z]) / shiftSize;
                }
            }

            final double cos = MathTools.cos(gradient, fdGradient);

            if (cos < 0.99999)
            {
                System.out.println("Weak Cos[" + k + "]: " + cos);
            }


            Assertions.assertTrue(cos > 0.99);

            final double fdSdSymmEpsilon = checkSymmetry(fdSecondDerivative);

            // Roughly speaking, we want this less than sqrt(machineEpsilon).
            if (fdSdSymmEpsilon > 1.0e-4)
            {
                System.out.println("FD second derivative not symmetric: " + fdSdSymmEpsilon);
            }

            // Now validate cosine similarity of the rows of the second derivative.
            final double matrixEpsilon = checkEquality(fdSecondDerivative, secondDerivative);

            System.out.println("MatrixEpsilon[" + k + "]:" + matrixEpsilon);

            if (matrixEpsilon >= 1.0e-4)
            {
                System.out.println("Blah2");
                checkEquality(fdSecondDerivative, secondDerivative);
                final double[] g2 = new double[paramCount];
                final double[][] s2 = new double[paramCount][paramCount];
                orig.computeGradient(paramGrid, k, g2, jDiag, s2);
            }

            maxEpsilon = Math.max(maxEpsilon, matrixEpsilon);

            Assertions.assertTrue(matrixEpsilon < 1.0e-4);


        }

        System.out.println("Max Epsilon: " + maxEpsilon);
    }

    private ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> readParams(final InputStream input)
            throws IOException
    {
        final ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> result =
                ItemParameters.readFromStream(input, SimpleStatus.class,
                        SimpleRegressor.class, StandardCurveType.class);
        return result;
    }

    private void writeParams(final File outputFile,
                             ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params) throws IOException
    {
        try (final FileOutputStream fOut = new FileOutputStream(outputFile);
             final ObjectOutputStream oOut = new ObjectOutputStream(fOut))
        {
            oOut.writeObject(params);
        }
    }

    /**
     * Zero out the beta param to remove some moving parts here, make sure everything matches up.
     */
    @Test
    void basicGradient() throws Exception
    {
        ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params =
                readParams(ItemModelTest.class.getResourceAsStream("/test_model_small.dat"));

        PackedParameters<SimpleStatus, SimpleRegressor, StandardCurveType> packed = params.generatePacked();

        final double[] raw = packed.getPacked();
        raw[1] = 0.0;
        packed.updatePacked(raw);

        testGradients(packed.generateParams());
    }

    /**
     * Zero out the beta param to remove some moving parts here, make sure everything matches up.
     */
    @Test
    void testSmallGradient() throws Exception
    {
        ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params =
                readParams(ItemModelTest.class.getResourceAsStream("/test_model_small.dat"));

        testGradients(params);
    }

    @Test
    void testMediumGradient() throws Exception
    {
        ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params =
                readParams(ItemModelTest.class.getResourceAsStream("/test_model_medium.dat"));

        testGradients(params);
        //testIceGradients(params);
    }

//    @Test
//    void testLargeGradient() throws Exception
//    {
//        ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params =
//                readParams(ItemModelTest.class.getResourceAsStream("/test_model_large.dat"));
//
//        //testGradients(params);
//        testIceGradients(params);
//    }


//    @Test
//    void computeGradient() throws Exception
//    {
//        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
//                makeFitter();
//
//        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
//                .fitCoefficients();
//        Assertions.assertTrue(result.getEndingLL() < 0.2024);
//
//        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.expandModel(_curveRegs, 12);
//
//        System.out.println(fitter.getChain());
//        System.out.println("Next param: " + r3.getEndingParams());
//        //Assertions.assertTrue(r3.getEndingLL() < 0.195);
//
//        ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params = r3.getEndingParams();
//
//        final File outputFolder = new File("/Users/tyler/sync-workspace/code/outputModels");
//
//        writeParams(new File(outputFolder, "test_model.dat"), params);
//
//        testGradients(params);
//    }

    @Test
    void transitionProbability()
    {
    }
}