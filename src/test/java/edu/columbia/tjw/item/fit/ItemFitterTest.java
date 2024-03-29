package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveFactory;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.base.raw.RawFittingGrid;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.optimize.OptimizationTarget;
import edu.columbia.tjw.item.util.random.PrngType;
import edu.columbia.tjw.item.util.random.RandomTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ItemFitterTest
{
    private static final double SQRT_EPSILON = Math.sqrt(Math.ulp(4.0));

    public ItemFitterTest()
    {
    }

    @Test
    void basicTest() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false, OptimizationTarget.ENTROPY);
        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .getChain().getLatestResults();
        Assertions.assertEquals(0.20231121363023669, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 2, false);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.19059332135780832, r3.getEntropy());

//
//        final File outputDir = new File("/Users/tyler/Documents/code/outputModels");
//        final File dataFile = new File(outputDir, "test_model_small.dat");
//        result.getParams().writeToStream(new FileOutputStream(dataFile));
//
//        final File dataFileMed = new File(outputDir, "test_model_medium.dat");
//        r3.getParams().writeToStream(new FileOutputStream(dataFileMed));
//
//        System.out.println("Done!");
        System.out.println("Done: " + result.getEntropy() + " -> " + r3.getEntropy());
    }

    @Test
    void basicTicTest() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false, OptimizationTarget.ICE_RAW);
        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .getChain().getLatestResults();
        Assertions.assertEquals(0.20231144233933415, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 2, false);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.1903592631042488, r3.getEntropy());
        System.out.println("Done: " + result.getEntropy() + " -> " + r3.getEntropy());
    }

    @Test
    void basicIceTest() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false, OptimizationTarget.ICE_SIMPLE);
        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .getChain().getLatestResults();
        Assertions.assertEquals(0.2023112138024271, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 2, false);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.19224638612675196, r3.getEntropy());
        System.out.println("Done: " + result.getEntropy() + " -> " + r3.getEntropy());
    }

    @Test
    void basicIce2Test() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false, OptimizationTarget.ICE2);
        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .getChain().getLatestResults();
        Assertions.assertEquals(0.20231121380243222, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 2, false);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.1922463829188469, r3.getEntropy());
        System.out.println("Done: " + result.getEntropy() + " -> " + r3.getEntropy());
    }

    @Test
    void basicIce4Test() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false, OptimizationTarget.ICE);
        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .getChain().getLatestResults();
        Assertions.assertEquals(0.20231144463992923, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 2, false);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.1905569130961492, r3.getEntropy());
        System.out.println("Done: " + result.getEntropy() + " -> " + r3.getEntropy());
    }

    @Test
    void basicIce5Test() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false, OptimizationTarget.ICE_B);
        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .getChain().getLatestResults();
        Assertions.assertEquals(0.2023112137295798, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 2, false);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.19020913782841956, r3.getEntropy());
        System.out.println("Done: " + result.getEntropy() + " -> " + r3.getEntropy());
    }

    @Test
    void mediumTest() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false, OptimizationTarget.ENTROPY);

        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .getChain().getLatestResults();
        Assertions.assertEquals(0.20231121363023669, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 20, false);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.18789106738395822, r3.getEntropy());


        //fitter.getCalculator().computeFitResult(r3.getParams(), r3);


        System.out.println("Done: " + result.getEntropy() + " -> " + r3.getEntropy());
    }

//    @Test
//    void mediumOutOfSample() throws Exception
//    {
//        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
//                makeFitter(false);
//
//        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());
//
//        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
//                .fitCoefficients();
//        Assertions.assertEquals(0.2023112681060799, result.getEntropy());
//
//        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
//                curveRegs, 20, false);
//
//        System.out.println("Revised: " + r3.getParams());
//        Assertions.assertEquals(0.18960145080215177, r3.getEntropy());
//
//        final ItemFittingGrid<SimpleStatus, SimpleRegressor> largeGrid = loadData(true);
//
//        final EntropyCalculator<SimpleStatus, SimpleRegressor, StandardCurveType> calc = new EntropyCalculator<>(
//                largeGrid);
//
//        final FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> oos = calc.computeFitResult(r3.getParams(),
//                r3);
//
//        System.out.println("Out of sample entropy: " + oos.getEntropy());
//        Assertions.assertEquals(0.18960145080215177, oos.getEntropy());
//
////        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3a = fitter.trim(true);
////        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> refit = fitter.fitAllParameters();
//
//        fitter.getCalculator().computeFitResult(r3.getParams(), r3);
//
//
//        System.out.println("Done!");
//    }


//    @Test
//    void largeTest() throws Exception
//    {
//        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
//                makeFitter(true);
//        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());
//
//        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
//                .fitCoefficients();
//        Assertions.assertEquals(0.20432772784896, result.getEntropy());
//
//        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 =
//                fitter.expandModel(curveRegs, 20);
//
//        System.out.println("Revised: " + r3.getParams());
//        Assertions.assertEquals(0.18996153070429303, r3.getEntropy());
//    }

    private Set<SimpleRegressor> getCurveRegs(final ItemFittingGrid<SimpleStatus, SimpleRegressor> rawData)
    {
        final Set<String> regNames = new TreeSet<>(Arrays.asList("FICO",
                "INCENTIVE", "AGE"));

        return regNames.stream().map((x) -> rawData.getRegressorFamily().getFromName(x))
                .collect(Collectors.toSet());
    }

    private ItemFittingGrid<SimpleStatus, SimpleRegressor> loadData(final boolean large_)
    {
        final String fileName;

        if (large_)
        {
            fileName = "/raw_data_large.dat";
        }
        else
        {
            fileName = "/raw_data.dat";
        }
        return loadData(fileName);
    }


    private ItemFittingGrid<SimpleStatus, SimpleRegressor> loadData(final String fileName_)
    {
        try (final InputStream iStream = this.getClass().getResourceAsStream(fileName_))
        {
            final ItemFittingGrid<SimpleStatus, SimpleRegressor> rawData = RawFittingGrid.readFromStream(iStream,
                    SimpleStatus.class, SimpleRegressor.class);
            return rawData;
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> loadParams(final String fileName_)
    {
        try (final InputStream iStream = this.getClass().getResourceAsStream(fileName_))
        {
            final ItemParameters<SimpleStatus, SimpleRegressor, StandardCurveType> rawData = ItemParameters
                    .readFromStream(iStream,
                            SimpleStatus.class, SimpleRegressor.class, StandardCurveType.class);
            return rawData;
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> makeFitter(final boolean large_,
                                                                                    final OptimizationTarget target_)
    {
        final ItemFittingGrid<SimpleStatus, SimpleRegressor> rawData = loadData(large_);
        final SimpleRegressor intercept = rawData.getRegressorFamily().getFromName("INTERCEPT");

        ItemSettings settings = ItemSettings.newBuilder()
                .setRand(RandomTool.getRandomGenerator(PrngType.SECURE, 0xcafebabe))
                .setTarget(target_).build();


        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                new ItemFitter<>(new StandardCurveFactory<>(),
                        intercept.getFamily(), rawData, settings);

        return fitter;
    }

}
