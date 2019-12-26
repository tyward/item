package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.StandardCurveFactory;
import edu.columbia.tjw.item.base.StandardCurveType;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.param.ParamFitResult;
import edu.columbia.tjw.item.util.random.PrngType;
import edu.columbia.tjw.item.util.random.RandomTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ItemFitterTest
{
    private final ItemFittingGrid<SimpleStatus, SimpleRegressor> _rawData;
    private final SimpleRegressor _intercept;
    private final Set<SimpleRegressor> _curveRegs;

    public ItemFitterTest()
    {
        try (final InputStream iStream = this.getClass().getResourceAsStream("/raw_data.dat");
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

    @Test
    void basicTest() throws Exception
    {
        final ItemFitter<SimpleStatus, SimpleRegressor, StandardCurveType> fitter =
                makeFitter();

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> result = fitter
                .fitCoefficients();
        Assertions.assertEquals(result.getEndingLL(), 0.20231126613149455);

        ParamFitResult<SimpleStatus, SimpleRegressor, StandardCurveType> r3 = fitter.expandModel(_curveRegs, 2);

        System.out.println("Revised: " + r3.getEndingParams());
        Assertions.assertEquals(r3.getEndingLL(), 0.1943684028229462);
    }

//    @Test
//    void simpleFitTest() throws Exception
//    {
//
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

}
