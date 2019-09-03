package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.util.EnumFamily;

import java.util.Set;

public class FittingGridShard<S extends ItemStatus<S>, R extends ItemRegressor<R>> implements ItemFittingGrid<S, R>
{
    private final ItemFittingGrid<S, R> _underlying;
    private final int _start;
    private final int _size;
    private final ItemRegressorReader[] _readers;


    public FittingGridShard(final ItemFittingGrid<S, R> underlying_, final int startRow_, final int size_)
    {
        if (null == underlying_)
        {
            throw new NullPointerException("Underlying cannot be null.");
        }
        if (size_ < 0 || startRow_ < 0)
        {
            throw new IllegalArgumentException("Invalid row parameters.");
        }

        _underlying = underlying_;
        _start = startRow_;

        final long startLong = startRow_;
        final long sizeLong = size_;
        final long endLong = startRow_ + size_;

        if (endLong > _underlying.size())
        {
            throw new IllegalArgumentException("Size overflows underlying.");
        }
        if (endLong > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException("Integer overflow.");
        }

        _size = size_;
        _readers = new ItemRegressorReader[getRegressorFamily().size()];
    }

    private int transform(final int index_)
    {
        if (index_ < 0 || index_ >= _size)
        {
            throw new ArrayIndexOutOfBoundsException("Out of bounds.");
        }

        return index_ + _start;
    }


    @Override
    public S getFromStatus()
    {
        return _underlying.getFromStatus();
    }

    @Override
    public int getNextStatus(int index_)
    {
        final int mapped = transform(index_);
        return _underlying.getNextStatus(mapped);
    }

    @Override
    public Set<R> getAvailableRegressors()
    {
        return _underlying.getAvailableRegressors();
    }

    @Override
    public ItemRegressorReader getRegressorReader(R field_)
    {
        final ItemRegressorReader reader = _readers[field_.ordinal()];

        if (null != reader)
        {
            return reader;
        }

        final ItemRegressorReader raw = _underlying.getRegressorReader(field_);
        final ItemRegressorReader sharded = new ShardedReader(raw);
        _readers[field_.ordinal()] = sharded;
        return sharded;
    }

    @Override
    public int size()
    {
        return _size;
    }

    @Override
    public EnumFamily<R> getRegressorFamily()
    {
        return _underlying.getRegressorFamily();
    }

    private final class ShardedReader implements ItemRegressorReader
    {
        private final ItemRegressorReader _reader;

        private ShardedReader(final ItemRegressorReader reader_)
        {
            if (null == reader_)
            {
                throw new NullPointerException("Reader cannot be null.");
            }

            _reader = reader_;
        }

        @Override
        public double asDouble(int index_)
        {
            final int mapped = transform(index_);
            return _reader.asDouble(mapped);
        }

        @Override
        public int size()
        {
            return _size;
        }
    }
}
