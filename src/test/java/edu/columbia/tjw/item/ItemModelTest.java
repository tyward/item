package edu.columbia.tjw.item;

import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveFactory;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.ItemFitter;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.param.ParamFitResult;
import edu.columbia.tjw.item.util.MathTools;
import edu.columbia.tjw.item.util.random.PrngType;
import edu.columbia.tjw.item.util.random.RandomTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelTest
{
    private final ItemFittingGrid<SimpleStatus, SimpleRegressor> _rawData;
    private final SimpleRegressor _intercept;
    private final Set<SimpleRegressor> _curveRegs;

    public ItemModelTest()
    {
        try (final InputStream iStream = ItemModelTest.class.getResourceAsStream("/raw_data.dat");
             final ObjectInputStream oIn = new ObjectInputStream(iStream))
        {
            _rawData = (ItemFittingGrid<SimpleStatus, SimpleRegressor>) oIn.readObject();

            _intercept = _rawData.getRegressorFamily().getFromName("INTERCEPT");

            final Set<String> regNames = new TreeSet<>(Arrays.asList("FICO",
                    "INCENTIVE", "AGE"));

            _curveRegs = regNames.stream().map((x) -> _rawData.getRegressorFamily().getFromName(x))
                    .collect(Collectors.toSet());
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (final ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }


    @BeforeEach
    void setUp()
    {

    }


    private ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> makeFitter()
    {
        ItemSettings settings = ItemSettings.newBuilder()
                .setRand(RandomTool.getRandom(PrngType.SECURE, 0xcafebabe)).build();

        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                new ItemFitter<>(new StandardCurveFactory<>(),
                        _intercept,
                        _rawData.getFromStatus(), _rawData, settings);

        return fitter;
    }


    @Test
    void vacuous() throws Exception
    {
        assertTrue(_rawData.size() > 0);

        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter();

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .fitCoefficients();

        System.out.println(fitter.getChain());
        System.out.println("Fit Vacuous: " + result.getEndingParams());
        Assertions.assertTrue(result.getEndingLL() < 0.2023);

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result2 =
                fitter.expandModel(Collections.singleton(_rawData.getRegressorFamily().getFromName("FICO")), 5);

        System.out.println(fitter.getChain());
        System.out.println("Next param: " + result2.getEndingParams());
        Assertions.assertTrue(result2.getEndingLL() < 0.2025);

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 =
                fitter.expandModel(_curveRegs, 50);

        System.out.println(fitter.getChain());
        System.out.println("Next param: " + r3.getEndingParams());
        Assertions.assertTrue(r3.getEndingLL() < 0.1889);
    }

    @Test
    void computeGradient() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter();

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .fitCoefficients();
        Assertions.assertTrue(result.getEndingLL() < 0.2024);

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 =
                fitter.expandModel(_curveRegs, 50);

        System.out.println(fitter.getChain());
        System.out.println("Next param: " + r3.getEndingParams());
        Assertions.assertTrue(r3.getEndingLL() < 0.195);

        ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> params = r3.getEndingParams();
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

        for (int k = 0; k < Math.min(1000, paramGrid.size()); k++)
        {
            orig.computeGradient(paramGrid, origPacked, k, gradient, secondDerivative);

            double minCos = Double.MAX_VALUE;

            // Verify that the second derivative is symmetric.
            for (int w = 0; w < paramCount; w++)
            {
                // Extract column w.
                for (int z = 0; z < paramCount; z++)
                {
                    workspace[z] = secondDerivative[z][w];
                }

                // Compare to row w.
                final double crossCos = MathTools.cos(workspace, secondDerivative[w]);
                System.out.println("CrossCos[" + k + "][" + w + "]: " + crossCos);

                if (!(crossCos > 0.99))
                {
                    System.out.println("BOing");
                }

                minCos = Math.min(minCos, crossCos);
            }

            Assertions.assertTrue(minCos > 0.99);

            for (int i = 0; i < paramCount; i++)
            {
                final double[] testBeta = beta.clone();
                final double h = Math.abs(testBeta[i] * 0.00001) + 1.0e-8;
                testBeta[i] = testBeta[i] + h;
                repacked.updatePacked(testBeta);

                ItemModel<SimpleStatus, SimpleRegressor, StandardCurveType> adjusted = new ItemModel<>(
                        repacked.generateParams());

                final double origLL = orig.logLikelihood(paramGrid, k);
                final double shiftLL = adjusted.logLikelihood(paramGrid, k);

                final double fdd = (shiftLL - origLL) / h;

                fdGradient[i] = fdd;

                // Now same for gradient.
                adjusted.computeGradient(paramGrid, repacked, k, fdSecondDerivative[i], null);

                for (int z = 0; z < paramCount; z++)
                {
                    fdSecondDerivative[i][z] = (fdSecondDerivative[i][z] - gradient[z]) / h;
                }
            }

            final double cos = MathTools.cos(gradient, fdGradient);

            System.out.println("Cos[" + k + "]: " + cos);

            if (!(cos > 0.99))
            {
                System.out.println("Blah!");
                final double origLL = orig.logLikelihood(paramGrid, k);
                //final double shiftLL = adjusted.logLikelihood(paramGrid, k);
            }

            Assertions.assertTrue(cos > 0.99);

            // Now validate cosine similarity of the rows of the second derivative.
            for (int z = 0; z < paramCount; z++)
            {
                final double cos2 = MathTools.cos(fdSecondDerivative[z], secondDerivative[z]);
                System.out.println("cos2[" + k + "][" + z + "]: " + cos2);
                Assertions.assertTrue(cos2 > 0.99);
            }
        }


    }

    @Test
    void transitionProbability()
    {
    }
}