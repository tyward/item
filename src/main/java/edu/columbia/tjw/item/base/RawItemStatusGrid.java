package edu.columbia.tjw.item.base;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.util.EnumFamily;

public final class RawItemStatusGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>> extends RawItemGrid<R>
        implements ItemStatusGrid<S, R>
{
    private final EnumFamily<S> _statusFamily;
    private final int[] _status;
    private final int[] _nextStatus;

    public RawItemStatusGrid(ItemStatusGrid<S, R> underlying_)
    {
        super(underlying_);

        _statusFamily = underlying_.getStatusFamily();

        final int size = underlying_.size();
        _status = new int[size];
        _nextStatus = new int[size];

        for (int i = 0; i < size; i++)
        {
            _status[i] = underlying_.getStatus(i);

            if (underlying_.hasNextStatus(i))
            {
                _nextStatus[i] = underlying_.getNextStatus(i);
            }
            else
            {
                // Guaranteed to not be used for an actual status.
                _nextStatus[i] = -1;
            }
        }
    }

    @Override
    public EnumFamily<S> getStatusFamily()
    {
        return _statusFamily;
    }

    @Override
    public int getStatus(int index_)
    {
        return _status[index_];
    }

    @Override
    public int getNextStatus(int index_)
    {
        if (!hasNextStatus(index_))
        {
            throw new IllegalArgumentException("Next status unavailable.");
        }

        return _nextStatus[index_];
    }

    @Override
    public boolean hasNextStatus(int index_)
    {
        return (_nextStatus[index_] >= 0);
    }
}
