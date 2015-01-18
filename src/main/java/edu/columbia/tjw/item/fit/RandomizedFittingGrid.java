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
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 */
public class RandomizedFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>> implements ItemFittingGrid<S, R>
{
    private final int[] _indexMap;

    private final ItemParameters<S, R, ? extends ItemCurveType<?>> _params;
    private final ItemFittingGrid<S, R> _underlying;
    private final ItemRegressorReader[] _readers;

    private final short[] _status;
    private final short[] _nextStatus;

    private final short[] _regOrdinals;
    private final ItemCurve[] _curves;

    private final float[][] _regressors;

    public RandomizedFittingGrid(final ItemParameters<S, R, ? extends ItemCurveType<?>> params_, final ItemFittingGrid<S, R> underlying_, final ItemSettings settings_)
    {
        _params = params_;
        _underlying = underlying_;

        final int size = _underlying.totalSize();

        _indexMap = new int[_underlying.totalSize()];

        for (int i = 0; i < _indexMap.length; i++)
        {
            _indexMap[i] = i;
        }

        if (settings_.isRandomShuffle())
        {
            RandomTool.shuffle(_indexMap, settings_.getRandom());
        }

        final List<R> regList = params_.getRegressorList();
        final SortedSet<R> regSet = new TreeSet<>(regList);
        final int regCount = regSet.first().getFamily().size();

        _readers = new ItemRegressorReader[regCount];
        _regressors = new float[regCount][];

        for (final R next : regSet)
        {
            final int ordinal = next.ordinal();
            _regressors[ordinal] = new float[size];

            final ItemRegressorReader reader = _underlying.getRegressorReader(next);

            for (int i = 0; i < size; i++)
            {
                final int mapped = mapIndex(i);
                _regressors[ordinal][i] = (float) reader.asDouble(mapped);
            }
        }

        _status = new short[size];
        _nextStatus = new short[size];

        for (int i = 0; i < size; i++)
        {
            final int mapped = mapIndex(i);

            _status[i] = (short) _underlying.getStatus(mapped);

            if (_underlying.hasNextStatus(mapped))
            {
                _nextStatus[i] = (short) _underlying.getNextStatus(mapped);
            }
            else
            {
                _nextStatus[i] = -1;
            }
        }

        final List<? extends ItemCurve<?>> curveList = _params.getTransformationList();
        _regOrdinals = new short[regList.size()];
        _curves = new ItemCurve[regList.size()];

        for (int i = 0; i < regList.size(); i++)
        {
            _regOrdinals[i] = (short) regList.get(i).ordinal();
            _curves[i] = curveList.get(i);
        }

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
    public int getStatus(int index_)
    {
        return _status[index_];
    }

    @Override
    public int getNextStatus(int index_)
    {
        if (!hasNextStatus(index_))
        {
            throw new IllegalArgumentException("Unknown status.");
        }

        return _nextStatus[index_];
    }

    @Override
    public boolean hasNextStatus(int index_)
    {
        final boolean output = _nextStatus[index_] != -1;
        return output;
    }

    @Override
    public void getRegressors(int index_, double[] output_)
    {
        final int regCount = _regOrdinals.length;

        for (int i = 0; i < regCount; i++)
        {
            final double raw = _regressors[_regOrdinals[i]][index_];

            final ItemCurve<?> curve = _curves[i];

            if (null == curve)
            {
                output_[i] = raw;
            }
            else
            {
                output_[i] = curve.transform(raw);
            }
        }
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

    private float[] extractRegressor(final R regressor_)
    {
        final int ordinal = regressor_.ordinal();

        if (null != _regressors[ordinal])
        {
            return _regressors[ordinal];
        }

        final int size = _underlying.totalSize();
        _regressors[ordinal] = new float[size];

        final ItemRegressorReader reader = _underlying.getRegressorReader(regressor_);

        for (int i = 0; i < size; i++)
        {
            final int mapped = mapIndex(i);
            _regressors[ordinal][i] = (float) reader.asDouble(mapped);
        }

        return _regressors[ordinal];
    }

    private int mapIndex(final int index_)
    {
        return _indexMap[index_];
    }

    private final class MappedReader implements ItemRegressorReader
    {
        private final float[] _data;

        public MappedReader(final R field_)
        {
            _data = extractRegressor(field_);
        }

        @Override
        public double asDouble(int index_)
        {
            return _data[index_];
        }

    }

}
