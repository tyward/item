package edu.columbia.tjw.item.base.wrapped;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.util.EnumFamily;

import java.util.Set;
import java.util.SortedSet;

public final class WrappedStatusGrid
        implements ItemStatusGrid<SimpleStatus, SimpleRegressor>
{
    private final WrappedItemGrid _underlyingWrapped;
    private final ItemStatusGrid<?, ?> _underlying;
    private final EnumFamily<SimpleStatus> _family;

    private WrappedStatusGrid(ItemStatusGrid<?, ?> grid_, WrappedItemGrid wrapped_,
                              final EnumFamily<SimpleStatus> family_)
    {
        _underlying = grid_;
        _underlyingWrapped = wrapped_;
        _family = family_;
    }

    public static <S extends ItemStatus<S>, R extends ItemRegressor<R>>
    WrappedStatusGrid wrapGrid(final ItemStatusGrid<S, R> grid_, final SortedSet<R> restricted_,
                               SortedSet<S> statusRestrict_)
    {
        WrappedItemGrid wrapped = WrappedItemGrid.wrapGrid(grid_, restricted_);
        EnumFamily<SimpleStatus> family = SimpleStatus.generateFamily(grid_.getStatusFamily(), statusRestrict_);

        return new WrappedStatusGrid(grid_, wrapped, family);
    }


    @Override
    public EnumFamily<SimpleStatus> getStatusFamily()
    {
        return _family;
    }

    @Override
    public int getStatus(int index_)
    {
        return _underlying.getStatus(index_);
    }

    @Override
    public int getNextStatus(int index_)
    {
        return _underlying.getNextStatus(index_);
    }

    @Override
    public boolean hasNextStatus(int index_)
    {
        return _underlying.hasNextStatus(index_);
    }

    @Override
    public ItemRegressorReader getRegressorReader(SimpleRegressor field_)
    {
        return _underlyingWrapped.getRegressorReader(field_);
    }

    @Override
    public Set<SimpleRegressor> getAvailableRegressors()
    {
        return _underlyingWrapped.getAvailableRegressors();
    }

    @Override
    public int size()
    {
        return _underlying.size();
    }

    @Override
    public EnumFamily<SimpleRegressor> getRegressorFamily()
    {
        return _underlyingWrapped.getRegressorFamily();
    }
}
