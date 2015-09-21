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
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.ItemGridFactory;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.EnumFamily;
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
public final class RandomizedCurveFactory<S extends ItemStatus<S>, R extends ItemRegressor<R>> implements ItemGridFactory<S, R>
{
    private final ItemGridFactory<S, R> _underlying;
    private final ItemSettings _settings;
    private final EnumFamily<R> _family;
    private final float[][] _regressors;

    private int[] _indexMap = null;

    public RandomizedCurveFactory(final ItemGridFactory<S, R> underlying_, final ItemSettings settings_, final EnumFamily<R> family_)
    {
        _underlying = underlying_;
        _settings = settings_;
        _family = family_;

        final int familySize = family_.size();
        _regressors = new float[familySize][];
    }

    @Override
    public <T extends ItemCurveType<T>> ItemFittingGrid<S, R> prepareGrid(ItemParameters<S, R, T> params_)
    {
        final ItemFittingGrid<S, R> grid = _underlying.prepareGrid(params_);
        ensureMapped(grid);
        final RandomizedFittingGrid<S, R> random = new RandomizedFittingGrid<>(params_, grid, _settings, _family);
        return random;
    }

    private void ensureMapped(final ItemFittingGrid<S, R> grid_)
    {
        if (null != _indexMap)
        {
            return;
        }

        _indexMap = new int[grid_.totalSize()];

        for (int i = 0; i < _indexMap.length; i++)
        {
            _indexMap[i] = i;
        }

        if (_settings.isRandomShuffle())
        {
            RandomTool.shuffle(_indexMap, _settings.getRandom());
        }
    }

    private int mapIndex(final int index_)
    {
        return _indexMap[index_];
    }

    public final class RandomizedFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>> implements ItemFittingGrid<S, R>
    {
        private final ItemParameters<S, R, ? extends ItemCurveType<?>> _params;
        private final ItemFittingGrid<S, R> _underlying;
        private final ItemRegressorReader[] _readers;

        private final short[] _status;
        private final short[] _nextStatus;

        //private final short[] _regOrdinals;
        private final ItemCurve[] _curves;
        private final EnumFamily<R> _family;

        private final float[][] _regByPosition;
        private final int _regCount;

        public RandomizedFittingGrid(final ItemParameters<S, R, ? extends ItemCurveType<?>> params_, final ItemFittingGrid<S, R> underlying_, final ItemSettings settings_, final EnumFamily<R> family_)
        {
            _family = family_;
            _params = params_;
            _underlying = underlying_;

            final int size = _underlying.totalSize();

            final List<R> regList = params_.getRegressorList();
            final SortedSet<R> regSet = new TreeSet<>(regList);
            final int allRegs = regSet.first().getFamily().size();

            _readers = new ItemRegressorReader[allRegs];
            _regCount = regList.size();

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
            _curves = new ItemCurve[_regCount];
            _regByPosition = new float[_regCount][];

            synchronized (this)
            {
                for (int i = 0; i < _regCount; i++)
                {
                    final int regOrdinal = regList.get(i).ordinal();
                    _regByPosition[i] = fetchRegressor(regOrdinal, underlying_);
                    _curves[i] = curveList.get(i);
                }
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
            for (int i = 0; i < _regCount; i++)
            {
                final double raw = _regByPosition[i][index_];

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

        @Override
        public int size()
        {
            return _underlying.size();
        }

        @Override
        public EnumFamily<S> getStatusFamily()
        {
            return _underlying.getStatusFamily();
        }

        private final class MappedReader implements ItemRegressorReader
        {
            private final float[] _data;

            public MappedReader(final R field_)
            {
                _data = fetchRegressor(field_.ordinal(), _underlying);
            }

            @Override
            public double asDouble(int index_)
            {
                return _data[index_];
            }

            public int size()
            {
                return _data.length;
            }
            
            
        }

        private synchronized float[] fetchRegressor(final int ordinal_, final ItemFittingGrid<S, R> grid_)
        {
            final float[] cached = _regressors[ordinal_];

            if (null != cached)
            {
                return cached;
            }

            final int tabSize = grid_.totalSize();
            final R reg = _family.getFromOrdinal(ordinal_);
            final ItemRegressorReader reader = grid_.getRegressorReader(reg);
            _regressors[ordinal_] = new float[tabSize];

            for (int i = 0; i < tabSize; i++)
            {
                final int mapped = mapIndex(i);
                _regressors[ordinal_][i] = (float) reader.asDouble(mapped);
            }

            return _regressors[ordinal_];
        }
    }

}
