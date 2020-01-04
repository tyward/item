package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveFactory;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.base.raw.RawFittingGrid;
import edu.columbia.tjw.item.data.ItemFittingGrid;
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
    public ItemFitterTest()
    {
    }

    @Test
    void basicTest() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false);
        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .fitCoefficients();
        Assertions.assertEquals(0.2023112681060799, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.expandModel(curveRegs, 2);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.19025716309892124, r3.getEntropy());


//        final File outputDir = new File("/Users/tyler/Documents/code/outputModels");
//        final File dataFile = new File(outputDir, "test_model_small.dat");
//        result.getParams().writeToStream(new FileOutputStream(dataFile));
//
//        final File dataFileMed = new File(outputDir, "test_model_medium.dat");
//        r3.getParams().writeToStream(new FileOutputStream(dataFileMed));
//
//        System.out.println("Done!");
    }

    @Test
    void mediumTest() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter(false);


        final Set<SimpleRegressor> curveRegs = getCurveRegs(fitter.getGrid());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .fitCoefficients();
        Assertions.assertEquals(0.2023112681060799, result.getEntropy());

        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.fitModel(Collections.emptySet(),
                curveRegs, 20, false);
//        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 =
//                fitter.expandModel(curveRegs, 20);

        System.out.println("Revised: " + r3.getParams());
        Assertions.assertEquals(0.18960145080215177, r3.getEntropy());
//        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3a = fitter.trim(true);
//        FitResult<SimpleStatus, SimpleRegressor, StandardCurveType> refit = fitter.fitAllParameters();

        fitter.getCalculator().computeFitResult(r3.getParams(), r3);


        System.out.println("Done!");
    }

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

    private ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> makeFitter(final boolean large_)
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

        try (final InputStream iStream = this.getClass().getResourceAsStream(fileName))
        {
            final ItemFittingGrid<SimpleStatus, SimpleRegressor> rawData = RawFittingGrid.readFromStream(iStream,
                    SimpleStatus.class, SimpleRegressor.class);
            final SimpleRegressor intercept = rawData.getRegressorFamily().getFromName("INTERCEPT");

            ItemSettings settings = ItemSettings.newBuilder()
                    .setRand(RandomTool.getRandom(PrngType.SECURE, 0xcafebabe)).build();

            final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                    new ItemFitter<>(new StandardCurveFactory<>(),
                            intercept,
                            rawData.getFromStatus(), rawData, settings);

            return fitter;
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }

}
