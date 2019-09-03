package edu.columbia.tjw.item.data;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.EnumFamily;

import java.util.Set;

public final class RawFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>> implements ItemFittingGrid<S, R>
{
    private final RandomizedStatusGrid<S, R> _randGrid;

    public RawFittingGrid(final RandomizedStatusGrid<S, R> randGrid_)
    {
        if (null == randGrid_)
        {
            throw new NullPointerException("Rand grid cannot be null.");
        }

        _randGrid = randGrid_;
    }


    @Override
    public S getFromStatus()
    {
        return _randGrid.getFromStatus();
    }

    @Override
    public int getNextStatus(int index_)
    {
        return _randGrid.getNextStatus(index_);
    }

    @Override
    public Set<R> getAvailableRegressors()
    {
        return _randGrid.getAvailableRegressors();
    }

    @Override
    public ItemRegressorReader getRegressorReader(R field_)
    {
        return _randGrid.getRegressorReader(field_);
    }

    @Override
    public int size()
    {
        return _randGrid.size();
    }

    @Override
    public EnumFamily<R> getRegressorFamily()
    {
        return _randGrid.getRegressorFamily();
    }
}
