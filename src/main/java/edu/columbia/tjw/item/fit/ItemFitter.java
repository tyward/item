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
import edu.columbia.tjw.item.util.MathFunctions;
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

    private ItemParameters<S, R, T> _currentBest;
    private double _bestLL;

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
        _grid = randomizeGrid(grid_);

        _currentBest = new ItemParameters<>(status_, _intercept);
        _bestLL = this.computeLogLikelihood(_currentBest);
    }

    public S getStatus()
    {
        return _status;
    }

    public double getBestLogLikelihood()
    {
        return _bestLL;
    }

    public ItemParameters<S, R, T> getBestParameters()
    {
        return _currentBest;
    }

//    public ItemParameters<S, R, T> generateInitialParameters(final S status_)
//    {
//        if (status_.getReachableCount() < 2)
//        {
//            throw new IllegalArgumentException("Only one reachable state, no need for a model.");
//        }
//
//        final ItemParameters<S, R, T> initial = new ItemParameters<>(status_, _intercept);
//        return initial;
//    }
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
     * @param grid_ The grid to randomize
     * @return A randomized version of grid_
     */
    private ItemStatusGrid<S, R> randomizeGrid(final ItemStatusGrid<S, R> grid_)
    {
        if (grid_ instanceof RandomizedStatusGrid)
        {
            return grid_;
        }

        final ItemStatusGrid<S, R> wrapped = new RandomizedStatusGrid<>(grid_, _settings, _family);
        return wrapped;
    }

    private ParamFitResult<S, R, T> considerResult(final ParamFitResult<S, R, T> result_)
    {
        //Restate this in terms of the current best, just to be sure...
        final ParamFitResult<S, R, T> result = new ParamFitResult<>(_currentBest, result_.getEndingParams(), result_.getEndingLL(), _bestLL, _grid.size());

        if (result_.isBetter() && MathFunctions.isAicBetter(_bestLL, result_.getEndingLL()))
        {
            LOG.info("Fitter improved result: " + result.getStartingLL() + " -> " + result.getEndingLL() + " (" + result.getAic() + ")s");
            _bestLL = result_.getEndingLL();
            _currentBest = result_.getEndingParams();
        }
        else
        {
            LOG.info("Fitter unable to improve result: " + result.getStartingLL());
        }

        return result;
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
        ItemParameters<S, R, T> params = _currentBest;

        for (final R field : coefficients_)
        {
            params = params.addBeta(field);
        }

        final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(params, filters_);
        return considerResult(fitResult);
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
        final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(_currentBest, filters_);
        return considerResult(fitResult);
    }

    public ParamFitResult<S, R, T> fitCoefficients(final ItemParameters<S, R, T> params_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(params_, filters_);
        return considerResult(fitResult);
    }

    private ParamFitResult<S, R, T> innerFitCoefficients(final ItemParameters<S, R, T> params_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(params_, _grid, _settings, filters_);
        final ParamFitResult<S, R, T> fitResult = fitter.fit();

        if (fitResult.isBetter())
        {
            _currentBest = fitResult.getEndingParams();
            _bestLL = fitResult.getEndingLL();
        }

        return fitResult;
    }

    public ParamFitResult<S, R, T> runAnnealingPass(final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final int regCount = _currentBest.getEntryCount();

        ItemParameters<S, R, T> base = _currentBest;
        double logLikelihood = _bestLL;

        for (final R regressor : curveFields_)
        {
            final ItemParameters<S, R, T> reduced = base.dropRegressor(regressor);
            final int reducedCount = reduced.getEntryCount();
            final int reduction = regCount - reducedCount;

            if (reduction <= 0)
            {
                continue;
            }

            LOG.info("Annealing attempting to drop " + reduction + " curves from " + regressor + ", now rebuilding");

            final ParamFitResult<S, R, T> rebuilt = expandModel(reduced, logLikelihood, curveFields_, filters_, reduction);
            //final ParamFitResult<S, R, T> merged = considerResult(rebuilt);

            if (rebuilt.isBetter())
            {
                LOG.info("Annealing improved model: " + rebuilt.getStartingLL() + " -> " + rebuilt.getEndingLL() + " (" + rebuilt.getAic() + ")");
                base = rebuilt.getEndingParams();
                logLikelihood = rebuilt.getEndingLL();
            }
            else
            {
                LOG.info("Annealing did not improve model, keeping old model");
            }

            //TODO: Check this for improvement?
            LOG.info("---->Finished rebuild after dropping regressor: " + regressor);
        }

        final ParamFitResult<S, R, T> improvement = new ParamFitResult<>(_currentBest, base, logLikelihood, _bestLL, _grid.size());

        return considerResult(improvement);
    }

    private double computeLogLikelihood(final ItemParameters<S, R, T> params_)
    {
        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, _grid);
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(params_, grid, _settings, null);
        final double ll = fitter.computeLogLikelihood(params_);
        return ll;
    }

    public ParamFitResult<S, R, T> generateFlagInteractions(final int interactionCount_)
    {
        final CurveFitter<S, R, T> fitter = new CurveFitter<>(_factory, _settings, _grid, _currentBest);
        final CurveFitResult<S, R, T> result = fitter.generateFlagInteraction(_bestLL, interactionCount_);

        if (null == result)
        {
            return new ParamFitResult<>(_currentBest, _currentBest, _bestLL, _bestLL, _grid.size());
        }

        final ParamFitResult<S, R, T> output = new ParamFitResult<>(_currentBest, result.getModelParams(), result.getLogLikelihood(), _bestLL, _grid.size());
        return considerResult(output);
    }

    /**
     * Add some new curves to this model.
     *
     * This function is the heart of the ITEM system, and uses most of the
     * computational resources.
     *
     * @param curveFields_ The regressors on which to draw curves
     * @param filters_ Filters describing any curves that should not be drawn or
     * optimized
     * @param curveCount_ The total number of curves that will be allowed.
     * @return A new model with additional curves added, and all coefficients
     * optimized.
     */
    public ParamFitResult<S, R, T> expandModel(final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_, final int curveCount_)
    {
        final ParamFitResult<S, R, T> result = expandModel(_currentBest, _bestLL, curveFields_, filters_, curveCount_);
        return considerResult(result);
    }

    private ParamFitResult<S, R, T> expandModel(final ItemParameters<S, R, T> params_, final double startingLL_, final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_, final int curveCount_)
    {
        final long start = System.currentTimeMillis();
        ItemParameters<S, R, T> params = params_;

        double bestLL = computeLogLikelihood(params_);

        for (int i = 0; i < curveCount_; i++)
        {
            try
            {
                final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(params, filters_);

                if (fitResult.isWorse())
                {
                    LOG.info("LL got worse: " + bestLL + " -> " + fitResult.getEndingLL());
                    LOG.info("Previous parameters: " + params);
                    LOG.info("Current parameters: " + fitResult.getEndingParams());
                }
                else
                {
                    bestLL = fitResult.getEndingLL();
                    params = fitResult.getEndingParams();
                }
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Unable to improve results in coefficient fit, moving on.");
            }

            final CurveFitter<S, R, T> fitter = new CurveFitter<>(_factory, _settings, _grid, params);

            //First, try to calibrate any existing curves to improve the fit. 
            final ItemParameters<S, R, T> m3 = fitter.calibrateCurves();
            final double test = computeLogLikelihood(m3);

            if (MathFunctions.isAicWorse(bestLL, test))
            {
                LOG.info("LL got worse: " + bestLL + " -> " + test);
                LOG.info("Previous parameters: " + params);
                LOG.info("Current parameters: " + m3);
            }
            else
            {
                bestLL = test;
                params = m3;
            }

            try
            {
                //Now, try to add a new curve. 
                final ItemParameters<S, R, T> m4 = fitter.generateCurve(curveFields_, filters_);

                final double test2 = computeLogLikelihood(m4);

                if (MathFunctions.isAicWorse(bestLL, test2))
                {
                    LOG.info("LL got worse: " + bestLL + " -> " + test);
                    LOG.info("Previous parameters: " + params);
                    LOG.info("Current parameters: " + m4);
                }
                else
                {
                    bestLL = test2;
                    params = m4;
                }

//                //If the expansion worked, try to update all curve betas...
//                final CurveFitter<S, R, T> f2 = new BaseCurveFitter<>(_factory, model, grid_, _settings, _intercept);
//                model = f2.calibrateCurves();
//
//                final ParamFitter<S, R, T> f3 = new ParamFitter<>(model, _settings);
//
//                final double paramLL = f3.computeLogLikelihood(model.getParams(), g2, null);
//
//                LOG.info("Generated Log Likelihood: " + paramLL);
//                LOG.info("Regenerated Log Likelihood: " + paramLL + " -> " + endingLL);
//
//                if (Math.abs(paramLL - endingLL) > 0.0001)
//                {
//                    LOG.info("LL mismatch.");
//                }
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
            final ParamFitResult<S, R, T> fitResult = innerFitCoefficients(params, filters_);

            if (fitResult.isBetter())
            {
                params = fitResult.getEndingParams();
                bestLL = fitResult.getEndingLL();
            }
        }
        catch (final ConvergenceException e)
        {
            LOG.info("Unable to improve results in coefficient fit, moving on.");
        }

        final ParamFitResult<S, R, T> output = new ParamFitResult<>(params_, params, bestLL, startingLL_, _grid.size());
        return output;
    }

}
