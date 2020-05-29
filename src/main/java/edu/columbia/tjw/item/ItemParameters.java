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

import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.util.EnumFamily;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * ItemParameters encapsulates everything needed to fully describe an item model. It has a fair bit of type-checking
 * logic as well, to ensure that the status types are compatible, and the regressors as well. This should prevent
 * issues like mismatched regressor names, shifting curve definitions, or status transition matrices that don't match
 * what the model was fit on.
 *
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 * @author tyler
 */
public final class ItemParameters<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements Serializable
{
    //This is just to make it clear that the 0th entry is always the intercept.
    private static final int INTERCEPT_INDEX = 0;
    private static final long serialVersionUID = 0x20315705d45eb662L;

    private final S _status;
    private final int _selfIndex;
    private final List<ItemCurve<T>> _trans;
    private final List<ParamFilter<S, R, T>> _filters;
    private final UniqueBetaFilter _uniqFilter = new UniqueBetaFilter();

    private final List<R> _uniqueFields;

    private final double[][] _betas;

    //This is 2-D so that we can have multiple field/curves per entry, allowing for interactions through weighting.
    private final int[][] _fieldOffsets;
    private final int[][] _transOffsets;

    // If  we allow only one beta to be set for this entry, place its index here, otherwise -1.
    private final int[] _uniqueBeta;

    private final int _effectiveParamCount;

    private final EnumFamily<T> _typeFamily;
    private final EnumFamily<R> _regFamily;

    public ItemParameters(final S status_, final EnumFamily<R> regFamily_, final EnumFamily<T> typeFamily_)
    {
        if (null == status_)
        {
            throw new NullPointerException("Status cannot be null.");
        }
        if (null == regFamily_)
        {
            throw new NullPointerException("Intercept cannot be null.");
        }
        if (null == typeFamily_)
        {
            throw new NullPointerException("Type family cannot be null.");
        }

        _status = status_;

        _betas = new double[status_.getReachableCount()][1];
        _uniqueFields = Collections.unmodifiableList(Collections.emptyList());

        _trans = Collections.unmodifiableList(Collections.singletonList(null));
        _filters = Collections.unmodifiableList(Collections.emptyList());

        _uniqueBeta = new int[1];
        _uniqueBeta[INTERCEPT_INDEX] = -1;

        _fieldOffsets = new int[1][1];
        _fieldOffsets[INTERCEPT_INDEX][0] = -1;

        _transOffsets = new int[1][1];
        _transOffsets[INTERCEPT_INDEX][0] = 0;

        _selfIndex = _status.getReachable().indexOf(_status);
        _effectiveParamCount = calculateEffectiveParamCount();

        _regFamily = regFamily_;
        _typeFamily = typeFamily_;
    }

    /**
     * We will not change the structure of the params at all, merely changing the value of some betas.
     *
     * @param base_
     * @param packed_
     */
    private ItemParameters(ItemParameters<S, R, T> base_, ItemParametersVector packed_)
    {
        _status = base_.getStatus();
        _selfIndex = base_._selfIndex;

        _filters = base_._filters;
        _uniqueFields = base_._uniqueFields;
        _fieldOffsets = base_._fieldOffsets;
        _transOffsets = base_._transOffsets;
        _uniqueBeta = base_._uniqueBeta;
        _effectiveParamCount = base_._effectiveParamCount;
        _regFamily = base_._regFamily;
        _typeFamily = base_._typeFamily;

        // We will go through and replace each of these curves...
        final List<ItemCurve<T>> trans = new ArrayList<>(base_._trans);
        _betas = new double[_status.getReachableCount()][base_._uniqueBeta.length];

        //Now we fill the data.
        int pointer = 0;

        final S fromStatus = this.getStatus();
        final List<S> reachable = fromStatus.getReachable();

        for (final S next : reachable)
        {
            for (int i = 0; i < ItemParameters.this.getEntryCount(); i++)
            {
                final S statusRestrict = ItemParameters.this.getEntryStatusRestrict(i);

                if (statusRestrict == null)
                {
                    if (next == fromStatus)
                    {
                        continue;
                    }

                    final int nextIndex = this.toStatusIndex(next);
                    _betas[nextIndex][i] = packed_.getParameter(pointer++);
                }
                else
                {
                    final int nextIndex = this.toStatusIndex(statusRestrict);

                    if (next != statusRestrict)
                    {
                        continue;
                    }

                    if (!packed_.isBeta(pointer))
                    {
                        throw new IllegalArgumentException("Impossible.");
                    }

                    _betas[nextIndex][i] = packed_.getParameter(pointer++);
                }

                for (int z = 0; z < ItemParameters.this.getEntryDepth(i); z++)
                {
                    final ItemCurve<T> curve = base_.getEntryCurve(i, z);

                    if (null == curve)
                    {
                        continue;
                    }

                    final T curveType = curve.getCurveType();
                    final int curveParamCount = curveType.getParamCount();
                    final ItemCurveFactory<R, T> factory = curveType.getFactory();

                    final double[] curveParams = new double[curveParamCount];

                    for (int w = 0; w < curveParamCount; w++)
                    {
                        if (!packed_.isCurve(pointer) || packed_.getDepth(pointer) != z || packed_
                                .getCurveIndex(pointer) != w)
                        {
                            throw new IllegalArgumentException("Impossible.");
                        }

                        curveParams[w] = packed_.getParameter(pointer++);
                    }

                    final int listIndex = base_.getEntryCurveOffset(i, z);
                    final ItemCurve<T> newCurve = factory.generateCurve(curveType, 0, curveParams);
                    trans.set(listIndex, newCurve);
                }
            }
        }

        if (pointer != base_.getEffectiveParamCount())
        {
            throw new IllegalArgumentException("Impossible: " + pointer + " != " + base_.getEffectiveParamCount());
        }

        _trans = Collections.unmodifiableList(trans);
    }


    /**
     * The constructor used to change betas, or add filters.
     *
     * @param base_
     * @param betas_
     * @param addedFilters_
     */
    private ItemParameters(final ItemParameters<S, R, T> base_, final double[][] betas_,
                           final Collection<ParamFilter<S, R, T>> addedFilters_)
    {
        _status = base_._status;
        _selfIndex = base_._selfIndex;
        _trans = base_._trans;
        _uniqueFields = base_._uniqueFields;
        _fieldOffsets = base_._fieldOffsets;
        _transOffsets = base_._transOffsets;
        _uniqueBeta = base_._uniqueBeta;

        _regFamily = base_._regFamily;
        _typeFamily = base_._typeFamily;

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

        _effectiveParamCount = calculateEffectiveParamCount();
    }

    /**
     * Used to make a new set of parameters with a new entry.
     *
     * @param base_
     */
    private ItemParameters(final ItemParameters<S, R, T> base_, final ItemCurveParams<R, T> curveParams_,
                           final S toStatus_)
    {
        _status = base_._status;
        _selfIndex = base_._selfIndex;
        _filters = base_._filters;
        _regFamily = base_._regFamily;
        _typeFamily = base_._typeFamily;

        final SortedSet<R> newFields = new TreeSet<>();
        newFields.addAll(base_._uniqueFields);
        newFields.addAll(curveParams_.getRegressors());

        //Always add the new entry to the end...
        final int baseEntryCount = base_.getEntryCount();
        final int newEntryCount = baseEntryCount + 1;
        final int endIndex = baseEntryCount;

        _uniqueFields = Collections.unmodifiableList(new ArrayList<>(newFields));

        //Just add the new entry to the end of the list.
        _uniqueBeta = Arrays.copyOf(base_._uniqueBeta, newEntryCount);

        final int toIndex;

        if (null == toStatus_)
        {
            toIndex = -1;
        }
        else
        {
            toIndex = getToIndex(toStatus_);
        }

        _uniqueBeta[endIndex] = toIndex;
        _fieldOffsets = new int[newEntryCount][];
        _transOffsets = new int[newEntryCount][];
        _betas = new double[base_._betas.length][];

        //Copy over prev betas, fill out with zeros. Will adjust again to change the betas...
        for (int i = 0; i < _betas.length; i++)
        {
            _betas[i] = Arrays.copyOf(base_._betas[i], newEntryCount);
        }

        //Careful about null curves.....
        final List<ItemCurve<T>> newTrans = new ArrayList<>();
        newTrans.add(null);

        _fieldOffsets[INTERCEPT_INDEX] = new int[1];
        _transOffsets[INTERCEPT_INDEX] = new int[1];
        _fieldOffsets[INTERCEPT_INDEX][0] = -1;
        _transOffsets[INTERCEPT_INDEX][0] = 0;
        _uniqueBeta[INTERCEPT_INDEX] = -1;

        //First, pull in all the old entries.
        for (int i = INTERCEPT_INDEX + 1; i < baseEntryCount; i++)
        {
            final int depth = base_.getEntryDepth(i);
            _fieldOffsets[i] = new int[depth];
            _transOffsets[i] = new int[depth];

            for (int w = 0; w < depth; w++)
            {
                final R field = base_.getEntryRegressor(i, w);
                final ItemCurve<T> curve = base_.getEntryCurve(i, w);
                final int transIndex;

                if (null == curve)
                {
                    transIndex = 0;
                }
                else
                {
                    transIndex = newTrans.size();
                    newTrans.add(curve);
                }


                _fieldOffsets[i][w] = _uniqueFields.indexOf(field);
                _transOffsets[i][w] = transIndex; //_trans.indexOf(curve);
            }
        }

        //Now fill out the last entry...
        final int endDepth = curveParams_.getEntryDepth();

        _fieldOffsets[endIndex] = new int[endDepth];
        _transOffsets[endIndex] = new int[endDepth];

        for (int i = 0; i < endDepth; i++)
        {
            final R field = curveParams_.getRegressor(i);
            final ItemCurve<T> curve = curveParams_.getCurve(i);
            final int transIndex;

            if (null == curve)
            {
                transIndex = 0;
            }
            else
            {
                transIndex = newTrans.size();
                newTrans.add(curve);
            }

            final int fieldIndex = _uniqueFields.indexOf(field);

            if (fieldIndex < 0)
            {
                throw new IllegalStateException("Impossible!");
            }

            _fieldOffsets[endIndex][i] = fieldIndex;
            _transOffsets[endIndex][i] = transIndex;
        }

        if (toIndex != -1)
        {
            //In this case, we have a real curve, let's set the betas....
            _betas[toIndex][endIndex] = curveParams_.getBeta();
            _betas[toIndex][INTERCEPT_INDEX] += curveParams_.getIntercept();
        }

        _trans = Collections.unmodifiableList(new ArrayList<>(newTrans));
        _effectiveParamCount = calculateEffectiveParamCount();

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
        _selfIndex = base_._selfIndex;
        _filters = base_._filters;
        _regFamily = base_._regFamily;
        _typeFamily = base_._typeFamily;

        final int startSize = base_.getEntryCount();
        final boolean[] drop = new boolean[startSize];

        // We are open to the possibility that dropIndices_ is not well formed, 
        // and some indices occur multiple times.
        for (int i = 0; i < dropIndices_.length; i++)
        {
            drop[dropIndices_[i]] = true;
        }

        if (drop[INTERCEPT_INDEX])
        {
            throw new IllegalArgumentException("The intercept index cannot be dropped.");
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

        _fieldOffsets[INTERCEPT_INDEX] = new int[1];
        _transOffsets[INTERCEPT_INDEX] = new int[1];
        _fieldOffsets[INTERCEPT_INDEX][0] = -1;
        _transOffsets[INTERCEPT_INDEX][0] = 0;
        _uniqueBeta[INTERCEPT_INDEX] = -1;
        newTrans.add(null);

        int pointer = INTERCEPT_INDEX + 1;

        for (int i = INTERCEPT_INDEX + 1; i < startSize; i++)
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

        for (int i = 0; i < _betas.length; i++)
        {
            _betas[i][INTERCEPT_INDEX] = base_._betas[i][INTERCEPT_INDEX];
        }

        pointer = INTERCEPT_INDEX + 1;

        for (int i = INTERCEPT_INDEX + 1; i < startSize; i++)
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

        _effectiveParamCount = calculateEffectiveParamCount();
    }

    private int calculateEffectiveParamCount()
    {
        final int effectiveTransSize = this._status.getReachableCount() - 1;
        int paramCount = 0;

        for (int i = 0; i < this.getEntryCount(); i++)
        {
            final boolean isRestricted = (this.getEntryStatusRestrict(i) != null);

            if (isRestricted)
            {
                paramCount += 1;
            }
            else
            {
                paramCount += effectiveTransSize;
            }

            for (int z = 0; z < this.getEntryDepth(i); z++)
            {
                final ItemCurve<T> curve = this.getEntryCurve(i, z);

                if (null == curve)
                {
                    continue;
                }

                paramCount += curve.getCurveType().getParamCount();
            }
        }

        return paramCount;
    }

    public int getEffectiveParamCount()
    {
        return _effectiveParamCount;
    }

    public int getToIndex(final S toStatus_)
    {
        final int toIndex = _status.getReachable().indexOf(toStatus_);

        if (toIndex == -1)
        {
            throw new IllegalArgumentException("Not reachable status: " + toStatus_);
        }

        return toIndex;
    }

    public EnumFamily<T> getCurveFamily()
    {
        return _typeFamily;
    }

    public EnumFamily<R> getRegressorFamily()
    {
        return _regFamily;
    }

    public int getInterceptIndex()
    {
        return INTERCEPT_INDEX;
    }

    public int getEntryCount()
    {
        return _fieldOffsets.length;
    }

    public int getEntryDepth(final int entryIndex_)
    {
        return _fieldOffsets[entryIndex_].length;
    }

    public S getEntryStatusRestrict(final int entryIndex_)
    {
        final int uniqueBeta = _uniqueBeta[entryIndex_];

        if (uniqueBeta == -1)
        {
            return null;
        }

        return this._status.getReachable().get(uniqueBeta);
    }

    public double getEntryRegressorValue(final int entryIndex_, final int entryDepth_, final double[] x_)
    {
        if (entryIndex_ == INTERCEPT_INDEX)
        {
            return 1.0;
        }

        final int offset = getEntryRegressorOffset(entryIndex_, entryDepth_);
        return x_[offset];
    }

    private int getEntryRegressorOffset(final int entryIndex_, final int entryDepth_)
    {
        return _fieldOffsets[entryIndex_][entryDepth_];
    }

    public R getEntryRegressor(final int entryIndex_, final int entryDepth_)
    {
        final int offset = getEntryRegressorOffset(entryIndex_, entryDepth_);
        return _uniqueFields.get(offset);
    }

    /**
     * returns the set of regressors that do not have curves.
     *
     * @return
     */
    public SortedSet<R> getFlagSet()
    {
        final SortedSet<R> output = new TreeSet<>();

        for (int i = INTERCEPT_INDEX + 1; i < this.getEntryCount(); i++)
        {
            if (this.getEntryDepth(i) != 1)
            {
                continue;
            }
            if (this.getEntryCurve(i, 0) != null)
            {
                continue;
            }

            output.add(this.getEntryRegressor(i, 0));
        }

        return Collections.unmodifiableSortedSet(output);
    }


    public int getEntryCurveOffset(final int entryIndex_, final int entryDepth_)
    {
        return _transOffsets[entryIndex_][entryDepth_];
    }

    /**
     * This function will find the entry corresponding to the given curve
     * parameters.
     * <p>
     * HOWEVER, it is necessary for the entry to match exactly, meaning many of
     * the constituent objects must be the exact same object. This is most
     * useful for (for instance) finding the entry of some ItemCurveParams that
     * were just used a moment ago to expand these parameters.
     *
     * @param params_ The ItemCurveParams to look for.
     * @return The entryIndex that would return equivalent params in response to
     * getEntryCurveParams(index), -1 if no such entry.
     */
    public int getEntryIndex(final ItemCurveParams<R, T> params_)
    {
        final int entryCount = this.getEntryCount();
        final int testDepth = params_.getEntryDepth();

        outer:
        for (int i = 0; i < entryCount; i++)
        {
            final int depth = this.getEntryDepth(i);

            if (depth != testDepth)
            {
                continue;
            }

            for (int w = 0; w < depth; w++)
            {
                if (this.getEntryCurve(i, w) != params_.getCurve(w))
                {
                    continue outer;
                }
                if (this.getEntryRegressor(i, w) != params_.getRegressor(w))
                {
                    continue outer;
                }

                //Do I verify the value of beta as well? No, I think I can safely skip that.
            }

            //We made it, this is the entry you're looking for.
            return i;
        }

        return -1;
    }

    public ItemCurveParams<R, T> getEntryCurveParams(final int entryIndex_)
    {
        return getEntryCurveParams(entryIndex_, false);
    }

    public ItemCurveParams<R, T> getEntryCurveParams(final int entryIndex_, final boolean allowNonCurve_)
    {
        final int toIndex = _uniqueBeta[entryIndex_];

        if (toIndex == -1 && !allowNonCurve_)
        {
            throw new IllegalArgumentException("Can only extract curve params for actual curves.");
        }

        final int depth = getEntryDepth(entryIndex_);
        final List<R> regs = new ArrayList<>(depth);
        final List<ItemCurve<T>> curves = new ArrayList<>(depth);

        for (int i = 0; i < depth; i++)
        {
            final R reg = this.getEntryRegressor(entryIndex_, i);
            final ItemCurve<T> curve = this.getEntryCurve(entryIndex_, i);
            regs.add(reg);
            curves.add(curve);
        }

        final double interceptAdjustment = 0.0;
        final double beta;

        if (toIndex != -1)
        {
            beta = _betas[toIndex][entryIndex_];
        }
        else
        {
            beta = 0.0;
        }

        final ItemCurveParams<R, T> output = new ItemCurveParams<>(interceptAdjustment, beta, regs, curves);
        return output;
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

    public boolean betaIsFrozen(S toStatus_, int paramEntry_)
    {
        if (_uniqFilter.betaIsFrozen(this, toStatus_, paramEntry_))
        {
            return true;
        }

        for (final ParamFilter<S, R, T> next : getFilters())
        {
            if (next.betaIsFrozen(this, toStatus_, paramEntry_))
            {
                return true;
            }
        }

        return false;
    }

    public boolean curveIsForbidden(S toStatus_, ItemCurveParams<R, T> curveParams_)
    {
        if (_uniqFilter.curveIsForbidden(this, toStatus_, curveParams_))
        {
            return true;
        }

        for (final ParamFilter<S, R, T> next : getFilters())
        {
            if (next.curveIsForbidden(this, toStatus_, curveParams_))
            {
                return true;
            }
        }

        return false;
    }

    private List<ParamFilter<S, R, T>> getFilters()
    {
        return _filters;
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

        return dropEntries(keep);
    }

    public ItemParameters<S, R, T> dropIndex(final int index_)
    {
        final int regCount = this.getEntryCount();
        final boolean[] keep = new boolean[regCount];
        Arrays.fill(keep, true);
        keep[index_] = false;

        return dropEntries(keep);
    }

    private ItemParameters<S, R, T> dropEntries(final boolean[] keep_)
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
     * Adds an empty beta entry, with just the given field.
     * <p>
     * Note, if these parameters already contain a raw flag for this beta, then
     * this is returned unchanged.
     *
     * @param regressor_
     * @return
     */
    public ItemParameters<S, R, T> addBeta(final R regressor_)
    {
        for (int i = 0; i < this.getEntryCount(); i++)
        {
            if (this.getEntryDepth(i) != 1)
            {
                continue;
            }

            if (this.getEntryRegressor(i, 0) == regressor_)
            {
                //Already have a flag for this regressor, return unchanged.
                return this;
            }
        }

        final ItemCurveParams<R, T> fieldParams = new ItemCurveParams<>(0.0, 0.0, regressor_, null);
        return new ItemParameters<>(this, fieldParams, null);
    }

    /**
     * Creates a new set of parameters with an additional beta.
     *
     * @param curveParams_
     * @param toStatus_
     * @return A new set of parameters, with the given curve added and its beta
     * set to zero.
     */
    public ItemParameters<S, R, T> addBeta(final ItemCurveParams<R, T> curveParams_, final S toStatus_)
    {
        if (null == toStatus_)
        {
            for (final ItemCurve<T> curve : curveParams_.getCurves())
            {
                if (null != curve)
                {
                    //This is OK as long as none of the curves are set (in which case, it's just a list of flags...)
                    throw new NullPointerException("To status cannot be null for non-flag curves.");
                }
            }
        }
        if (toStatus_ == this._status)
        {
            throw new NullPointerException("Beta for from status must be zero.");
        }

        return new ItemParameters<>(this, curveParams_, toStatus_);
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

            builder.append("\t\t\t Entry Definition[" + i + ", depth=" + depth + "]: \n");

            for (int w = 0; w < this.getEntryDepth(i); w++)
            {

                final ItemCurve<T> curve = this.getEntryCurve(i, w);

                if (i == INTERCEPT_INDEX)
                {
                    builder.append("\t\t\t[" + i + ", " + w + "]:INTERCEPT:" + curve + "\n");
                }
                else
                {
                    final R reg = this.getEntryRegressor(i, w);
                    builder.append("\t\t\t[" + i + ", " + w + "]:" + reg + ":" + curve + "\n");
                }
            }

            builder.append("\t\t\t Entry Beta Restricted: " + _uniqueBeta[i] + "\n");
        }

        builder.append("]");

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

    public PackedParameters<S, R, T> generatePacked()
    {
        return new ItemParametersVector();
    }

    public void writeToStream(final OutputStream stream_) throws IOException
    {
        try (final GZIPOutputStream zipout = new GZIPOutputStream(stream_);
             final ObjectOutputStream oOut = new ObjectOutputStream(zipout))
        {
            oOut.writeObject(this);
            oOut.flush();
        }
    }

    public static <S2 extends ItemStatus<S2>, R2 extends ItemRegressor<R2>, T2 extends ItemCurveType<T2>>
    ItemParameters<S2, R2, T2> readFromStream(final InputStream stream_,
                                              final Class<S2> statusClass_, final Class<R2> regClass_,
                                              final Class<T2> typeClass_)
            throws IOException
    {
        try (final GZIPInputStream zipin = new GZIPInputStream(stream_);
             final ObjectInputStream oIn = new ObjectInputStream(zipin))
        {
            final ItemParameters<?, ?, ?> raw = (ItemParameters<?, ?, ?>) oIn.readObject();

            if (raw.getStatus().getClass() != statusClass_)
            {
                throw new IOException("Status class mismatch.");
            }
            if (raw._regFamily.getComponentType() != regClass_)
            {
                throw new IOException("Regressor class mismatch.");
            }
            if (raw._typeFamily.getComponentType() != typeClass_)
            {
                throw new IOException("Curve Type class mismatch.");
            }

            final ItemParameters<S2, R2, T2> typed = (ItemParameters<S2, R2, T2>) raw;
            return typed;
        }
        catch (ClassNotFoundException e)
        {
            throw new IOException(e);
        }
    }


    private final class UniqueBetaFilter implements ParamFilter<S, R, T>
    {
        private static final long serialVersionUID = 0x49e89a36e4553a69L;

        @Override
        public boolean betaIsFrozen(ItemParameters<S, R, T> params_, S toStatus_, int paramEntry_)
        {
            if (params_.getStatus() == toStatus_)
            {
                return true;
            }

            final S restrict = params_.getEntryStatusRestrict(paramEntry_);

            if (null == restrict)
            {
                return false;
            }

            if (restrict != toStatus_)
            {
                return true;
            }

            return false;
        }

        @Override
        public boolean curveIsForbidden(ItemParameters<S, R, T> params_, S toStatus_,
                                        ItemCurveParams<R, T> curveParams_)
        {
            //We don't forbid any new additions, except where fromStatus_ == toStatus_
            return (params_.getStatus() == toStatus_);
        }

    }

    private final class ItemParametersVector implements PackedParameters<S, R, T>
    {
        private static final long serialVersionUID = 0x72174035ac14e56L;

        private final double[] _paramValues;
        private final int[] _toStatus;
        private final int[] _entryIndex;
        private final int[] _curveDepth;
        private final int[] _curveIndex;
        private final int[] _betaIndex;
        private final boolean[] _betaIsFrozen;

        private ItemParameters<S, R, T> _generated = null;

        public ItemParametersVector(final ItemParametersVector vec_)
        {
            _paramValues = vec_._paramValues.clone();
            _toStatus = vec_._toStatus;
            _entryIndex = vec_._entryIndex;
            _curveDepth = vec_._curveDepth;
            _curveIndex = vec_._curveIndex;
            _betaIndex = vec_._betaIndex;
            _betaIsFrozen = vec_._betaIsFrozen;
        }

        public ItemParametersVector()
        {
            final int paramCount = ItemParameters.this.getEffectiveParamCount();

            _paramValues = new double[paramCount];
            _toStatus = new int[paramCount];
            _entryIndex = new int[paramCount];
            _curveDepth = new int[paramCount];
            _curveIndex = new int[paramCount];
            _betaIndex = new int[paramCount];
            _betaIsFrozen = new boolean[paramCount];

            int pointer = 0;

            final S fromStatus = ItemParameters.this.getStatus();
            final List<S> reachable = fromStatus.getReachable();

            for (final S next : reachable)
            {
                for (int i = 0; i < ItemParameters.this.getEntryCount(); i++)
                {
                    final S statusRestrict = ItemParameters.this.getEntryStatusRestrict(i);

                    if (statusRestrict == null)
                    {
                        if (next == fromStatus)
                        {
                            continue;
                        }

                        pointer = fillBeta(next, i, pointer);
                    }
                    else
                    {
                        if (next != statusRestrict)
                        {
                            continue;
                        }

                        pointer = fillBeta(statusRestrict, i, pointer);
                        final int toIndex = ItemParameters.this.getToIndex(statusRestrict);

                        for (int z = 0; z < ItemParameters.this.getEntryDepth(i); z++)
                        {
                            final ItemCurve<T> curve = ItemParameters.this.getEntryCurve(i, z);

                            if (null == curve)
                            {
                                continue;
                            }

                            final int curveParamCount = curve.getCurveType().getParamCount();


                            for (int w = 0; w < curveParamCount; w++)
                            {
                                final double curveParam = curve.getParam(w);
                                pointer = fillOne(curveParam, toIndex, i, z, w, pointer);
                            }
                        }
                    }
                }
            }

            if (pointer != paramCount)
            {
                throw new IllegalArgumentException("Impossible: " + pointer + " != " + paramCount);
            }

            // Basically we are just getting the parameters that represent the actual betas.
            for (int i = 0; i < paramCount; i++)
            {
                if (this._curveDepth[i] < 0)
                {
                    _betaIndex[i] = i;
                }
                else
                {
                    _betaIndex[i] = _betaIndex[i - 1];
                }
            }
        }

        private int fillBeta(final S toStatus_, final int entryIndex_, final int pointer_)
        {
            final int toIndex = ItemParameters.this.getToIndex(toStatus_);
            _betaIsFrozen[pointer_] = ItemParameters.this.betaIsFrozen(toStatus_, entryIndex_);
            return fillOne(ItemParameters.this.getBeta(toIndex, entryIndex_), toIndex, entryIndex_, -1, -1, pointer_);

        }

        private int fillOne(final double val_, final int toStatus_, final int entryIndex_, final int curveDepth_,
                            final int curveIndex_, final int pointer_)
        {
            _paramValues[pointer_] = val_;
            _toStatus[pointer_] = toStatus_;
            _entryIndex[pointer_] = entryIndex_;
            _curveDepth[pointer_] = curveDepth_;
            _curveIndex[pointer_] = curveIndex_;
            return pointer_ + 1;
        }

        @Override
        public int size()
        {
            return _paramValues.length;
        }

        @Override
        public double[] getPacked()
        {
            return _paramValues.clone();
        }

        @Override
        public synchronized void updatePacked(double[] newParams_)
        {
            if (newParams_.length != _paramValues.length)
            {
                throw new IllegalArgumentException("Params wrong length.");
            }

            System.arraycopy(newParams_, 0, _paramValues, 0, _paramValues.length);
            _generated = null;
        }

        @Override
        public double getParameter(int index_)
        {
            return _paramValues[index_];
        }

        @Override
        public double getEntryBeta(int index_)
        {
            return _paramValues[_betaIndex[index_]];
        }

        @Override
        public synchronized void setParameter(int index_, double value_)
        {
            if (_paramValues[index_] != value_)
            {
                // We only reset the generated params if a parameter actually changed.
                _generated = null;
            }

            _paramValues[index_] = value_;
        }

        @Override
        public boolean isBeta(int index_)
        {
            return _curveIndex[index_] == -1;
        }

        @Override
        public boolean betaIsFrozen(int index_)
        {
            return _betaIsFrozen[index_];
        }

        @Override
        public boolean isCurve(int index_)
        {
            return !isBeta(index_);
        }

        @Override
        public int getTransition(int index_)
        {
            return _toStatus[index_];
        }

        public int findBetaIndex(final int toStatus_, final int entryIndex_)
        {
            for (int i = 0; i < _entryIndex.length; i++)
            {
                if (_entryIndex[i] != entryIndex_)
                {
                    continue;
                }

                if (_toStatus[i] != toStatus_)
                {
                    continue;
                }

                return _betaIndex[i];
            }

            return -1;
        }

        @Override
        public int getEntry(int index_)
        {
            return _entryIndex[index_];
        }

        @Override
        public int getDepth(int index_)
        {
            return _curveDepth[index_];
        }

        @Override
        public int getCurveIndex(int index_)
        {
            return _curveIndex[index_];
        }

        @Override
        public synchronized ItemParameters<S, R, T> generateParams()
        {
            if (null != _generated)
            {
                return _generated;
            }

            _generated = new ItemParameters(ItemParameters.this, this);
            return _generated;
        }

        @Override
        public ItemParameters<S, R, T> getOriginalParams()
        {
            return ItemParameters.this;
        }

        public PackedParameters<S, R, T> clone()
        {
            return new ItemParametersVector(this);
        }
    }
}
