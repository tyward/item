package edu.columbia.tjw.item;

import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveFactory;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.ItemFitter;
import edu.columbia.tjw.item.fit.param.ParamFitResult;
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
    ItemFittingGrid<SimpleStatus, SimpleRegressor> rawData;

    @BeforeEach
    void setUp()
    {
        try (final InputStream iStream = ItemModelTest.class.getResourceAsStream("/raw_data.dat");
             final ObjectInputStream oIn = new ObjectInputStream(iStream))
        {
            rawData = (ItemFittingGrid<SimpleStatus, SimpleRegressor>) oIn.readObject();
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

    @Test
    void vacuous() throws Exception
    {
        assertTrue(rawData.size() > 0);

        ItemSettings settings = ItemSettings.newBuilder()
                .setRand(RandomTool.getRandom(PrngType.SECURE, 0xcafebabe)).build();

        SimpleRegressor intercept = rawData.getRegressorFamily().getFromName("INTERCEPT");

        Set<String> regNames = new TreeSet<>(Arrays.asList("FICO",
                "INCENTIVE", "AGE"));


        Set<SimpleRegressor> curveRegs = regNames.stream().map((x) -> rawData.getRegressorFamily().getFromName(x))
                .collect(Collectors.toSet());


        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                new ItemFitter<>(new StandardCurveFactory<>(),
                        rawData.getRegressorFamily().getFromName("INTERCEPT"),
                        rawData.getFromStatus(), rawData, settings);

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .fitCoefficients();

        System.out.println(fitter.getChain());
        System.out.println("Fit Vacuous: " + result.getEndingParams());
        Assertions.assertTrue(result.getEndingLL() < 0.2023);

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result2 =
                fitter.expandModel(Collections.singleton(rawData.getRegressorFamily().getFromName("FICO")), 5);

        System.out.println(fitter.getChain());
        System.out.println("Next param: " + result2.getEndingParams());
        Assertions.assertTrue(result2.getEndingLL() < 0.2025);

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 =
                fitter.expandModel(curveRegs, 50);

        System.out.println(fitter.getChain());
        System.out.println("Next param: " + r3.getEndingParams());
        Assertions.assertTrue(r3.getEndingLL() < 0.1889);
    }

    @Test
    void computeGradient()
    {
    }

    @Test
    void transitionProbability()
    {
    }
}