package edu.columbia.tjw.item.base.wrapped;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.data.ItemGrid;
import edu.columbia.tjw.item.util.EnumFamily;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class WrappedItemGrid implements ItemGrid<SimpleRegressor>, Serializable
{
    private final SortedSet<SimpleRegressor> _available;
    private final EnumFamily<SimpleRegressor> _enumFamily;
    private final ItemRegressorReader[] _readers;
    private final int _size;

    private WrappedItemGrid(final SortedSet<SimpleRegressor> available_, final int size_,
                            final EnumFamily<SimpleRegressor> enumFamily_, final ItemRegressorReader[] readers_)
    {
        _available = Collections.unmodifiableSortedSet(available_);
        _size = size_;
        _enumFamily = enumFamily_;
        _readers = readers_;
    }

    public static <V extends ItemRegressor<V>> WrappedItemGrid wrapGrid(final ItemGrid<V> grid_, final SortedSet<V> restricted_)
    {
        final int size = grid_.size();
        final EnumFamily<SimpleRegressor> family = SimpleRegressor.generateFamily(grid_.getRegressorFamily());
        final ItemRegressorReader[] readers = new ItemRegressorReader[family.size()];
        final SortedSet<SimpleRegressor> available = new TreeSet<>();

        for (final V next : restricted_)
        {
            if (!grid_.getAvailableRegressors().contains(next))
            {
                throw new IllegalArgumentException("Regressor not available.");
            }


            final String nextName = next.name();
            final SimpleRegressor nextConverted = family.getFromName(nextName);

            if (null == nextConverted)
            {
                throw new IllegalArgumentException("Name mismatch.");
            }

            available.add(nextConverted);
            readers[nextConverted.ordinal()] = grid_.getRegressorReader(next);
        }

        return new WrappedItemGrid(available, size, family, readers);
    }

    @Override
    public Set<SimpleRegressor> getAvailableRegressors()
    {
        return _available;
    }

    @Override
    public ItemRegressorReader getRegressorReader(SimpleRegressor field_)
    {
        if (!_enumFamily.getMembers().contains(field_))
        {
            throw new IllegalArgumentException("Enum mismatch!");
        }

        return _readers[field_.ordinal()];
    }

    @Override
    public int size()
    {
        return _size;
    }

    @Override
    public EnumFamily<SimpleRegressor> getRegressorFamily()
    {
        return _enumFamily;
    }
}
