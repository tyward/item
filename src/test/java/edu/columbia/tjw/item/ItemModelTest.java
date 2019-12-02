package edu.columbia.tjw.item;

import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

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
    void vacuous()
    {
        assertTrue(rawData.size() > 0);
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