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
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
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

    private final MultivariateOptimizer _optimizer;
    private final ItemSettings _settings;
    private final EntropyCalculator<S, R, T> _calc;

//    ItemParameters<S, R, T> _cacheParams;
//    LogisticModelFunction<S, R, T> _cacheFunction;

    public ParamFitter(final EntropyCalculator<S, R, T> calc_, final ItemSettings settings_)
    {
        _calc = calc_;
        _optimizer = new MultivariateOptimizer(settings_.getBlockSize(), 300, 20, 0.1);
        _settings = settings_;
    }

    public FitResult<S, R, T> fit(final FittingProgressChain<S, R, T> chain_) throws ConvergenceException
    {
        return fit(chain_, chain_.getBestParameters());
    }

    public FitResult<S, R, T> fit(final FittingProgressChain<S, R, T> chain_, ItemParameters<S, R, T> params_)
            throws ConvergenceException
    {
        final double entropy = chain_.getLogLikelihood();
        final LogisticModelFunction<S, R, T> function = generateFunction(params_);
        final double[] beta = function.getBeta();
        final MultivariatePoint point = new MultivariatePoint(beta);
        final int numRows = function.numRows();

        final OptimizationResult<MultivariatePoint> result = _optimizer.optimize(function, point);
        final MultivariatePoint optimumPoint = result.getOptimum();

        for (int i = 0; i < beta.length; i++)
        {
            beta[i] = optimumPoint.getElement(i);
        }

        final double newLL = result.minValue();
        LOG.info("Fitting coefficients, LL improvement: " + entropy + " -> " + newLL + "(" + (newLL - entropy) + ")");

        if (!result.converged())
        {
            LOG.info("Exhausted dataset before convergence, moving on.");
        }

        final FitResult<S, R, T> output;

        if (newLL > entropy)
        {
            // Push a frame with no improvement.
            final FitResult<S, R, T> res = new FitResult<>(chain_.getLatestResults(),
                    chain_.getLatestResults());
            output = res;
            chain_.pushResults("ParamFit", res);
        }
        else
        {
            final ItemParameters<S, R, T> updated = function.generateParams(beta);
            final FitResult<S, R, T> fitResult = _calc
                    .computeFitResult(updated, chain_.getLatestResults());

            output = fitResult;
            chain_.pushResults("ParamFit", fitResult);
        }

        return output;
    }

    private LogisticModelFunction<S, R, T> generateFunction(final ItemParameters<S, R, T> params_)
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

        final LogisticModelFunction<S, R, T> function = new LogisticModelFunction<>(params_, _calc.getGrid(),
                new ItemModel<>(params_), _settings, reduced);
        return function;
    }

}
