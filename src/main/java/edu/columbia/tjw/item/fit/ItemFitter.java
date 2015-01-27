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
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemGridFactory;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.fit.curve.BaseCurveFitter;
import edu.columbia.tjw.item.fit.curve.CurveFitter;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.Collection;
import java.util.Collections;
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

    private final ItemCurveFactory<T> _factory;
    private final ItemSettings _settings;
    private final R _intercept;

    public ItemFitter(final ItemCurveFactory<T> factory_, final R intercept_)
    {
        this(factory_, intercept_, new ItemSettings());
    }

    public ItemFitter(final ItemCurveFactory<T> factory_, final R intercept_, ItemSettings settings_)
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
    }

    public ItemParameters<S, R, T> generateInitialParameters(final S status_)
    {
        if (status_.getReachableCount() < 2)
        {
            throw new IllegalArgumentException("Only one reachable state, no need for a model.");
        }

        final ItemParameters<S, R, T> initial = new ItemParameters<>(status_, Collections.singletonList(_intercept));
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
     * N.B: The wrapped grid may cache, so if the underlying regressors are changed,
     * the resulting factory should be wrapped again. 
     *
     * @param factory_
     * @return
     */
    public ItemGridFactory<S, R, T> wrapGridGenerator(final ItemGridFactory<S, R, T> factory_)
    {
        if (factory_ instanceof RandomizedCurveFactory)
        {
            return factory_;
        }

        final ItemGridFactory<S, R, T> wrapped = new RandomizedCurveFactory<>(factory_, _settings);
        return wrapped;
    }

    /**
     * Add a group of coefficients to the model, then refit all coefficients.
     *
     * @param params_ The parameters to expand
     * @param gridFactory_ The factory that will construct datasets
     * @param filters_ Any filters that should be used to limit the allowed
     * coefficients, else null
     * @param coefficients_ The set of coefficients to fit.
     * @return A model fit with all the additional allowed coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    public ItemModel<S, R, T> addCoefficients(final ItemParameters<S, R, T> params_, final ItemGridFactory<S, R, T> gridFactory_, final Collection<ParamFilter<S, R, T>> filters_,
            final Collection<R> coefficients_) throws ConvergenceException
    {
        ItemParameters<S, R, T> params = params_;

        for (final R field : coefficients_)
        {
            params = params.addBeta(field, null);
        }

        return fitCoefficients(params, gridFactory_, filters_);
    }

    /**
     *
     * Optimize the coefficients.
     *
     * @param params_ The parameters to start with
     * @param gridFactory_ A factory that can create datasets for fitting
     * @param filters_ Filters describing any coefficients that should not be
     * adjusted
     * @return A model with newly optimized coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    public ItemModel<S, R, T> fitCoefficients(final ItemParameters<S, R, T> params_, final ItemGridFactory<S, R, T> gridFactory_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        ItemModel<S, R, T> model = new ItemModel<>(params_);
        //final ItemGridFactory<S, R, T> wrapped = new RandomizedCurveFactory<>(gridFactory_);
        final ItemFittingGrid<S, R> grid = gridFactory_.prepareGrid(params_);
        return fitCoefficients(model, grid, filters_);
    }

    /**
     * Same as overloaded method, different parameters.
     *
     * @param model_
     * @param fittingGrid_
     * @param filters_
     * @return
     * @throws ConvergenceException
     */
    public ItemModel<S, R, T> fitCoefficients(final ItemModel<S, R, T> model_, final ItemFittingGrid<S, R> fittingGrid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(model_, _settings);

        final ItemModel<S, R, T> m2 = fitter.fit(fittingGrid_, filters_);

        if (null == m2)
        {
            throw new ConvergenceException("Unable to improve parameter fit.");
        }

        return m2;
    }

    public ItemModel<S, R, T> runAnnealingPass(final ItemParameters<S, R, T> params_, final ItemGridFactory<S, R, T> gridFactory_, final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final int regCount = params_.regressorCount();

        final ParamFitter<S, R, T> f1 = new ParamFitter<>(new ItemModel<>(params_), _settings);

        //final ItemGridFactory<S, R, T> wrapped = new RandomizedCurveFactory<>(gridFactory_);

        final double startingLL = f1.computeLogLikelihood(params_, gridFactory_.prepareGrid(params_), filters_);
        double baseLL = startingLL;

        ItemParameters<S, R, T> base = params_;

        for (final R regressor : curveFields_)
        {
            final ItemParameters<S, R, T> reduced = base.dropRegressor(regressor);

            final int reducedCount = reduced.regressorCount();

            final int reduction = regCount - reducedCount;

            if (reduction <= 0)
            {
                continue;
            }

            final ItemParameters<S, R, T> rebuilt = expandModel(reduced, gridFactory_, curveFields_, filters_, reduction).getParams();

            final ParamFitter<S, R, T> f2 = new ParamFitter<>(new ItemModel<>(rebuilt), _settings);

            final double ll2 = f2.computeLogLikelihood(rebuilt, gridFactory_.prepareGrid(rebuilt), filters_);

            if (ll2 < baseLL)
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

    public double computeLogLikelihood(final ItemModel<S, R, T> model_, final ItemGridFactory<S, R, T> gridFactory_)
    {
        final ItemParameters<S, R, T> params = model_.getParams();
        final ParamFitter<S, R, T> f1 = new ParamFitter<>(new ItemModel<>(params), _settings);

        final double startingLL = f1.computeLogLikelihood(params, gridFactory_.prepareGrid(params), null);

        return startingLL;
    }

    /**
     * Add some new curves to this model.
     *
     * This function is the heart of the ITEM system, and uses most of the
     * computational resources.
     *
     * @param params_ The parameters to start with
     * @param gridFactory_ A factory that can create datasets for fitting
     * @param curveFields_ The regressors on which to draw curves
     * @param filters_ Filters describing any curves that should not be drawn or
     * optimized
     * @param curveCount_ The total number of curves that will be allowed.
     * @return A new model with additional curves added, and all coefficients
     * optimized.
     */
    public ItemModel<S, R, T> expandModel(final ItemParameters<S, R, T> params_, final ItemGridFactory<S, R, T> gridFactory_, final Set<R> curveFields_,
            final Collection<ParamFilter<S, R, T>> filters_, final int curveCount_)
    {
        final long start = System.currentTimeMillis();
        ItemModel<S, R, T> model = new ItemModel<>(params_);

        //final ItemGridFactory<S, R, T> wrapped = new RandomizedCurveFactory<>(gridFactory_);

        for (int i = 0; i < curveCount_; i++)
        {
            final ItemFittingGrid<S, R> grid = gridFactory_.prepareGrid(model.getParams());

            try
            {
                model = fitCoefficients(model, grid, filters_);
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Unable to improve results in coefficient fit, moving on.");
            }

            final CurveFitter<S, R, T> fitter = new BaseCurveFitter<>(_factory, model, grid, _settings, _intercept);

            try
            {
                model = fitter.generateCurve(curveFields_, filters_);
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
            final ItemFittingGrid<S, R> grid = gridFactory_.prepareGrid(model.getParams());
            model = fitCoefficients(model, grid, filters_);
        }
        catch (final ConvergenceException e)
        {
            LOG.info("Unable to improve results in coefficient fit, moving on.");
        }

        return model;
    }



}
