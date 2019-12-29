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
package edu.columbia.tjw.item.fit.curve;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.EntropyCalculator;
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.fit.FittingProgressChain;
import edu.columbia.tjw.item.fit.param.ParamFitResult;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.MathFunctions;
import org.apache.commons.math3.util.Pair;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 * @author tyler
 */
public final class CurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(CurveFitter.class);

    private final EnumFamily<T> _family;
    private final ItemSettings _settings;
    private final ItemFittingGrid<S, R> _grid;
    private final ItemCurveFactory<R, T> _factory;
    private final EntropyCalculator<S, R, T> _calc;

    private final ParamFitter<S, R, T> _paramFitter;

    private CurveParamsFitter<S, R, T> _fitter;
    private ItemParameters<S, R, T> _cacheParams;

    public CurveFitter(final ItemCurveFactory<R, T> factory_, final ItemSettings settings_, final ItemFittingGrid<S,
            R> grid_, final EntropyCalculator<S, R, T> calc_)
    {
        if (null == settings_)
        {
            throw new NullPointerException("Settings cannot be null.");
        }

        _factory = factory_;
        _family = factory_.getFamily();
        _settings = settings_;
        _grid = grid_;
        _calc = calc_;

        _paramFitter = new ParamFitter<>(_calc, _settings);
    }

    private synchronized CurveParamsFitter<S, R, T> getFitter(final FittingProgressChain<S, R, T> chain_)
    {
        final ItemParameters<S, R, T> params = chain_.getBestParameters();

        if (params != _cacheParams)
        {
            _fitter = new CurveParamsFitter<>(_factory, _grid, _settings, chain_);
            _cacheParams = params;
        }

        return _fitter;
    }

    public final boolean calibrateCurves(final double improvementTarget_, final boolean exhaustive_,
                                         final FittingProgressChain<S, R, T> chain_)
    {
        if (!(improvementTarget_ >= 0.0))
        {
            throw new IllegalArgumentException("Improvement target must be nonnegative.");
        }

        LOG.info("Starting curve calibration sweep.");
        final ItemParameters<S, R, T> initParams = chain_.getBestParameters();

        final int entryCount = initParams.getEntryCount();

        final List<ItemCurveParams<R, T>> curveEntries = new ArrayList<>();

        for (int i = 0; i < entryCount; i++)
        {
            if (initParams.getEntryStatusRestrict(i) == null)
            {
                continue;
            }

            curveEntries.add(initParams.getEntryCurveParams(i));
        }

        //Go through these in a random order.
        Collections.shuffle(curveEntries, _settings.getRandom());
        final int minCurves = _settings.getCalibrateSize();

        //So long as average improvement is above the bound, we will continue...
        //However, always do at least minCurves computations first...
        final double improvementBound = improvementTarget_ * _settings.getImprovementRatio();
        double totalImprovement = 0.0;

        final double startingEntropy = chain_.getLogLikelihood();

        for (int i = 0; i < curveEntries.size(); i++)
        {
            final double targetLevel = (totalImprovement / (i + 1));

            if (!exhaustive_ && i >= minCurves && (targetLevel < improvementBound))
            {
                //Not enough improvement, break out.
                break;
            }

            final ItemCurveParams<R, T> entry = curveEntries.get(i);
            final ItemParameters<S, R, T> params = chain_.getBestParameters();
            final int entryIndex = params.getEntryIndex(entry);

            if (entryIndex == -1)
            {
                System.out.println("Should not be possible, but skipping.");
                continue;
                //throw new IllegalStateException("Impossible.");
            }

            final S status = params.getEntryStatusRestrict(entryIndex);

            if (null == status)
            {
                throw new IllegalStateException("Impossible.");
            }

            final double startingLL = chain_.getLogLikelihood();
            //final double startingLL = computeLogLikelihood(params, _grid);

            try
            {
                calibrateCurve(entryIndex, status, chain_);
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Trouble converging, done calibrating.");
                LOG.info(e.getMessage());
                break;
            }

            final double endingLL = chain_.getLogLikelihood();
            final double improvement = startingLL - endingLL;

            if (MathFunctions.doubleCompareRounded(endingLL, startingLL) < 0)
            {
                LOG.warning("Ending LL is worse than starting: " + startingLL + " -> " + endingLL);

                LOG.info("Curve calibration starting params: " + params);
                LOG.warning("Starting entry: " + entry);
                LOG.warning("Ending params: " + chain_.getBestParameters());

                throw new IllegalStateException("Impossible.");
            }

            totalImprovement += improvement;
        }

        final double endingEntropy = chain_.getLogLikelihood();
        final boolean isImproved = (0 != MathFunctions.doubleCompareRounded(startingEntropy, endingEntropy));

        LOG.info("Finished curve calibration sweep[" + isImproved + "]: " + chain_.getBestParameters());

        return isImproved;
    }

    public final boolean generateCurve(final FittingProgressChain<S, R, T> chain_, final Set<R> fields_)
            throws ConvergenceException
    {
        final ItemParameters<S, R, T> preExpansion = chain_.getBestParameters();
        final double preExpansionEntropy = chain_.getLogLikelihood();
        CurveFitResult<S, R, T> best = findBest(fields_, getFitter(chain_));

        if (null == best)
        {
            return false;
        }

        final boolean origBetter = chain_.pushResults("CurveGeneration", best);

        if (!origBetter)
        {
            return false;
        }

        final double aicPP = best.aicPerParameter();

        //final FittingProgressChain<S, R, T> subChain = new FittingProgressChain
        LOG.info("Generated curve[" + aicPP + "][" + best.getStartingLogLikelihood() + " -> " + best
                .getLogLikelihood() + "][" + best.getToState() + "]: " + best.getCurveParams());

        if (_settings.getAllowInteractionCurves())
        {
            LOG.info("Now calculating interactions.");

            final boolean interactionBetter = this.generateInteractions(chain_, best);

            if (!interactionBetter)
            {
                LOG.info("Interaction terms were not better.");
            }
//            else
//            {
////                LOG.info("Added interaction term[" + bestAicPP + " -> " + interAicPP + "]");
////                best = interactionResult;
//            }
        }

        LOG.info("New Parameters[" + best.getLogLikelihood() + "]: \n" + best.getModelParams().toString());
        return true;
    }

    private ItemCurveParams<R, T> appendToCurveParams(final ItemCurveParams<R, T> initParams_,
                                                      final ItemCurve<T> curve_, final R reg_)
    {
        final List<ItemCurve<T>> curveList = new ArrayList<>(initParams_.getCurves());
        final List<R> regs = new ArrayList<>(initParams_.getRegressors());

        regs.add(reg_);
        curveList.add(curve_);

        final double intercept = initParams_.getIntercept();
        final double beta = initParams_.getBeta();

        final ItemCurveParams<R, T> curveParams = new ItemCurveParams<>(intercept, beta, regs, curveList);

        return curveParams;
    }

    private SortedSet<R> getFlagRegs(final ItemParameters<S, R, T> params_)
    {
        final int entryCount = params_.getEntryCount();
        final SortedSet<R> flagRegs = new TreeSet<>();

        for (int i = 0; i < entryCount; i++)
        {
            if (params_.getEntryStatusRestrict(i) != null)
            {
                continue;
            }
            if (params_.getInterceptIndex() == i)
            {
                continue;
            }

            final int depth = params_.getEntryDepth(i);

            for (int k = 0; k < depth; k++)
            {
                final R reg = params_.getEntryRegressor(i, k);
                flagRegs.add(reg);
            }
        }

        return flagRegs;
    }


    private CurveFitResult<S, R, T> generateSingleInteraction(final R reg_, final ItemParameters<S, R, T> params_,
                                                              final CurveFitResult<S, R, T> starting_,
                                                              final ItemCurve<T> curve_, final S toStatus)
    {
        final ItemCurveParams<R, T> testParams = appendToCurveParams(starting_.getCurveParams(), curve_, reg_);

        if (params_.curveIsForbidden(toStatus, testParams))
        {
            // Skip out, we aren't allowed to add a curve like this.
            return null;
        }

        final FittingProgressChain<S, R, T> subChain = new FittingProgressChain<>("SingleInteraction",
                starting_.getModelParams(),
                starting_.getFitResult().getEntropy(), _calc.size(), _calc, true);

        if (null == toStatus)
        {
            //This is a flag-flag interaction term...
            //This means, among other things, that we just add an additional entry with more flags
            final ItemParameters<S, R, T> updatedParams = params_.addBeta(testParams, null);

            try
            {
                final ParamFitResult<S, R, T> fitResult = _paramFitter.fit(subChain, updatedParams);
                final ItemParameters<S, R, T> modParams = fitResult.getEndingParams();
                ItemCurveParams<R, T> modCurveParams = modParams.getEntryCurveParams(modParams.getEntryCount() - 1,
                        true);

                return new CurveFitResult<>(fitResult.getFitResult(), modCurveParams, toStatus, _grid.size());
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Convergence exception, moving on: " + e.toString());
                return null;
            }
        }
        else
        {
            //This is a flag-curve interaction term.
            // Try to append this to the given CurveParams
            try
            {
                final CurveFitResult<S, R, T> result = getFitter(subChain).expandParameters(params_, testParams,
                        toStatus, starting_.getFitResult());

                if (!subChain.pushResults("ParameterExpansion", result))
                {
                    return result;
                }

                final ParamFitResult<S, R, T> calibrated = _paramFitter.fit(subChain, result.getModelParams());

                if (calibrated.isBetter())
                {
                    final ItemParameters<S, R, T> updated = calibrated.getEndingParams();
                    final CurveFitResult<S, R, T> r2 = new CurveFitResult<>(calibrated.getFitResult(),
                            updated.getEntryCurveParams(updated.getEntryCount() - 1, true), toStatus, _grid.size());
                    return r2;
                }
                else
                {
                    return result;
                }
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Convergence exception, moving on: " + e.toString());
                return null;
            }
        }
    }

    private List<Pair<R, ItemCurve<T>>> extractRegs(final ItemParameters<S, R, T> startingResult_, final S toStatus_)
    {
        final SortedSet<R> flags = getFlagRegs(startingResult_);
        final List<Pair<R, ItemCurve<T>>> allRegs = new ArrayList<>();

        for (final R reg : flags)
        {
            allRegs.add(new Pair<>(reg, null));
        }

        if (null != toStatus_)
        {
            for (int i = 0; i < startingResult_.getEntryCount(); i++)
            {
                final int depth = startingResult_.getEntryDepth(i);

                for (int z = 0; z < depth; z++)
                {
                    final ItemCurve<T> curve = startingResult_.getEntryCurve(i, z);

                    if (null == curve)
                    {
                        continue;
                    }

                    final R curveReg = startingResult_.getEntryRegressor(i, z);
                    allRegs.add(new Pair<>(curveReg, curve));
                }
            }
        }

        return allRegs;
    }

    public boolean generateInteractions(final FittingProgressChain<S, R, T> chain_,
                                        final CurveFitResult<S, R, T> curveFitResult_)
    {
        return generateInteractions(chain_, curveFitResult_.getCurveParams(), curveFitResult_.getToState(),
                curveFitResult_.aicPerParameter(), curveFitResult_.getFitResult().getPrev().getEntropy(),
                true);
    }

    // TODO: This needs to be fixed up, it doesn't work well currently.
    public boolean generateInteractions(final FittingProgressChain<S, R, T> chain_,
                                        final ItemCurveParams<R, T> curveParams_,
                                        final S toStatus_, final double perParameterTarget_,
                                        final double baseLL_, final boolean exhaustive_)
    {
        final ItemParameters<S, R, T> base = chain_.getBestParameters();
        final List<Pair<R, ItemCurve<T>>> allRegs = extractRegs(base, toStatus_);
        Collections.shuffle(allRegs, _settings.getRandom());

        final FitResult<S, R, T> prevResult = chain_.getLatestFrame().getFitResults().getFitResult();
        final double startingLL = prevResult.getEntropy();
        final double improvementBound = _settings.getImprovementRatio() * (chain_.getLatestFrame().getAicDiff());

        CurveFitResult<S, R, T> best = new CurveFitResult<>(prevResult, curveParams_, toStatus_,
                chain_.getRowCount());
        final boolean curveIsFlag = (curveParams_.getEntryDepth() == 1) && (curveParams_.getCurve(0) == null);
        int calcCount = 0;

        boolean hasInteraction = false;

        for (int i = 0; i < allRegs.size(); i++)
        {
            final double actLL = chain_.getLogLikelihood();
            final double aicTarget = improvementBound * (calcCount + 1);
            final double actAic = chain_.getLatestFrame().getAicDiff();
            final double llTarget = startingLL - (startingLL * 0.001 * (calcCount + 1));

            if (!exhaustive_ && calcCount >= _settings.getCalibrateSize() && actAic >= aicTarget && actLL > llTarget)
            {
                //We failed to make enough improvement...
                break;
            }

            final Pair<R, ItemCurve<T>> pair = allRegs.get(i);
            final R reg = pair.getFirst();
            final ItemCurve<T> curve = pair.getSecond();

            if (null == curve && curveIsFlag && reg.ordinal() < curveParams_.getRegressor(0).ordinal())
            {
                continue;
            }
            if (null == toStatus_ && null != curve)
            {
                //Trying to add a curve to a flag variable, not allowed (the other way is fine though).
                continue;
            }

            if (null == curve && curveParams_.getRegressors().contains(reg))
            {
                //It's a flag that was already used, skip. 
                continue;
            }

            calcCount++;
            final CurveFitResult<S, R, T> result = generateSingleInteraction(reg, base, best, curve,
                    toStatus_);

            if (null == result)
            {
                //Convergence error or forbidden curve, just break out.
                continue;
            }

            final double thisAic = MathFunctions.computeAicDifference(0, result.getEffectiveParamCount(), baseLL_,
                    result.getLogLikelihood(), this._grid.size());
            final double thisAicPP = thisAic / result.getEffectiveParamCount();

            if (thisAicPP >= perParameterTarget_)
            {
                //This failed, we need to drop out...
                continue;
            }

            //This result is sufficiently better that we can (maybe) add it to the chain.
            if (chain_.pushResults("CurveInteractions", result.getModelParams(), result.getLogLikelihood()))
            {
                hasInteraction = true;
                best = result;
            }
        }

        return hasInteraction;
    }

    private CurveFitResult<S, R, T> findBest(final Set<R> fields_, final CurveParamsFitter<S, R, T> fitter_)
    {
        final ItemParameters<S, R, T> params = fitter_.getParams();
        final S fromStatus = params.getStatus();
        CurveFitResult<S, R, T> bestResult = null;
        double bestImprovement = 0.0;

        for (final S toStatus : fromStatus.getReachable())
        {
            fieldLoop:
            for (final R field : fields_)
            {
                for (final T curveType : _family.getMembers())
                {
                    try
                    {
                        //First, check for admissibiilty.
                        //Requires making a quick vacuous set of params...
                        final ItemCurveParams<R, T> vacuousParams = new ItemCurveParams<>(0.0, 0.0, field,
                                _factory.generateCurve(curveType, 0, new double[curveType.getParamCount()]));

                        if (params.curveIsForbidden(toStatus, vacuousParams))
                        {
                            continue;
                        }

                        final CurveFitResult<S, R, T> res = fitter_.calibrateCurveAddition(curveType, field, toStatus);

                        if (params.curveIsForbidden(toStatus, res.getCurveParams()))
                        {
                            LOG.info("Generated curve, but it is forbidden by filters, dropping: " + res
                                    .getCurveParams());
                            continue;
                        }

                        final double improvement = res.calculateAicDifference();

                        if (improvement < bestImprovement)
                        {
                            LOG.info("New Best: " + res + " -> " + improvement + " vs. " + bestImprovement);
                            bestImprovement = improvement;
                            bestResult = res;
                        }
                    }
                    catch (final ConvergenceException e)
                    {
                        LOG.info("Trouble converging, moving on to next curve.");
                        LOG.info(e.getMessage());
                    }
                    catch (final IllegalArgumentException e)
                    {
                        LOG.log(Level.INFO, "Argument trouble (" + field + "), moving on to next curve.", e);
                    }

                }
            }
        }

        return bestResult;
    }

    /**
     * Calibrate the given curve, but also update the model underlying this
     * fitter, if necessary.
     *
     * @param entryIndex_ The index of the entry to calibrate.
     * @param toStatus_
     * @return
     * @throws ConvergenceException
     */
    private boolean calibrateCurve(final int entryIndex_, final S toStatus_,
                                   final FittingProgressChain<S, R, T> subChain_) throws ConvergenceException
    {
        CurveFitResult<S, R, T> result = getFitter(subChain_).calibrateExistingCurve(entryIndex_, toStatus_);

        if (null == result)
        {
            return false;
        }

        final boolean isBetter = subChain_.pushResults("CurveCalibrate", result);
        return isBetter;
    }

}
