package edu.columbia.tjw.item;

import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.base.raw.RawFittingGrid;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
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
    private final ItemFittingGrid<SimpleStatus, SimpleRegressor> _rawData;
    private final SimpleRegressor _intercept;

    public ItemModelTest()
    {
        try (final InputStream iStream = ItemModelTest.class.getResourceAsStream("/raw_data.dat"))
        {
            _rawData = RawFittingGrid.readFromStream(iStream, SimpleStatus.class, SimpleRegressor.class);
            _intercept = _rawData.getRegressorFamily().getFromName("INTERCEPT");
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

    private void testGradients(ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params)
    {
        PackedParameters<SimpleStatus, SimpleRegressor, StandardCurveType> origPacked = params.generatePacked();
        ParamFittingGrid<SimpleStatus, SimpleRegressor, StandardCurveType> paramGrid = new ParamFittingGrid<>(params,
                _rawData);
        final double[] beta = origPacked.getPacked();
        ItemModel<SimpleStatus, SimpleRegressor, StandardCurveType> orig = new ItemModel<>(params);

        PackedParameters<SimpleStatus, SimpleRegressor, StandardCurveType> repacked = origPacked.clone();
        final int paramCount = beta.length;
        final double[] gradient = new double[paramCount];
        final double[] fdGradient = new double[paramCount];
        final double[][] secondDerivative = new double[paramCount][paramCount];
        final double[][] fdSecondDerivative = new double[paramCount][paramCount];

        final double[] workspace = new double[paramCount];

        double maxEpsilon = Double.MIN_VALUE;

        for (int k = 0; k < Math.min(10000, paramGrid.size()); k++)
        {
            orig.computeGradient(paramGrid, k, gradient, secondDerivative);

            final double sdSymmEpsilon = checkSymmetry(secondDerivative);

            // Roughly speaking, we want this less than sqrt(machineEpsilon).
            Assertions.assertTrue(sdSymmEpsilon < 1.0e-8);

            for (int i = 0; i < paramCount; i++)
            {
                final double[] testBeta = beta.clone();
                //final double h = Math.abs(testBeta[i] * 0.00001) + 1.0e-8;
                final double h = 1.0e-6;
                testBeta[i] = testBeta[i] + h;
                repacked.updatePacked(testBeta);

                ItemModel<SimpleStatus, SimpleRegressor, StandardCurveType> adjusted = new ItemModel<>(
                        repacked);

                final double origLL = orig.logLikelihood(paramGrid, k);
                final double shiftLL = adjusted.logLikelihood(paramGrid, k);

                final double fdd = (shiftLL - origLL) / h;

                fdGradient[i] = fdd;

                // Now same for gradient.
                adjusted.computeGradient(paramGrid, k, fdSecondDerivative[i],
                        null);

                for (int z = 0; z < paramCount; z++)
                {
                    fdSecondDerivative[i][z] = (fdSecondDerivative[i][z] - gradient[z]) / h;
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
                orig.computeGradient(paramGrid, k, g2, s2);
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
    }


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