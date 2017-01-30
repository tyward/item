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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author tyler
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 */
public final class ItemParameters<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements Serializable
{
    private static final int[] EMPTY = new int[0];
    private static final long serialVersionUID = 0x35c74a5424d6cf48L;

    private final S _status;
    private final int _selfIndex;
    private final R _intercept;
    private final List<ItemCurve<T>> _trans;
    private final List<ParamFilter<S, R, T>> _filters;

    private final List<R> _uniqueFields;

    private final double[][] _betas;

    //This is 2-D so that we can have multiple field/curves per entry, allowing for interactions through weighting.
    private final int[][] _fieldOffsets;
    private final int[][] _transOffsets;

    // If  we allow only one beta to be set for this entry, place its index here, otherwise -1.
    private final int[] _uniqueBeta;

    public ItemParameters(final S status_, final R intercept_)
    {
        if (null == status_)
        {
            throw new NullPointerException("Status cannot be null.");
        }
        if (null == intercept_)
        {
            throw new NullPointerException("Intercept cannot be null.");
        }

        _status = status_;
        _intercept = intercept_;

        _betas = new double[status_.getReachableCount()][1];

        _uniqueFields = Collections.unmodifiableList(Collections.singletonList(intercept_));
        _trans = Collections.unmodifiableList(Collections.singletonList(null));
        _filters = Collections.unmodifiableList(Collections.singletonList(new UniqueBetaFilter()));

        _uniqueBeta = new int[1];
        _uniqueBeta[0] = -1;

        _fieldOffsets = new int[1][1];
        _fieldOffsets[0][0] = 0;

        _transOffsets = new int[1][1];
        _transOffsets[0][0] = 0;

        _selfIndex = _status.getReachable().indexOf(_status);
    }

    /**
     * The constructor used to change betas, or add filters.
     *
     * @param base_
     * @param betas_
     * @param addedFilters_
     */
    private ItemParameters(final ItemParameters<S, R, T> base_, final double[][] betas_, final Collection<ParamFilter<S, R, T>> addedFilters_)
    {
        _status = base_._status;
        _intercept = base_._intercept;
        _selfIndex = base_._selfIndex;
        _trans = base_._trans;
        _uniqueFields = base_._uniqueFields;
        _fieldOffsets = base_._fieldOffsets;
        _transOffsets = base_._transOffsets;
        _uniqueBeta = base_._uniqueBeta;

        if (null != betas_)
        {
            final int size = base_._betas.length;

            //We will build up a clone to make sure there are no problems with external modification.
            double[][] newBeta = new double[size][];

            if (size != betas_.length)
            {
                throw new IllegalArgumentException("Beta matrix is the wrong size.");
            }
            for (int i = 0; i < size; i++)
            {
                if (base_._betas[i].length != betas_[i].length)
                {
                    throw new IllegalArgumentException("Beta matrix is the wrong size.");
                }

                newBeta[i] = betas_[i].clone();
            }

            _betas = newBeta;
        }
        else
        {
            _betas = base_._betas;
        }

        if (addedFilters_.size() < 1)
        {
            _filters = base_._filters;
        }
        else
        {
            final List<ParamFilter<S, R, T>> newFilters = new ArrayList<>(base_._filters);

            for (final ParamFilter<S, R, T> next : addedFilters_)
            {
                newFilters.add(next);
            }

            _filters = Collections.unmodifiableList(newFilters);
        }
    }

    /**
     * Used to make a new set of parameters with a new entry.
     *
     * @param base_
     * @param regs_
     * @param curves_
     */
    private ItemParameters(final ItemParameters<S, R, T> base_, final List<R> regs_, final List<ItemCurve<T>> curves_, final int toStatusTarget_)
    {
        _status = base_._status;
        _intercept = base_._intercept;
        _selfIndex = base_._selfIndex;
        _filters = base_._filters;
//        _trans = base_._trans;
//        _uniqueFields = base_._uniqueFields;
//        _fieldOffsets = base_._fieldOffsets;
//        _transOffsets = base_._transOffsets;
//        _uniqueBeta = base_._uniqueBeta;

        final SortedSet<R> newFields = new TreeSet<>();

        //Workaround for issues with nulls.
        final Set<ItemCurve<T>> newTrans = new HashSet<>();

        newFields.addAll(base_._uniqueFields);
        newFields.addAll(regs_);

        newTrans.addAll(base_._trans);
        newTrans.addAll(curves_);

        final int baseEntryCount = base_.getEntryCount();
        final int newEntryCount = baseEntryCount + 1;
        final int endIndex = baseEntryCount;

        //Just add the new entry to the end of the list.
        _trans = Collections.unmodifiableList(new ArrayList<>(newTrans));
        _uniqueFields = Collections.unmodifiableList(new ArrayList<>(newFields));

        _uniqueBeta = Arrays.copyOf(base_._uniqueBeta, newEntryCount);

        _uniqueBeta[endIndex] = toStatusTarget_;
        _fieldOffsets = new int[newEntryCount][];
        _transOffsets = new int[newEntryCount][];
        _betas = new double[base_._betas.length][];

        //Copy over prev betas, fill out with zeros. Will adjust again to change the betas...
        for (int i = 0; i < _betas.length; i++)
        {
            _betas[i] = Arrays.copyOf(base_._betas[i], newEntryCount);
        }

        //First, pull in all the old entries.
        for (int i = 0; i < baseEntryCount; i++)
        {
            final int depth = base_.getEntryDepth(i);
            _fieldOffsets[i] = new int[depth];
            _transOffsets[i] = new int[depth];

            for (int w = 0; w < depth; w++)
            {
                final R field = base_.getEntryRegressor(i, w);
                final ItemCurve<T> curve = base_.getEntryCurve(i, w);

                _fieldOffsets[i][w] = _uniqueFields.indexOf(field);
                _transOffsets[i][w] = _trans.indexOf(curve);
            }
        }

        //Now fill out the last entry...
        final int endDepth = regs_.size();

        if (curves_.size() != endDepth)
        {
            throw new IllegalArgumentException("Invalid depth.");
        }

        _fieldOffsets[endIndex] = new int[endDepth];
        _transOffsets[endIndex] = new int[endDepth];

        for (int i = 0; i < endDepth; i++)
        {
            final R field = regs_.get(i);
            final ItemCurve<T> curve = curves_.get(i);

            _fieldOffsets[endIndex][i] = _uniqueFields.indexOf(field);
            _transOffsets[endIndex][i] = _trans.indexOf(curve);
        }

    }

    /**
     * Used to make a new set of parameters with some indices dropped.
     *
     * @param base_
     * @param dropIndices_
     */
    private ItemParameters(final ItemParameters<S, R, T> base_, final int[] dropIndices_)
    {
        _status = base_._status;
        _intercept = base_._intercept;
        _selfIndex = base_._selfIndex;
        _filters = base_._filters;

        final int startSize = base_.getEntryCount();
        final boolean[] drop = new boolean[startSize];

        // We are open to the possibility that dropIndices_ is not well formed, 
        // and some indices occur multiple times.
        for (int i = 0; i < dropIndices_.length; i++)
        {
            drop[dropIndices_[i]] = true;
        }

        int dropped = 0;

        for (int i = 0; i < startSize; i++)
        {
            if (drop[i])
            {
                dropped++;
            }
        }

        final int newSize = startSize - dropped;

        _fieldOffsets = new int[newSize][];
        _transOffsets = new int[newSize][];
        _betas = new double[base_._betas.length][newSize];
        _uniqueBeta = new int[newSize];

        final SortedSet<R> newFields = new TreeSet<>();
        final Set<ItemCurve<T>> newTrans = new HashSet<>();

        int pointer = 0;

        for (int i = 0; i < startSize; i++)
        {
            if (drop[i])
            {
                continue;
            }

            _uniqueBeta[pointer] = base_._uniqueBeta[i];

            final int depth = base_.getEntryDepth(i);

            _fieldOffsets[pointer] = new int[depth];
            _transOffsets[pointer] = new int[depth];
            pointer++;

            for (int w = 0; w < depth; w++)
            {
                final R next = base_.getEntryRegressor(i, w);
                final ItemCurve<T> nextCurve = base_.getEntryCurve(i, w);

                newFields.add(next);
                newTrans.add(nextCurve);
            }
        }

        if (pointer != newSize)
        {
            throw new IllegalStateException("Impossible.");
        }

        this._trans = Collections.unmodifiableList(new ArrayList<>(newTrans));
        this._uniqueFields = Collections.unmodifiableList(new ArrayList<>(newFields));

        pointer = 0;

        for (int i = 0; i < startSize; i++)
        {
            if (drop[i])
            {
                continue;
            }

            final int depth = base_.getEntryDepth(i);

            for (int w = 0; w < depth; w++)
            {
                final R reg = base_.getEntryRegressor(i, w);
                final ItemCurve<T> curve = base_.getEntryCurve(i, w);

                final int rIndex = _uniqueFields.indexOf(reg);
                final int cIndex = _trans.indexOf(curve);

                if (rIndex < 0 || cIndex < 0)
                {
                    throw new IllegalStateException("Impossible.");
                }

                _fieldOffsets[pointer][w] = rIndex;
                _transOffsets[pointer][w] = cIndex;
            }

            for (int w = 0; w < _betas.length; w++)
            {
                _betas[w][pointer] = base_._betas[w][i];
            }

            pointer++;
        }
    }

    public int getEntryCount()
    {
        return _fieldOffsets.length;
    }

    public int getEntryDepth(final int entryIndex_)
    {
        return _fieldOffsets[entryIndex_].length;
    }

    public int getEntryRegressorOffset(final int entryIndex_, final int entryDepth_)
    {
        return _fieldOffsets[entryIndex_][entryDepth_];
    }

    public R getEntryRegressor(final int entryIndex_, final int entryDepth_)
    {
        final int offset = getEntryRegressorOffset(entryIndex_, entryDepth_);
        return _uniqueFields.get(offset);
    }

    public int getEntryCurveOffset(final int entryIndex_, final int entryDepth_)
    {
        return _transOffsets[entryIndex_][entryDepth_];
    }

    public ItemCurve<T> getEntryCurve(final int entryIndex_, final int entryDepth_)
    {
        final int offset = getEntryCurveOffset(entryIndex_, entryDepth_);
        return _trans.get(offset);
    }

    public List<R> getUniqueRegressors()
    {
        return _uniqueFields;
    }

    public List<ItemCurve<T>> getUniqueCurves()
    {
        return _trans;
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

    private List<ParamFilter<S, R, T>> getFilters()
    {
        return _filters;
    }

    public int getIndex(final R field_, final ItemCurve<?> trans_)
    {
        for (int i = 0; i < this.getEntryCount(); i++)
        {
            final R nextReg = this.getEntryRegressor(i, 0);

            if (nextReg != field_)
            {
                continue;
            }

            final ItemCurve<T> curve = this.getEntryCurve(i, 0);

            if (!compareTrans(curve, trans_))
            {
                continue;
            }

            return i;
        }

        return -1;
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
        final int regCount = this.getEntryCount();
        final boolean[] keep = new boolean[regCount];

        outer:
        for (int i = 0; i < regCount; i++)
        {
            final R next = getEntryRegressor(i, 0);

            if (!field_.equals(next))
            {
                keep[i] = true;
                continue;
            }

            keep[i] = false;
        }

        return dropEntries(field_, keep);
    }

    public ItemParameters<S, R, T> dropIndex(final int index_)
    {
        final int regCount = this.getEntryCount();
        final boolean[] keep = new boolean[regCount];
        Arrays.fill(keep, true);
        keep[index_] = false;

        return dropEntries(null, keep);
    }

    private ItemParameters<S, R, T> dropEntries(final R field_, final boolean[] keep_)
    {
        int dropCount = 0;

        for (final boolean next : keep_)
        {
            if (!next)
            {
                dropCount++;
            }
        }

        final int[] dropIndices = new int[dropCount];
        int pointer = 0;

        for (int i = 0; i < keep_.length; i++)
        {
            if (keep_[i])
            {
                continue;
            }

            dropIndices[pointer++] = i;
        }

        return new ItemParameters<>(this, dropIndices);
    }

    /**
     * Creates a new set of parameters with an additional beta.
     *
     * @param field_ The regressor on which the new curve is defined
     * @param trans_ The curve that is being added (defined on field_)
     * @return A new set of parameters, with the given curve added and its beta
     * set to zero.
     */
    public ItemParameters<S, R, T> addBeta(final R field_, final ItemCurve<T> trans_, final int toStatusIndex_)
    {
        final List<R> fields = Collections.singletonList(field_);
        final List<ItemCurve<T>> curves = Collections.singletonList(trans_);

        return new ItemParameters<>(this, fields, curves, toStatusIndex_);
    }

    public ItemParameters<S, R, T> updateBetas(final double[][] betas_)
    {
        return new ItemParameters<>(this, betas_, Collections.emptyList());
    }

    public ItemParameters<S, R, T> addFilters(final Collection<ParamFilter<S, R, T>> filters_)
    {
        return new ItemParameters<>(this, null, filters_);
    }

    public ItemParameters<S, R, T> addFilter(final ParamFilter<S, R, T> filter_)
    {
        final Set<ParamFilter<S, R, T>> set = Collections.singleton(filter_);
        return this.addFilters(set);
    }

    public int getReachableSize()
    {
        return _status.getReachableCount();
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

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("ItemParameters[" + Integer.toHexString(System.identityHashCode(this)) + "][\n");

        builder.append("\tEntries[" + this.getEntryCount() + "]: \n");

        for (int i = 0; i < this.getEntryCount(); i++)
        {
            builder.append("\t\t Entry \n \t\t\tBeta: [");

            for (int w = 0; w < _betas.length; w++)
            {
                if (w > 0)
                {
                    builder.append(", ");
                }

                builder.append(_betas[w][i]);
            }

            builder.append("]\n");

            final int depth = this.getEntryDepth(i);

            builder.append("\t\t Entry Definition[" + i + ", depth=" + depth + "]: \n");

            for (int w = 0; w < this.getEntryDepth(i); w++)
            {
                final R reg = this.getEntryRegressor(i, w);
                final ItemCurve<T> curve = this.getEntryCurve(i, w);

                builder.append("\t\t\t[" + i + ", " + w + "]:" + reg + ":" + curve + "\n");
            }

            builder.append("\t\t\t Entry Beta Restricted: " + _uniqueBeta[i]);
        }

        builder.append("]\n\n");

        return builder.toString();
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

    private final class UniqueBetaFilter implements ParamFilter<S, R, T>
    {
        private static final long serialVersionUID = 0x49e89a36e4553a69L;

        @Override
        public boolean isFiltered(S fromStatus_, S toStatus_, R field_, ItemCurve<T> trans_)
        {
            if (fromStatus_ != ItemParameters.this.getStatus())
            {
                return false;
            }

            if (fromStatus_.equals(toStatus_))
            {
                return true;
            }

            final int toIndex = ItemParameters.this.getStatus().getReachable().indexOf(toStatus_);

            final int entryCount = getEntryCount();

            for (int i = 0; i < entryCount; i++)
            {
                final R reg = getEntryRegressor(i, 0);

                if (reg != field_)
                {
                    continue;
                }

                final ItemCurve<T> trans = getEntryCurve(i, 0);

                if (trans != trans_)
                {
                    continue;
                }

                if (_uniqueBeta[i] != -1 && _uniqueBeta[i] != toIndex)
                {
                    return true;
                }
            }

            return false;
        }
    }

    private final class SelfTransitionFilter implements ParamFilter<S, R, T>
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

    }

}
