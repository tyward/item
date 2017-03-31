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

import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.data.RandomizedStatusGrid;
import edu.columbia.tjw.item.fit.curve.CurveFitter;
import edu.columbia.tjw.item.fit.curve.CurveFitResult;
import edu.columbia.tjw.item.fit.param.ParamFitResult;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * A class designed to expand the model by adding curves.
 *
 * In addition, it may be used to fit only coefficients if needed.
 *
 *
 * @author tyler
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 */
public final class ItemFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(ItemFitter.class);

    private final ItemCurveFactory<R, T> _factory;
    private final ItemSettings _settings;
    private final R _intercept;
    private final EnumFamily<R> _family;
    private final S _status;
    private final ItemStatusGrid<S, R> _grid;

    private FittingProgressChain<S, R, T> _chain;

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_, final S status_, final ItemStatusGrid<S, R> grid_)
    {
        this(factory_, intercept_, status_, grid_, new ItemSettings());
    }

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_, final S status_, final ItemStatusGrid<S, R> grid_, ItemSettings settings_)
    {
        if (null == factory_)
        {
            throw new NullPointerException("Factory cannot be null.");
        }
        if (null == intercept_)
        {
            throw new NullPointerException("Intercept cannot be null.");
        }
        if (null == settings_)
        {
            throw new NullPointerException("Settings cannot be null.");
        }
        if (null == status_)
        {
            throw new NullPointerException("Status cannot be null.");
        }
        if (null == grid_)
        {
            throw new NullPointerException("Grid cannot be null.");
        }

        _factory = factory_;
        _settings = settings_;
        _intercept = intercept_;
        _family = intercept_.getFamily();
        _status = status_;
        _grid = randomizeGrid(grid_, _settings);

        final ItemParameters<S, R, T> starting = new ItemParameters<>(status_, _intercept);
        final double logLikelihood = this.computeLogLikelihood(starting);

        _chain = new FittingProgressChain<>(starting, logLikelihood, _grid.size());
    }

    public S getStatus()
    {
        return _status;
    }

    public double getBestLogLikelihood()
    {
        return _chain.getLogLikelihood();
    }

    public ItemParameters<S, R, T> getBestParameters()
    {
        return _chain.getBestParameters();
    }

    /**
     * This function will wrap the provided grid factory. The goal here is to
     * handle the randomization needed for accurate calculation, and also to
     * cache some data for efficiency.
     *
     * It is strongly recommended that all grid factories are wrapped before
     * use.
     *
     * N.B: The wrapped grid may cache, so if the underlying regressors are
     * changed, the resulting factory should be wrapped again.
     *
     * @param <S>
     * @param <R>
     * @param grid_ The grid to randomize
     * @return A randomized version of grid_
     */
    public static <S extends ItemStatus<S>, R extends ItemRegressor<R>> ItemStatusGrid<S, R> randomizeGrid(final ItemStatusGrid<S, R> grid_, final ItemSettings settings_)
    {
        if (grid_ instanceof RandomizedStatusGrid)
        {
            return grid_;
        }

        final ItemStatusGrid<S, R> wrapped = new RandomizedStatusGrid<>(grid_, settings_, grid_.getRegressorFamily());
        return wrapped;
    }

    /**
     * Add a group of coefficients to the model, then refit all coefficients.
     *
     * @param filters_ Any filters that should be used to limit the allowed
     * coefficients, else null
     * @param coefficients_ The set of coefficients to fit.
     * @return A model fit with all the additional allowed coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    public ParamFitResult<S, R, T> addCoefficients(final Collection<ParamFilter<S, R, T>> filters_,
            final Collection<R> coefficients_) throws ConvergenceException
    {
        ItemParameters<S, R, T> params = _chain.getBestParameters();

        for (final R field : coefficients_)
        {
            params = params.addBeta(field);
        }

        final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(params, filters_);

        if (fitResult.getEndingParams().getEffectiveParamCount() != params.getEffectiveParamCount())
        {
            //We weren't able to make any progress optimizing these values, push on zeros anyway.
            _chain.pushVacuousResults(params);
        }

        return _chain.getLatestResults();
    }

    /**
     *
     * Optimize the coefficients.
     *
     * @param filters_ Filters describing any coefficients that should not be
     * adjusted
     * @return A model with newly optimized coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    public ParamFitResult<S, R, T> fitCoefficients(final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        return innerFitCoefficients(_chain.getBestParameters(), filters_);
    }

    public ParamFitResult<S, R, T> fitCoefficients(final ItemParameters<S, R, T> params_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        return innerFitCoefficients(params_, filters_);
    }

    private ParamFitResult<S, R, T> innerFitCoefficients(final ItemParameters<S, R, T> params_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(params_, _grid, _settings, filters_);
        final ParamFitResult<S, R, T> fitResult = fitter.fit();
        _chain.pushResults(fitResult);
        return _chain.getLatestResults();
    }

    public ParamFitResult<S, R, T> runAnnealingPass(final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final int regCount = _chain.getBestParameters().getEntryCount();

        final FittingProgressChain<S, R, T> subChain = new FittingProgressChain<>(_chain);

        for (final R regressor : curveFields_)
        {
            final ItemParameters<S, R, T> base = subChain.getBestParameters();
            final ItemParameters<S, R, T> reduced = base.dropRegressor(regressor);
            final int reducedCount = reduced.getEntryCount();
            final int reduction = regCount - reducedCount;

            if (reduction <= 0)
            {
                continue;
            }

            LOG.info("Annealing attempting to drop " + reduction + " curves from " + regressor + ", now rebuilding");

            final ParamFitResult<S, R, T> rebuilt = expandModel(reduced, subChain.getLogLikelihood(), curveFields_, filters_, reduction);
            final boolean better = subChain.pushResults(rebuilt);

            if (better)
            {
                LOG.info("Annealing improved model: " + rebuilt.getStartingLL() + " -> " + rebuilt.getEndingLL() + " (" + rebuilt.getAic() + ")");
            }
            else
            {
                LOG.info("Annealing did not improve model, keeping old model");
            }

            //TODO: Check this for improvement?
            LOG.info("---->Finished rebuild after dropping regressor: " + regressor);
        }

        final ParamFitResult<S, R, T> consolidated = subChain.getConsolidatedResults();
        _chain.pushResults(consolidated);
        return consolidated;
    }

    public double computeLogLikelihood(final ItemParameters<S, R, T> params_)
    {
        //final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, _grid);
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(params_, _grid, _settings, null);
        final double ll = fitter.computeLogLikelihood(params_);
        return ll;
    }

//    public ParamFitResult<S, R, T> generateFlagInteractions(final int entryNumber_)
//    {
//        final CurveFitter<S, R, T> fitter = new CurveFitter<>(_factory, _settings, _grid, _chain.getBestParameters());
//        final CurveFitResult<S, R, T> result = fitter.generateFlagInteractions(_chain.getLogLikelihood(), interactionCount_);
//
//        if (null != result)
//        {
//            _chain.pushResults(result.getModelParams(), result.getLogLikelihood());
//        }
//
//        return _chain.getLatestResults();
//    }
    /**
     * Add some new curves to this model.
     *
     * This function is the heart of the ITEM system, and uses most of the
     * computational resources.
     *
     * @param curveFields_ The regressors on which to draw curves
     * @param filters_ Filters describing any curves that should not be drawn or
     * optimized
     * @param paramCount_ The total number of additional params that will be
     * allowed.
     * @return A new model with additional curves added, and all coefficients
     * optimized.
     */
    public ParamFitResult<S, R, T> expandModel(final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_, final int paramCount_)
    {
        if (paramCount_ < 1)
        {
            throw new IllegalArgumentException("Param count must be positive.");
        }

        final ParamFitResult<S, R, T> result = expandModel(_chain.getBestParameters(), _chain.getLogLikelihood(), curveFields_, filters_, paramCount_);
        _chain.pushResults(result);
        return _chain.getLatestResults();
    }

    private ParamFitResult<S, R, T> expandModel(final ItemParameters<S, R, T> params_, final double startingLL_, final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_, final int paramCount_)
    {
        final long start = System.currentTimeMillis();
        final FittingProgressChain<S, R, T> subChain = new FittingProgressChain<>(params_, startingLL_, _grid.size());

        final int statingParamCount = subChain.getBestParameters().getEffectiveParamCount();
        final int paramCountLimit = statingParamCount + paramCount_;
        double improvement = 0.0;

        //As a bare minimum, each expansion will consume at least one param, we'll break out before this most likely.
        for (int i = 0; i < paramCount_; i++)
        {
            try
            {
                final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(subChain.getBestParameters(), filters_);
                final boolean isBetter = subChain.pushResults(fitResult);

                if (!isBetter)
                {
                    LOG.warning("LL got worse: " + subChain.getLogLikelihood() + " -> " + fitResult.getEndingLL());
                    LOG.warning("Current parameters: " + fitResult.getEndingParams());
                }
            }
            catch (final ConvergenceException e)
            {
                LOG.warning("Unable to improve results in coefficient fit, moving on.");
            }

            final CurveFitter<S, R, T> fitter = new CurveFitter<>(_factory, _settings, _grid, subChain.getBestParameters());

            //First, try to calibrate any existing curves to improve the fit. 
            final ItemParameters<S, R, T> m3 = fitter.calibrateCurves(improvement);
            final double test = computeLogLikelihood(m3);
            final boolean isBetter = subChain.pushResults(m3, test);

            if (!isBetter)
            {
                LOG.info("Curve calibration unable to improve results: " + subChain.getLogLikelihood() + " -> " + test);
            }

            try
            {
                //Now, try to add a new curve. 
                final CurveFitter<S, R, T> fitter2 = new CurveFitter<>(_factory, _settings, _grid, subChain.getBestParameters());
                final CurveFitResult<S, R, T> m4 = fitter2.generateCurve(curveFields_, filters_);

                improvement = Math.max(0.0, m4.getStartingLogLikelihood() - m4.getLogLikelihood());
                final boolean isBetter2 = subChain.pushResults(m4);

                if (!isBetter2)
                {
                    LOG.info("Curve expansion unable to improve results, breaking out.");
                    break;
                }

                if (m4.getModelParams().getEffectiveParamCount() >= paramCountLimit)
                {
                    LOG.info("Param count limit reached, breaking out.");
                    break;
                }

            }
            catch (final ConvergenceException e)
            {
                LOG.info("Unable to make progress, breaking out.");
                break;
            }
            finally
            {
                LOG.info("Completed one round of curve drawing, moving on.");
                LOG.info("Time marker: " + (System.currentTimeMillis() - start));
                LOG.info("Heap used: " + Runtime.getRuntime().totalMemory() / (1024 * 1024));
            }
        }

        try
        {
            final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(subChain.getBestParameters(), filters_);
            subChain.pushResults(fitResult);
        }
        catch (final ConvergenceException e)
        {
            LOG.info("Unable to improve results in coefficient fit, moving on.");
        }

        return subChain.getConsolidatedResults();
    }

}
