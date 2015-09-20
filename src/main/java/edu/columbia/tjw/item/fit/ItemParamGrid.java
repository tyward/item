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
import edu.columbia.tjw.item.ItemGrid;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public abstract class ItemParamGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final ItemParameters<S, R, T> _params;
    //private final ItemGrid<R> _grid;

    private final int[] _regressorIndices;
    private final List<ItemCurve<T>> _transformations;
    private final List<R> _uniqueRegressors;
    private final ItemRegressorReader[] _readers;
    private final int _uniqueCount;

    public ItemParamGrid(final ItemParameters<S, R, T> params_, final ItemGrid<R> grid_)
    {
        _params = params_;

        final List<R> regressors = _params.getRegressorList();
        final int regressorCount = regressors.size();
        final SortedSet<R> uniqueRegressors = new TreeSet<>(regressors);
        _uniqueRegressors = new ArrayList<>(uniqueRegressors);
        _uniqueCount = _uniqueRegressors.size();

        _regressorIndices = new int[regressorCount];
        _readers = new ItemRegressorReader[_uniqueCount];

        for (int i = 0; i < regressorCount; i++)
        {
            final R next = regressors.get(i);
            _regressorIndices[i] = _uniqueRegressors.indexOf(next);
        }

        for (int i = 0; i < _uniqueCount; i++)
        {
            final R next = _uniqueRegressors.get(i);
            _readers[i] = grid_.getRegressorReader(next);
        }

        _transformations = _params.getTransformationList();
    }

    public ItemParameters<S, R, T> getParams()
    {
        return _params;
    }

    public abstract ItemGrid<R> getUnderlying();

    /**
     * Get the regressors for this observation.
     *
     * Note that the results will be transformed (according to the curves from
     * getTransformation)
     *
     * @param index_
     * @param output_
     */
    public void getRegressors(final int index_, final double[] output_)
    {
        if (output_.length != _transformations.size())
        {
            throw new IllegalArgumentException("Size mismatch: " + output_.length + " != " + _transformations.size());
        }

        final int outputCount = _transformations.size();
        int pointer = 0;

        for (int i = 0; i < _uniqueCount; i++)
        {
            final double rawRegressor = _readers[i].asDouble(index_);

            for (; (pointer < outputCount) && (_regressorIndices[pointer] == i); pointer++)
            {
                output_[pointer] = rawRegressor;
            }
        }

        for (int i = 0; i < outputCount; i++)
        {
            final ItemCurve<T> trans = _transformations.get(i);

            if (null == trans)
            {
                continue;
            }

            final double raw = output_[i];
            final double transformed = trans.transform(raw);
            output_[i] = transformed;
        }

    }

}