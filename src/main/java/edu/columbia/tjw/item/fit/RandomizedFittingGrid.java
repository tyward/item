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
package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.random.RandomTool;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 */
public class RandomizedFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>> implements ItemFittingGrid<S, R>
{
    private final int[] _indexMap;

    private final ItemFittingGrid<S, R> _underlying;
    private final ItemRegressorReader[] _readers;

    public RandomizedFittingGrid(final ItemParameters<S, R, ? extends ItemCurveType<?>> params_, final ItemFittingGrid<S, R> underlying_, final ItemSettings settings_)
    {
        _underlying = underlying_;

        _indexMap = new int[_underlying.totalSize()];

        for (int i = 0; i < _indexMap.length; i++)
        {
            _indexMap[i] = i;
        }

        RandomTool.shuffle(_indexMap, settings_.getRandom());

        _readers = new ItemRegressorReader[params_.getStatus().getFamily().size()];
    }

    @Override
    public ItemRegressorReader getRegressorReader(R field_)
    {
        final int ordinal = field_.ordinal();

        if (null != _readers[ordinal])
        {
            return _readers[ordinal];
        }

        _readers[ordinal] = new MappedReader(_underlying.getRegressorReader(field_));
        return _readers[ordinal];
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
    public void getRegressors(int index_, double[] output_)
    {
        final int mapped = mapIndex(index_);
        _underlying.getRegressors(mapped, output_);
    }

    @Override
    public int totalSize()
    {
        return _underlying.totalSize();
    }

    @Override
    public R getField(int fieldIndex_)
    {
        return _underlying.getField(fieldIndex_);
    }

    @Override
    public ItemCurve<?> getTransformation(int fieldIndex_)
    {
        return _underlying.getTransformation(fieldIndex_);
    }

    private int mapIndex(final int index_)
    {
        return _indexMap[index_];
    }

    private final class MappedReader implements ItemRegressorReader
    {
        private final ItemRegressorReader _underlying;

        public MappedReader(final ItemRegressorReader underlying_)
        {
            _underlying = underlying_;
        }

        @Override
        public double asDouble(int index_)
        {
            final int mapped = mapIndex(index_);
            return _underlying.asDouble(mapped);
        }

    }

}
