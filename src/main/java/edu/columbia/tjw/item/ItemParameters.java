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
import java.util.Arrays;
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
    private static final long serialVersionUID = 321502161012435523L;
    private final S _status;
    private final int _selfIndex;
    private final double[][] _betas;
    private final List<R> _fields; //N.B: _fields are sorted, keep it that way.
    private final List<ItemCurve<T>> _trans;
    private final List<ParamFilter<S, R, T>> _filters;

    private final int[] _mergePointer;

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
        filters.add(new SelfTransitionFilter<>());
        _filters = Collections.unmodifiableList(filters);

        _mergePointer = new int[1];

        //We don't need to merge anything in for the intercept.
        _mergePointer[0] = -1;
    }

    private ItemParameters(final S status_, final double[][] betas_, final List<R> fields_, final List<? extends ItemCurve<T>> trans_, final List<ParamFilter<S, R, T>> filters_, final int[] mergePointers_)
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

        if (mergePointers_.length != regCount)
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        _status = status_;
        _betas = betas_;

        _fields = Collections.unmodifiableList(new ArrayList<>(fields_));
        _trans = Collections.unmodifiableList(new ArrayList<>(trans_));

        _selfIndex = _status.getReachable().indexOf(_status);
        _filters = Collections.unmodifiableList(new ArrayList<>(filters_));

        _mergePointer = mergePointers_;
    }

    private boolean checkFilter(S fromStatus_, S toStatus_, R field_, ItemCurve<T> trans_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        if (null == filters_)
        {
            return false;
        }

        for (final ParamFilter<S, R, T> next : filters_)
        {
            if (next.isFiltered(fromStatus_, toStatus_, field_, trans_))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isFiltered(S fromStatus_, S toStatus_, R field_, ItemCurve<T> trans_)
    {
        return isFiltered(fromStatus_, toStatus_, field_, trans_, null);
    }

    public boolean isFiltered(S fromStatus_, S toStatus_, R field_, ItemCurve<T> trans_, final Collection<ParamFilter<S, R, T>> extraFilters_)
    {
        if (checkFilter(fromStatus_, toStatus_, field_, trans_, getFilters()))
        {
            return true;
        }
        if (checkFilter(fromStatus_, toStatus_, field_, trans_, extraFilters_))
        {
            return true;
        }

        return false;
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
        //We demand exact reference equality for this operation.
        //return (trans1_ == trans2_);
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

        outer:
        for (int i = 0; i < regCount; i++)
        {
            final R next = regressorList.get(i);

            if (field_.equals(next))
            {
                keep[i] = false;
                continue;
            }

            //We will drop anything that depends (even indirectly) on the regressor.
            int currPointer = i;

            while (_mergePointer[currPointer] != -1)
            {
                currPointer = _mergePointer[currPointer];
                final R curr = regressorList.get(currPointer);

                if (field_.equals(curr))
                {
                    keep[i] = false;
                    continue outer;
                }
            }

            //Made it through, this is something we want to keep.
            keep[i] = true;
        }

        return dropEntries(field_, keep);
    }

    public ItemParameters<S, R, T> dropIndex(final int index_)
    {
        final int regCount = this.regressorCount();
        final boolean[] keep = new boolean[regCount];
        Arrays.fill(keep, true);
        keep[index_] = false;

        return dropEntries(null, keep);
    }

    private ItemParameters<S, R, T> dropEntries(final R field_, final boolean[] keep_)
    {
        final int regCount = this.regressorCount();

        if (keep_.length != regCount)
        {
            throw new IllegalArgumentException("Keep vector size mismatch.");
        }

        int keepCount = 0;
        final List<R> regressorList = this.getRegressorList();
        final List<ItemCurve<T>> tranList = this.getTransformationList();
        final List<R> reducedReg = new ArrayList<>();
        final List<ItemCurve<T>> reducedTran = new ArrayList<>();

        for (int i = 0; i < regCount; i++)
        {
            if (keep_[i])
            {
                keepCount++;
                reducedReg.add(regressorList.get(i));
                reducedTran.add(tranList.get(i));
            }
        }

        final double[][] reducedBeta = new double[_betas.length][keepCount];
        final int[] reducedMerge = new int[keepCount];

        int pointer = 0;

        for (int k = 0; k < regCount; k++)
        {
            if (!keep_[k])
            {
                continue;
            }

            for (int i = 0; i < reducedBeta.length; i++)
            {
                reducedBeta[i][pointer] = _betas[i][k];
            }

            reducedMerge[pointer] = _mergePointer[k];
            pointer++;
        }

        final List<ParamFilter<S, R, T>> reducedFilters = new ArrayList<>();

        //Get rid of any filters related specifically to this regressor, and that should
        //be dropped when this regressor is dropped.
        for (final ParamFilter<S, R, T> filter : _filters)
        {
            final R related = filter.relatedRegressor();

            //If the field is null, just keep everything.
            if ((null == field_) || !field_.equals(related))
            {
                reducedFilters.add(filter);
            }
        }

        final ItemParameters<S, R, T> output = new ItemParameters<>(_status, reducedBeta, reducedReg, reducedTran, reducedFilters, reducedMerge);
        return output;
    }

    /**
     * Creates a new set of parameters with an additional beta.
     *
     * @param field_ The regressor on which the new curve is defined
     * @param trans_ The curve that is being added (defined on field_)
     * @return A new set of parameters, with the given curve added and its beta
     * set to zero.
     */
    public ItemParameters<S, R, T> addBeta(final R field_, final ItemCurve<T> trans_)
    {
        return addBeta(field_, trans_, -1);
    }

    /**
     * Creates a new set of parameters with an additional beta.
     *
     * @param field_ The regressor on which the new curve is defined
     * @param trans_ The curve that is being added (defined on field_)
     * @param mergePointer_ Pointer to the curve to merge (or -1 if none)
     * @return A new set of parameters, with the given curve added and its beta
     * set to zero.
     */
    public ItemParameters<S, R, T> addBeta(final R field_, final ItemCurve<T> trans_, final int mergePointer_)
    {
        if (mergePointer_ < -1 || mergePointer_ >= _mergePointer.length)
        {
            throw new IllegalArgumentException("Invalid merge pointer.");
        }

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

        final int[] mergePointers = this.resizeArray(_mergePointer, addedIndex, mergePointer_);

        final ItemParameters<S, R, T> output = new ItemParameters<>(_status, beta, fields, trans, _filters, mergePointers);
        return output;
    }

    public ItemParameters<S, R, T> updateBetas(final double[][] betas_)
    {
        return new ItemParameters<>(_status, betas_, _fields, _trans, _filters, _mergePointer);
    }

    public ItemParameters<S, R, T> addFilters(final Collection<ParamFilter<S, R, T>> filters_)
    {
        final List<ParamFilter<S, R, T>> filters = new ArrayList<>(_filters);
        filters.addAll(filters_);
        return new ItemParameters<>(_status, _betas, _fields, _trans, filters, _mergePointer);
    }

    public ItemParameters<S, R, T> addFilter(final ParamFilter<S, R, T> filter_)
    {
        final Set<ParamFilter<S, R, T>> set = Collections.singleton(filter_);
        return this.addFilters(set);
    }

    public int toStatusIndex(final S toStatus_)
    {
        final int index = _status.getReachable().indexOf(toStatus_);
        return index;
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

    /**
     * The list of all (not necessarily unique) regressors underlying this
     * model's inputs.
     *
     * A regressor may appear many times if it is transformed by several curves.
     *
     * @return THe list of all regressors used in these parameters
     */
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

    private int[] resizeArray(final int[] input_, final int insertionPoint_, final int insertionValue_)
    {
        final int[] output = new int[input_.length + 1];

        for (int i = 0; i < insertionPoint_; i++)
        {
            output[i] = input_[i];
        }

        output[insertionPoint_] = insertionValue_;

        for (int i = insertionPoint_; i < input_.length; i++)
        {
            output[i + 1] = input_[i];
        }

        return output;
    }

    private final class SelfTransitionFilter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements ParamFilter<S, R, T>
    {
        private static final long serialVersionUID = 3355948289866022590L;

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
