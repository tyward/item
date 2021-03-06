/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 *
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.item.data;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.random.RandomTool;

import java.util.Arrays;
import java.util.Set;

/**
 * @param <S> The status type for this grid
 * @param <R> The regressor type for this grid
 * @author tyler
 */
public class RandomizedStatusGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>>
        implements ItemStatusGrid<S, R>, ItemFittingGrid<S, R>
{
    private final ItemStatusGrid<S, R> _underlying;
    private final int[] _mapping;
    private final ItemRegressorReader[] _readers;
    private final float[][] _regressors;
    private final EnumFamily<R> _regFamily;
    private final S _fromStatus;

    public RandomizedStatusGrid(final ItemStatusGrid<S, R> underlying_, final ItemSettings settings_,
                                final EnumFamily<R> regFamily_, final S fromStatus_)
    {
        synchronized (this)
        {
            _fromStatus = fromStatus_;
            _underlying = underlying_;
            _regFamily = regFamily_;

            final int fromOrdinal = fromStatus_.ordinal();
            final int size = underlying_.size();
            final int[] rawMapping = new int[size];
            int count = 0;

            final boolean[] reachableMask = new boolean[fromStatus_.getFamily().size()];

            for (S next : fromStatus_.getReachable())
            {
                reachableMask[next.ordinal()] = true;
            }


            for (int i = 0; i < size; i++)
            {
                final int statOrdinal = underlying_.getStatus(i);

                if (statOrdinal != fromOrdinal)
                {
                    // Wrong starting status.
                    continue;
                }
                if (!underlying_.hasNextStatus(i))
                {
                    // Next status not available.
                    continue;
                }
                if (!reachableMask[underlying_.getNextStatus(i)])
                {
                    // This is plainly invalid, it shows a status that is impossible according to our status family.
                    continue;
                }

                rawMapping[count++] = i;
            }

            _mapping = Arrays.copyOf(rawMapping, count);

            if (settings_.isRandomShuffle())
            {
                RandomTool.shuffle(_mapping, settings_.getRandom());
            }

            final int familySize = regFamily_.size();
            _regressors = new float[familySize][];
            _readers = new ItemRegressorReader[familySize];
        }
    }

    public S getFromStatus()
    {
        return _fromStatus;
    }

    private int mapIndex(final int index_)
    {
        return _mapping[index_];
    }

    @Override
    public EnumFamily<S> getStatusFamily()
    {
        return _underlying.getStatusFamily();
    }

    @Override
    public int getStatus(int index_)
    {
        final int mapped = mapIndex(index_);
        return _underlying.getStatus(mapped);
    }

    @Override
    public int getNextStatus(int index_)
    {
        final int mapped = mapIndex(index_);
        return _underlying.getNextStatus(mapped);

    }

    @Override
    public boolean hasNextStatus(int index_)
    {
        final int mapped = mapIndex(index_);
        return _underlying.hasNextStatus(mapped);

    }

    @Override
    public ItemRegressorReader getRegressorReader(R field_)
    {
        final int ordinal = field_.ordinal();

        if (null != _readers[ordinal])
        {
            return _readers[ordinal];
        }

        _readers[ordinal] = new MappedReader(field_);
        return _readers[ordinal];
    }

    @Override
    public int size()
    {
        return _mapping.length;
    }

    @Override
    public EnumFamily<R> getRegressorFamily()
    {
        return _underlying.getRegressorFamily();
    }


    @Override
    public final Set<R> getAvailableRegressors()
    {
        return _underlying.getAvailableRegressors();
    }

    private synchronized float[] fetchRegressor(final int ordinal_)
    {
        final float[] cached = _regressors[ordinal_];

        if (null != cached)
        {
            return cached;
        }

        final int tabSize = this.size();
        final R reg = _regFamily.getFromOrdinal(ordinal_);
        final ItemRegressorReader reader = _underlying.getRegressorReader(reg);
        _regressors[ordinal_] = new float[tabSize];

        for (int i = 0; i < tabSize; i++)
        {
            final int mapped = mapIndex(i);
            _regressors[ordinal_][i] = (float) reader.asDouble(mapped);
        }

        return _regressors[ordinal_];
    }

    public final class MappedReader implements ItemRegressorReader
    {
        private final float[] _data;

        public MappedReader(final R field_)
        {
            _data = fetchRegressor(field_.ordinal());
        }

        @Override
        public double asDouble(int index_)
        {
            return _data[index_];
        }

        /**
         * N.B: This is extremely dangerous, to give out our underlying array.
         * <p>
         * However, it is very useful for performance reasons, so it is allowed,
         * but hidden.
         *
         * @return
         */
        public float[] getUnderlyingArray()
        {
            return _data;
        }

        @Override
        public int size()
        {
            return _data.length;
        }

    }

}
