package edu.columbia.tjw.item.base;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.data.ItemGrid;
import edu.columbia.tjw.item.util.EnumFamily;

import java.io.Serializable;
import java.util.Set;

/**
 * A serializable version of the ItemGrid. Allows for data to be coveniently cached and moved around.
 * <p>
 * Converts doubles to floats, so some loss of precision occurs, but reduces needed space by 50%.
 *
 * @param <R>
 */
public class RawItemGrid<R extends ItemRegressor<R>> implements ItemGrid<R>, Serializable
{
    private final Set<R> _availableRegressors;
    private final int _size;
    private final EnumFamily<R> _regressorFamily;
    private final ItemRegressorReader[] _readers;

    public RawItemGrid(ItemGrid<R> underlying_)
    {
        _availableRegressors = underlying_.getAvailableRegressors();
        _size = underlying_.size();
        _regressorFamily = underlying_.getRegressorFamily();
        _readers = new ItemRegressorReader[_regressorFamily.size()];

        for (final R next : _availableRegressors)
        {
            _readers[next.ordinal()] = new RawRegressorReader(underlying_.getRegressorReader(next));
        }
    }


    @Override
    public Set<R> getAvailableRegressors()
    {
        return _availableRegressors;
    }

    @Override
    public ItemRegressorReader getRegressorReader(R field_)
    {
        return _readers[field_.ordinal()];
    }

    @Override
    public int size()
    {
        return _size;
    }

    @Override
    public EnumFamily<R> getRegressorFamily()
    {
        return _regressorFamily;
    }

    private final class RawRegressorReader implements ItemRegressorReader, Serializable
    {
        private final float[] _data;

        public RawRegressorReader(ItemRegressorReader underlying_)
        {
            _data = new float[this.size()];

            if (this.size() != underlying_.size())
            {
                throw new IllegalArgumentException("Size mismatch.");
            }

            for (int i = 0; i < this.size(); i++)
            {
                _data[i] = (float) underlying_.asDouble(i);
            }
        }


        @Override
        public double asDouble(int index_)
        {
            return _data[index_];
        }

        @Override
        public int size()
        {
            return _size;
        }
    }
}
