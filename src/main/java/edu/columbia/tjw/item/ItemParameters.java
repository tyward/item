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
package edu.columbia.tjw.item;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 *
 * @author tyler
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 */
public final class ItemParameters<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements Serializable
{
    private static final long serialVersionUID = 21032932723181271L;
    private final S _status;
    private final int _selfIndex;
    private final double[][] _betas;
    private final List<R> _fields; //N.B: _fields are sorted, keep it that way.
    private final List<ItemCurve<T>> _trans;
    private final List<ParamFilter<S, R, T>> _filters;

    public ItemParameters(final S status_, final List<R> fields_)
    {
        _status = status_;
        _betas = new double[status_.getReachableCount()][1];

        _fields = Collections.unmodifiableList(new ArrayList<>(fields_));

        final List<ItemCurve<T>> trans = new ArrayList<>();
        trans.add(null);
        _trans = Collections.unmodifiableList(trans);

        _selfIndex = _status.getReachable().indexOf(_status);

        for (int i = 0; i < _betas.length; i++)
        {
            if (i != this._selfIndex)
            {
                _betas[i][0] = -3.0;
            }
        }

        final List<ParamFilter<S, R, T>> filters = new ArrayList<>();
        filters.add(new SelfTransitionFilter<S, R, T>());
        _filters = Collections.unmodifiableList(filters);
    }

    private ItemParameters(final S status_, final double[][] betas_, final List<R> fields_, final List<? extends ItemCurve<T>> trans_, final List<ParamFilter<S, R, T>> filters_)
    {
        if (betas_.length != status_.getReachableCount())
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        final int regCount = fields_.size();

        if (regCount != trans_.size())
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        for (final double[] nextBeta : betas_)
        {
            if (nextBeta.length != regCount)
            {
                throw new IllegalArgumentException("Size mismatch.");
            }
        }

        _status = status_;
        _betas = betas_;

        _fields = Collections.unmodifiableList(new ArrayList<>(fields_));
        _trans = Collections.unmodifiableList(new ArrayList<>(trans_));

        _selfIndex = _status.getReachable().indexOf(_status);
        _filters = Collections.unmodifiableList(new ArrayList<>(filters_));
    }

    public List<ParamFilter<S, R, T>> getFilters()
    {
        return _filters;
    }

    public int getIndex(final R field_, final ItemCurve<?> trans_)
    {
        final int fieldIndex = _fields.indexOf(field_);

        if (fieldIndex < 0)
        {
            return -1;
        }

        int searchIndex = fieldIndex;

        while (!compareTrans(trans_, _trans.get(searchIndex)))
        {
            searchIndex++;

            if (searchIndex >= _trans.size())
            {
                return -1;
            }

            if (!field_.equals(_fields.get(searchIndex)))
            {
                return -1;
            }
        }

        return searchIndex;
    }

    private boolean compareTrans(final ItemCurve<?> trans1_, final ItemCurve<?> trans2_)
    {
        if (trans1_ == trans2_)
        {
            return true;
        }
        if (null == trans1_)
        {
            return false;
        }

        return trans1_.equals(trans2_);
    }

    public ItemParameters<S, R, T> dropRegressor(final R field_)
    {
        final int regCount = this.regressorCount();
        final boolean[] keep = new boolean[regCount];

        final List<R> regressorList = this.getRegressorList();
        final List<ItemCurve<T>> tranList = this.getTransformationList();
        final List<R> reducedReg = new ArrayList<>();
        final List<ItemCurve<T>> reducedTran = new ArrayList<>();

        int keepCount = 0;

        for (int i = 0; i < regCount; i++)
        {
            final R next = regressorList.get(i);

            if (field_.equals(next))
            {
                keep[i] = false;
            }
            else
            {
                reducedReg.add(next);
                reducedTran.add(tranList.get(i));
                keep[i] = true;
                keepCount++;
            }
        }

        final double[][] reducedBeta = new double[_betas.length][keepCount];

        int pointer = 0;

        for (int k = 0; k < regCount; k++)
        {
            if (!keep[k])
            {
                continue;
            }

            for (int i = 0; i < reducedBeta.length; i++)
            {
                reducedBeta[i][pointer] = _betas[i][k];
            }

            pointer++;
        }

        final List<ParamFilter<S, R, T>> reducedFilters = new ArrayList<>();

        //Get rid of any filters related specifically to this regressor, and that should
        //be dropped when this regressor is dropped.
        for (final ParamFilter<S, R, T> filter : _filters)
        {
            final R related = filter.relatedRegressor();

            if (!field_.equals(related))
            {
                reducedFilters.add(filter);
            }
        }

        final ItemParameters<S, R, T> output = new ItemParameters<>(_status, reducedBeta, reducedReg, reducedTran, reducedFilters);

        return output;
    }

    /**
     * Creates a new set of parameters with an additional beta.
     *
     * @param field_
     * @param trans_
     * @return
     */
    public ItemParameters<S, R, T> addBeta(final R field_, final ItemCurve<T> trans_)
    {
        final List<R> fields = new ArrayList<>(_fields);
        final List<ItemCurve<T>> trans = new ArrayList<>(_trans);

        final int index = fields.indexOf(field_);
        final int addedIndex;

        if (index < 0)
        {
            //Find where we would put it in the array to keep things sorted. 
            final int targetIndex = Collections.binarySearch(_fields, field_);
            addedIndex = (-targetIndex) - 1;
        }
        else
        {
            addedIndex = index;
        }

        fields.add(addedIndex, field_);
        trans.add(addedIndex, trans_);

        final double[][] beta = this.getBetas();

        for (int i = 0; i < beta.length; i++)
        {
            beta[i] = this.resizeArray(beta[i], addedIndex);
        }

        final ItemParameters<S, R, T> output = new ItemParameters<>(_status, beta, fields, trans, _filters);
        return output;
    }

    public ItemParameters<S, R, T> updateBetas(final double[][] betas_)
    {
        return new ItemParameters<>(_status, betas_, _fields, _trans, _filters);
    }

    public ItemParameters<S, R, T> addFilters(final Collection<ParamFilter<S, R, T>> filters_)
    {
        final List<ParamFilter<S, R, T>> filters = new ArrayList<>(_filters);
        filters.addAll(filters_);
        return new ItemParameters<>(_status, _betas, _fields, _trans, filters);
    }

    public ItemParameters<S, R, T> addFilter(final ParamFilter<S, R, T> filter_)
    {
        final Set<ParamFilter<S, R, T>> set = Collections.singleton(filter_);
        return this.addFilters(set);
    }

    public double getBeta(final int statusIndex_, final int regIndex_)
    {
        return _betas[statusIndex_][regIndex_];
    }

    public double[][] getBetas()
    {
        final double[][] output = _betas.clone();

        for (int i = 0; i < output.length; i++)
        {
            output[i] = output[i].clone();
        }

        return output;
    }

    public S getStatus()
    {
        return _status;
    }

    public int regressorCount()
    {
        return _fields.size();
    }

    public List<R> getRegressorList()
    {
        return _fields;
    }

    public List<ItemCurve<T>> getTransformationList()
    {
        return _trans;
    }

    public R getRegressor(final int index_)
    {
        return _fields.get(index_);
    }

    public ItemCurve<T> getTransformation(final int index_)
    {
        return _trans.get(index_);
    }

    private double[] resizeArray(final double[] input_, final int insertionPoint_)
    {
        final double[] output = new double[input_.length + 1];

        for (int i = 0; i < insertionPoint_; i++)
        {
            output[i] = input_[i];
        }

        output[insertionPoint_] = 0.0;

        for (int i = insertionPoint_; i < input_.length; i++)
        {
            output[i + 1] = input_[i];
        }

        return output;
    }

    private final class SelfTransitionFilter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements ParamFilter<S, R, T>
    {
        public SelfTransitionFilter()
        {
        }

        @Override
        public boolean isFiltered(S fromStatus_, S toStatus_, R field_, ItemCurve<T> trans_)
        {
            final boolean output = fromStatus_.equals(toStatus_);
            return output;
        }

        @Override
        public R relatedRegressor()
        {
            return null;
        }

    }

}
