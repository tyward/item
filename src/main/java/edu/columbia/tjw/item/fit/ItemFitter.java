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
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.data.RandomizedStatusGrid;
import edu.columbia.tjw.item.fit.curve.CurveFitter;
import edu.columbia.tjw.item.fit.curve.FitResult;
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

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_)
    {
        this(factory_, intercept_, new ItemSettings());
    }

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_, ItemSettings settings_)
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

        _factory = factory_;
        _settings = settings_;
        _intercept = intercept_;
        _family = intercept_.getFamily();
    }

    public ItemParameters<S, R, T> generateInitialParameters(final S status_)
    {
        if (status_.getReachableCount() < 2)
        {
            throw new IllegalArgumentException("Only one reachable state, no need for a model.");
        }

        final ItemParameters<S, R, T> initial = new ItemParameters<>(status_, _intercept);
        return initial;
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
     * @param grid_ The grid to randomize
     * @return A randomized version of grid_
     */
    public ItemStatusGrid<S, R> randomizeGrid(final ItemStatusGrid<S, R> grid_)
    {
        if (grid_ instanceof RandomizedStatusGrid)
        {
            return grid_;
        }

        final ItemStatusGrid<S, R> wrapped = new RandomizedStatusGrid<>(grid_, _settings, _family);
        return wrapped;
    }

    /**
     * Add a group of coefficients to the model, then refit all coefficients.
     *
     * @param params_ The parameters to expand
     * @param grid_ The grid of raw data to use for fitting.
     * @param filters_ Any filters that should be used to limit the allowed
     * coefficients, else null
     * @param coefficients_ The set of coefficients to fit.
     * @return A model fit with all the additional allowed coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    public ItemModel<S, R, T> addCoefficients(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_,
            final Collection<R> coefficients_) throws ConvergenceException
    {
        ItemParameters<S, R, T> params = params_;

        for (final R field : coefficients_)
        {
            params = params.addBeta(field);
        }

        return fitCoefficients(params, grid_, filters_);
    }

    /**
     *
     * Optimize the coefficients.
     *
     * @param params_ The parameters to start with
     * @param grid_ A factory that can create datasets for fitting
     * @param filters_ Filters describing any coefficients that should not be
     * adjusted
     * @return A model with newly optimized coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    public ItemModel<S, R, T> fitCoefficients(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        ItemModel<S, R, T> model = new ItemModel<>(params_);
        return fitCoefficients(model, grid_, filters_);
    }

    /**
     * Same as overloaded method, different parameters.
     *
     * @param model_ The model to use as a starting point
     * @param fittingGrid_ The grid of data used for fitting
     * @param filters_ Any filters to apply
     * @return An updated model with the beta values refit
     * @throws ConvergenceException If no progress could be made
     */
    private ItemModel<S, R, T> fitCoefficients(final ItemModel<S, R, T> model_, final ItemStatusGrid<S, R> fittingGrid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(model_.getParams(), fittingGrid_);
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(model_, _settings);

        final ItemModel<S, R, T> m2 = fitter.fit(grid, filters_);

        if (null == m2)
        {
            throw new ConvergenceException("Unable to improve parameter fit.");
        }

        return m2;
    }

    public ItemModel<S, R, T> runAnnealingPass(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_, final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final int regCount = params_.getEntryCount();

        final ParamFitter<S, R, T> f1 = new ParamFitter<>(new ItemModel<>(params_), _settings);
        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, grid_);

        final double startingLL = f1.computeLogLikelihood(params_, grid, filters_);
        double baseLL = startingLL;

        ItemParameters<S, R, T> base = params_;

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

            final ItemParameters<S, R, T> rebuilt = expandModel(reduced, grid_, curveFields_, filters_, reduction).getParams();

            final ParamFitter<S, R, T> f2 = new ParamFitter<>(new ItemModel<>(rebuilt), _settings);

            final double ll2 = f2.computeLogLikelihood(rebuilt, new ParamFittingGrid<>(rebuilt, grid_), filters_);

            if (MathFunctions.isAicBetter(baseLL, ll2))
            {
                LOG.info("Annealing improved model: " + baseLL + " -> " + ll2);
                base = rebuilt;
                baseLL = ll2;
            }
            else
            {
                LOG.info("Annealing did not improve model, keeping old model: " + baseLL + " -> " + ll2);
            }

            //TODO: Check this for improvement?
            LOG.info("---->Finished rebuild after dropping regressor: " + regressor);
        }

        if (baseLL == startingLL)
        {
            throw new ConvergenceException("Unable to make progress.");
        }

        return new ItemModel<>(base);
    }

    public double computeLogLikelihood(final ItemModel<S, R, T> model_, final ItemStatusGrid<S, R> grid_)
    {
        final ItemParameters<S, R, T> params = model_.getParams();
        final ParamFitter<S, R, T> f1 = new ParamFitter<>(new ItemModel<>(params), _settings);

        final double startingLL = f1.computeLogLikelihood(params, new ParamFittingGrid<>(params, grid_), null);

        return startingLL;
    }

    private double computeLogLikelihood(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_)
    {
        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, grid_);
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(new ItemModel<>(params_), _settings);
        final double ll = fitter.computeLogLikelihood(params_, grid, null);
        return ll;
    }

    public ItemModel<S, R, T> generateFlagInteractions(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_, final int interactionCount_)
    {
        //final long start = System.currentTimeMillis();
        ItemModel<S, R, T> model = new ItemModel<>(params_);

        double bestLL = computeLogLikelihood(params_, grid_);

        final CurveFitter<S, R, T> fitter = new CurveFitter<>(_factory, _settings, grid_, model);
        final FitResult<S, R, T> result = fitter.generateFlagInteraction(bestLL, interactionCount_);

        if (null == result)
        {
            return model;
        }

        if (MathFunctions.isAicWorse(bestLL, result.getLogLikelihood()))
        {
            LOG.info("Flag interactions made things worse, skipping.");
            return model;
        }

        LOG.info("Improved results through interactions: " + result.aicPerParameter());

        model = result.getModel();
        bestLL = result.getLogLikelihood();

        return model;
    }

    /**
     * Add some new curves to this model.
     *
     * This function is the heart of the ITEM system, and uses most of the
     * computational resources.
     *
     * @param params_ The parameters to start with
     * @param grid_ A factory that can create datasets for fitting
     * @param curveFields_ The regressors on which to draw curves
     * @param filters_ Filters describing any curves that should not be drawn or
     * optimized
     * @param curveCount_ The total number of curves that will be allowed.
     * @return A new model with additional curves added, and all coefficients
     * optimized.
     */
    public ItemModel<S, R, T> expandModel(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_, final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_, final int curveCount_)
    {
        final long start = System.currentTimeMillis();
        ItemModel<S, R, T> model = new ItemModel<>(params_);

        double bestLL = computeLogLikelihood(params_, grid_);

        for (int i = 0; i < curveCount_; i++)
        {
            try
            {
                final ItemModel<S, R, T> m2 = fitCoefficients(model, grid_, filters_);

                final double test = computeLogLikelihood(model.getParams(), grid_);

                if (MathFunctions.isAicWorse(bestLL, test))
                {
                    LOG.info("LL got worse: " + bestLL + " -> " + test);
                    LOG.info("Previous parameters: " + model.getParams());
                    LOG.info("Current parameters: " + m2.getParams());
                }
                else
                {
                    bestLL = test;
                    model = m2;
                }
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Unable to improve results in coefficient fit, moving on.");
            }

            final CurveFitter<S, R, T> fitter = new CurveFitter<>(_factory, _settings, grid_, model);

            //First, try to calibrate any existing curves to improve the fit. 
            final ItemModel<S, R, T> m3 = fitter.calibrateCurves();

            final double test = computeLogLikelihood(model.getParams(), grid_);

            if (MathFunctions.isAicWorse(bestLL, test))
            {
                LOG.info("LL got worse: " + bestLL + " -> " + test);
                LOG.info("Previous parameters: " + model.getParams());
                LOG.info("Current parameters: " + m3.getParams());
            }
            else
            {
                bestLL = test;
                model = m3;
            }

            try
            {
                //Now, try to add a new curve. 
                final ItemModel<S, R, T> m4 = fitter.generateCurve(curveFields_, filters_);

                final double test2 = computeLogLikelihood(model.getParams(), grid_);

                if (MathFunctions.isAicWorse(bestLL, test2))
                {
                    LOG.info("LL got worse: " + bestLL + " -> " + test);
                    LOG.info("Previous parameters: " + model.getParams());
                    LOG.info("Current parameters: " + m4.getParams());
                }
                else
                {
                    bestLL = test2;
                    model = m4;
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
            model = fitCoefficients(model, grid_, filters_);
        }
        catch (final ConvergenceException e)
        {
            LOG.info("Unable to improve results in coefficient fit, moving on.");
        }

        return model;
    }

}
