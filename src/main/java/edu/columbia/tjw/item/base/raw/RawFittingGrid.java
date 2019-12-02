package edu.columbia.tjw.item.base.raw;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.data.RandomizedStatusGrid;

public class RawFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>>
        extends RawItemGrid<R> implements ItemFittingGrid<S, R>
{
    private final S _fromStatus;
    private final int[] _nextStatus;

    public RawFittingGrid(ItemFittingGrid<S, R> underlying_)
    {
        super(underlying_);

        _fromStatus = underlying_.getFromStatus();
        final int size = underlying_.size();
        _nextStatus = new int[size];

        for (int i = 0; i < size; i++)
        {
            _nextStatus[i] = underlying_.getNextStatus(i);
        }
    }

    public static <S extends ItemStatus<S>, R extends ItemRegressor<R>>
    ItemFittingGrid<S, R> fromStatusGrid(final ItemStatusGrid<S, R> grid_, final S status_)
    {
        final ItemSettings noShuffleSettings = (new ItemSettings()).makeBuilder().setRandomShuffle(false).build();
        return fromStatusGrid(grid_, noShuffleSettings, status_);
    }

    public static <S extends ItemStatus<S>, R extends ItemRegressor<R>>
    ItemFittingGrid<S, R> fromStatusGrid(final ItemStatusGrid<S, R> grid_, final ItemSettings settings_, final S status_)
    {
        final ItemFittingGrid<S, R> wrapped = new RandomizedStatusGrid<>(grid_, settings_,
                grid_.getRegressorFamily(), status_);
        return new RawFittingGrid<>(wrapped);
    }

    @Override
    public S getFromStatus()
    {
        return _fromStatus;
    }

    @Override
    public int getNextStatus(int index_)
    {
        return _nextStatus[index_];
    }
}
