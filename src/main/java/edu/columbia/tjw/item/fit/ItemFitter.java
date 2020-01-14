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

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.base.raw.RawFittingGrid;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.fit.base.ModelFitter;
import edu.columbia.tjw.item.fit.calculator.BlockResult;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A class designed to expand the model by adding curves.
 * <p>
 * In addition, it may be used to fit only coefficients if needed.
 *
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 * @author tyler
 */
public final class ItemFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(ItemFitter.class);

    private final ItemSettings _settings;
    private final R _intercept;
    private final S _status;
    private final EntropyCalculator<S, R, T> _calc;

    private final ModelFitter<S, R, T> _modelFitter;

    private final FittingProgressChain<S, R, T> _chain;
    private final EnumFamily<T> _curveFamily;

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_, final S status_,
                      final ItemStatusGrid<S, R> grid_)
    {
        this(factory_, intercept_, status_, grid_, new ItemSettings());
    }

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_, final S status_,
                      final ItemStatusGrid<S, R> grid_, ItemSettings settings_)
    {
        this(factory_, intercept_, status_, randomizeGrid(grid_, settings_, status_), settings_);
    }

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_, final S status_,
                      final ItemFittingGrid<S, R> grid_)
    {
        this(factory_, intercept_, status_, grid_, new ItemSettings());
    }

    public ItemFitter(final ItemCurveFactory<R, T> factory_, final R intercept_, final S status_,
                      final ItemFittingGrid<S, R> grid_, ItemSettings settings_)
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

        _settings = settings_;
        _intercept = intercept_;
        _status = status_;
        _calc = new EntropyCalculator<>(grid_, _settings);
        _curveFamily = factory_.getFamily();

        final ItemParameters<S, R, T> starting = new ItemParameters<>(status_, _intercept,
                factory_.getFamily());

        _modelFitter = new ModelFitter(_curveFamily, _intercept, _status, grid_, settings_);
//        _base = new BaseFitter<>(_calc, _settings);
//        _fitter = new ParamFitter<>(_base);
//        _curveFitter = new CurveFitter<>(_settings, _base);

        _chain = new FittingProgressChain<>("Primary", starting, _calc.size(), _calc,
                _settings.getDoValidate());

        // Start by calibrating the parameters.
        this.fitAllParameters();
    }


    /**
     * This function will wrap the provided grid factory. The goal here is to
     * handle the randomization needed for accurate calculation, and also to
     * cache some data for efficiency.
     * <p>
     * It is strongly recommended that all grid factories are wrapped before
     * use.
     * <p>
     * N.B: The wrapped grid may cache, so if the underlying regressors are
     * changed, the resulting factory should be wrapped again.
     * <p>
     * Also, keep in mind that only relevant rows will be retained, particularly
     * those with the correct from state, and for which the next status is
     * known.
     *
     * @param grid_     The grid to randomize
     * @param settings_
     * @return A randomized version of grid_
     */
    public static <S extends ItemStatus<S>, R extends ItemRegressor<R>> ItemFittingGrid<S, R> randomizeGrid(
            final ItemStatusGrid<S, R> grid_,
            final ItemSettings settings_, final S status_)
    {
        return RawFittingGrid.fromStatusGrid(grid_, settings_, status_);
    }

    public S getStatus()
    {
        return _status;
    }

    public FittingProgressChain<S, R, T> getChain()
    {
        return _chain;
    }

    public EntropyCalculator<S, R, T> getCalculator()
    {
        return _calc;
    }

    public ItemParameters<S, R, T> getBestParameters()
    {
        return _chain.getBestParameters();
    }

    public ItemFittingGrid<S, R> getGrid()
    {
        return _calc.getGrid();
    }

    public FitResult<S, R, T> pushParameters(final String label_, ItemParameters<S, R, T> params_)
    {
        _chain.forcePushResults("ForcePush[" + label_ + "]", params_);

        this.fitAllParameters();

        return _chain.getLatestResults();
    }

    public FitResult<S, R, T> fitModel(final Collection<R> coefficients_,
                                       final Set<R> curveFields_,
                                       final int paramCount_, final boolean doAnnealing_) throws ConvergenceException
    {
        // This will basically generate a fully formed model.
        if (!coefficients_.isEmpty())
        {
            // Add the coefficients.
            addCoefficients(coefficients_);
        }

        // Add the curves.
        if (!curveFields_.isEmpty())
        {
            expandModel(curveFields_, paramCount_);
        }

        // Fit all the parameters together.
        fitAllParameters();

        if (doAnnealing_)
        {
            runAnnealingPass(curveFields_, true);
        }

        // Remove any parameters that aren't very helpful.
        trim(true);

        return _chain.getLatestResults();
    }


    public FitResult<S, R, T> runAnnealingByEntry(final Set<R> curveFields_,
                                                  final boolean exhaustiveCalibration_) throws ConvergenceException
    {
        int offset = 0;

        for (int i = 0; i < _chain.getBestParameters().getEntryCount(); i++)
        {
            final FittingProgressChain<S, R, T> subChain = new FittingProgressChain<>("AnnealingSubChain[" + i + "]",
                    _chain);
            final ItemParameters<S, R, T> base = subChain.getBestParameters();
            final int index = i - offset;

            if (index == base.getInterceptIndex())
            {
                continue;
            }
            if (base.getEntryStatusRestrict(index) == null)
            {
                //Annealing is only applied to curve entries.
                continue;
            }

            final ItemParameters<S, R, T> reduced = base.dropIndex(index);
            doSingleAnnealingOperation(curveFields_, base, reduced, subChain, exhaustiveCalibration_);

            final FitResult<S, R, T> results = subChain.getConsolidatedResults();
            //final double aicDiff = results.getFitResult().getAic() - results.getFitResult().getPrev().getAic();
            final double aicDiff = results.getInformationCriterion();

            LOG.info("----->Completed Annealing Step[" + i + "]: " + aicDiff);

            if (aicDiff < _settings.getAicCutoff())
            {
                //Just step back, this entry has been removed, other entries slid up.
                offset++;
                //_chain.pushResults(subChain.getName(), subChain.getConsolidatedResults());
            }
        }

        return _chain.getLatestResults();
    }

    public FitResult<S, R, T> runAnnealingPass(final Set<R> curveFields_, final boolean exhaustiveCalibration_)
            throws ConvergenceException
    {
        for (final R regressor : curveFields_)
        {
            final FittingProgressChain<S, R, T> subChain =
                    new FittingProgressChain<>("AnnealingSubChain[" + regressor.name() + "]", _chain);
            final ItemParameters<S, R, T> base = subChain.getBestParameters();
            final ItemParameters<S, R, T> reduced = base.dropRegressor(regressor);

            LOG.info("Annealing attempting to drop params from " + regressor);
            doSingleAnnealingOperation(curveFields_, base, reduced, subChain, exhaustiveCalibration_);
            LOG.info("---->Finished rebuild after dropping regressor: " + regressor);
        }

        this.trim(exhaustiveCalibration_);

        return _chain.getLatestResults();
    }

    /**
     * Add a group of coefficients to the model, then refit all coefficients.
     *
     * @param coefficients_ The set of coefficients to fit.
     * @return A model fit with all the additional allowed coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    private FitResult<S, R, T> addCoefficients(final Collection<R> coefficients_) throws ConvergenceException
    {
        final FitResult<S, R, T> fitResult = _modelFitter.addDirectRegressors(_chain.getLatestResults(), coefficients_);
        _chain.pushResults("Adding Direct Betas", fitResult);
        return _chain.getLatestResults();
    }

    public FitResult<S, R, T> fitAllParameters()
    {
        final FitResult<S, R, T> best = _chain.getLatestResults();
        final FitResult<S, R, T> refit = _modelFitter.fitAllParameters(best);
        _chain.pushResults("Full Refit", refit);
        return refit;
    }


    /**
     * Optimize the coefficients.
     *
     * @return A model with newly optimized coefficients.
     * @throws ConvergenceException If no progress could be made
     */
    private FitResult<S, R, T> fitCoefficients() throws ConvergenceException
    {
        final FitResult<S, R, T> betaFit = _modelFitter.fitBetas(_chain.getLatestResults());
        _chain.pushResults("Fit Betas", betaFit);
        return _chain.getLatestResults();
    }

    private void doSingleAnnealingOperation(final Set<R> curveFields_, final ItemParameters<S, R, T> base_,
                                            final ItemParameters<S, R, T> reduced_,
                                            final FittingProgressChain<S, R, T> subChain_,
                                            final boolean exhaustiveCalibrate_)
    {
        final int paramCount = base_.getEffectiveParamCount();

        subChain_.forcePushResults("ReducedFrame", reduced_);

        if (exhaustiveCalibrate_)
        {
            try
            {
                _modelFitter.getParamFitter().fit(subChain_);
                _modelFitter.getCurveFitter().calibrateCurves(0.0, true, subChain_);
            }
            catch (final ConvergenceException e)
            {
                e.printStackTrace();
            }
        }

        final int reducedCount = reduced_.getEffectiveParamCount();
        final int reduction = paramCount - reducedCount;

        if (reduction <= 0)
        {
            return;
        }

        final FitResult<S, R, T> rebuilt = expandModel(subChain_, curveFields_, reduction);
        final boolean better = _chain.pushResults("AnnealingExpansion", subChain_.getLatestResults());

        if (better)
        {
            LOG.info("Annealing improved model: " + rebuilt.getPrev().getEntropy() + " -> " + rebuilt
                    .getEntropy() + " (" + rebuilt.getInformationCriterion() + ")");
        }
        else
        {
            LOG.info("Annealing did not improve model, keeping old model");
        }
    }

    private FitResult<S, R, T> trim(final boolean exhaustiveCalibration_)
    {
        final FitResult<S, R, T> trimmed = _modelFitter.trim(_chain.getLatestResults());

        // Rebase the trimmed on top of the latest.
        final FitResult<S, R, T> rebased = new FitResult<>(trimmed, _chain.getLatestResults());

        _chain.pushResults("Trimmed", rebased);
        return _chain.getLatestResults();
    }


    public double computeLogLikelihood(final ItemParameters<S, R, T> params_)
    {
        final BlockResult ea = _calc.computeEntropy(params_);
        final double entropy = ea.getEntropyMean();
        return entropy;
    }

    public FitResult<S, R, T> generateFlagInteractions(final boolean exhaustive_)
    {
        return generateFlagInteractions(_chain.getBestParameters().getEntryCount(), exhaustive_);
    }

    private FitResult<S, R, T> generateFlagInteractions(final int entryNumber_, final boolean exhaustive_)
    {
        //N.B: This loop can keep expanding as the params grows very large, if we are very successful.
        // Just make sure to cap it out at the entryNumber_
        for (int i = 0; i < Math.min(_chain.getBestParameters().getEntryCount(), entryNumber_); i++)
        {
            final ItemParameters<S, R, T> params = _chain.getBestParameters();

            if (i == params.getInterceptIndex())
            {
                continue;
            }
            if (params.getEntryStatusRestrict(i) != null)
            {
                //This would be a curve, but curves already get interactions when generated or annealed.
                continue;
            }

            _modelFitter.getCurveFitter().generateInteractions(_chain, params.getEntryCurveParams(i, true),
                    params.getEntryStatusRestrict(i), 0.0, _chain.getLogLikelihood(), exhaustive_);
        }

        return _chain.getLatestResults();
    }

    /**
     * Add some new curves to this model.
     * <p>
     * This function is the heart of the ITEM system, and uses most of the
     * computational resources.
     *
     * @param curveFields_ The regressors on which to draw curves
     * @param paramCount_  The total number of additional params that will be
     *                     allowed.
     * @return A new model with additional curves added, and all coefficients
     * optimized.
     */
    private FitResult<S, R, T> expandModel(final Set<R> curveFields_, final int paramCount_)
    {
        if (paramCount_ < 1)
        {
            throw new IllegalArgumentException("Param count must be positive.");
        }

        expandModel(_chain, curveFields_, paramCount_);
        return _chain.getLatestResults();
    }

    public FitResult<S, R, T> calibrateCurves()
    {
        final FittingProgressChain<S, R, T> subChain = new FittingProgressChain<>("CalibrationChain", _chain);

        //First, try to calibrate any existing curves to improve the fit. 
        _modelFitter.getCurveFitter().calibrateCurves(0.0, true, subChain);

        final FitResult<S, R, T> results = subChain.getConsolidatedResults();

        if (this._chain.pushResults("ExhaustiveCalibration", results))
        {
            // If we were able to improve things, try to hit it with one more full calibration.
            this.fitAllParameters();
        }

        return results;
    }

    private FitResult<S, R, T> expandModel(final FittingProgressChain<S, R, T> chain_, final Set<R> curveFields_
            , final int paramCount_)
    {
        final FitResult<S, R, T> expansion = _modelFitter.expandModel(chain_.getLatestResults(), curveFields_,
                paramCount_ + chain_.getBestParameters().getEffectiveParamCount());

        final List<FitResult<S, R, T>> resultList = new ArrayList<>();

        FitResult<S, R, T> target = expansion;

        for (int i = 0; i < 1000; i++)
        {
            if (target != chain_.getLatestResults())
            {
                if (target.getPrev() == target)
                {
                    throw new IllegalArgumentException("Prev loopback.");
                }

                resultList.add(0, target);
                target = target.getPrev();
            }
            else
            {
                break;
            }
        }

        for (final FitResult<S, R, T> result : resultList)
        {
            final FitResult<S, R, T> rebased = new FitResult<>(result, chain_.getLatestResults());
            chain_.pushResults("CurveGeneration", rebased);
        }

        try
        {
            _modelFitter.getParamFitter().fit(chain_);
        }
        catch (final ConvergenceException e)
        {
            LOG.info("Unable to improve results in coefficient fit, moving on.");
        }

        return expansion;
    }

}
