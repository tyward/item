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
package edu.columbia.tjw.item.fit.param;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.fit.*;
import edu.columbia.tjw.item.fit.base.BaseFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.logging.Logger;

/**
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 * @author tyler
 */
public final class ParamFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(ParamFitter.class);

    private final BaseFitter<S, R, T> _base;

    public ParamFitter(final BaseFitter<S, R, T> base_)
    {
        if (null == base_)
        {
            throw new NullPointerException("Base cannot be null.");
        }

        _base = base_;
    }

    public ParamFitter(final EntropyCalculator<S, R, T> calc_, final ItemSettings settings_)
    {
        this(new BaseFitter<>(calc_, settings_));
    }

    public FitResult<S, R, T> fit(final FittingProgressChain<S, R, T> chain_) throws ConvergenceException
    {
        return fit(chain_, chain_.getBestParameters());
    }

    public FitResult<S, R, T> fit(final FittingProgressChain<S, R, T> chain_, ItemParameters<S, R, T> params_)
    {
        final FitResult<S, R, T> prev = chain_.getLatestResults();
        final FitResult<S, R, T> fitResult = fitBetas(prev.getParams(), prev); //_base.doFit(packed, chain_
        chain_.pushResults("ParamFit", fitResult);
        return fitResult;
    }


    public FitResult<S, R, T> fitBetas(final ItemParameters<S, R, T> params_, final FitResult<S, R, T> prev_)
    {
        final PackedParameters<S, R, T> packed = packParameters(params_);
        final FitResult<S, R, T> fitResult = _base.doFit(packed, prev_);
        return fitResult;
    }

    private PackedParameters<S, R, T> packParameters(final ItemParameters<S, R, T> params_)
    {
        final PackedParameters<S, R, T> packed = params_.generatePacked();
        final boolean[] active = new boolean[params_.getEffectiveParamCount()];

        for (int i = 0; i < active.length; i++)
        {
            if (!packed.isBeta(i))
            {
                continue;
            }
            if (packed.betaIsFrozen(i))
            {
                continue;
            }

            active[i] = true;
        }

        final PackedParameters<S, R, T> reduced = new ReducedParameterVector<>(active, packed);
        return reduced;
    }


}
