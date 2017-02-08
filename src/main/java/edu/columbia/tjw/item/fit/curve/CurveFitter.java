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

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 */
public final class CurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(CurveFitter.class);

    //How many interactions will we allow to be added in one batch...
    private static final int MAX_INTERACTION_DEPTH = 4;

    private final EnumFamily<T> _family;
    private final ItemSettings _settings;
    private final ItemStatusGrid<S, R> _grid;
    private final ItemCurveFactory<R, T> _factory;

    private CurveParamsFitter<S, R, T> _fitter;

    public CurveFitter(final ItemCurveFactory<R, T> factory_, final ItemSettings settings_, final ItemStatusGrid<S, R> grid_, final ItemModel<S, R, T> model_)
    {
        if (null == settings_)
        {
            throw new NullPointerException("Settings cannot be null.");
        }

        _factory = factory_;
        _family = factory_.getFamily();
        _settings = settings_;
        _grid = grid_;

        _fitter = new CurveParamsFitter<>(_factory, model_, _grid, _settings);

    }

    public void setModel(final ItemModel<S, R, T> model_)
    {
        _fitter = new CurveParamsFitter<>(_fitter, model_.getParams());
    }

    protected double computeLogLikelihood(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_)
    {
        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, grid_);
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(new ItemModel<>(params_), _settings);
        final double ll = fitter.computeLogLikelihood(params_, grid, null);
        return ll;
    }

    public final ItemModel<S, R, T> calibrateCurves()
    {
        LOG.info("Starting curve calibration sweep.");
        final ItemParameters<S, R, T> initParams = _fitter.getParams();

        final int entryCount = initParams.getEntryCount();
        ItemModel<S, R, T> model = new ItemModel<>(initParams);

        final List<ItemCurveParams<R, T>> curveEntries = new ArrayList<>();

        for (int i = 0; i < entryCount; i++)
        {
            if (initParams.getEntryStatusRestrict(i) == null)
            {
                continue;
            }

            curveEntries.add(initParams.getEntryCurveParams(i));
        }

        for (final ItemCurveParams<R, T> entry : curveEntries)
        {
            final ItemParameters<S, R, T> params = model.getParams();
            final int entryIndex = params.getEntryIndex(entry);

            if (entryIndex == -1)
            {
                throw new IllegalStateException("Impossible.");
            }

            final S status = params.getEntryStatusRestrict(entryIndex);

            if (null == status)
            {
                throw new IllegalStateException("Impossible.");
            }

            final double startingLL = computeLogLikelihood(params, _grid);

            FitResult<S, R, T> calibrated = null;

            try
            {
                calibrated = calibrateCurve(entryIndex, status, startingLL);

                if (null == calibrated)
                {
                    continue;
                }

                model = calibrated.getModel();
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Trouble converging, moving on to next curve.");
                LOG.info(e.getMessage());
            }

            final double endingLL = computeLogLikelihood(model.getParams(), _grid);

            if (endingLL > startingLL)
            {
                LOG.warning("Ending LL is worse than starting: " + startingLL + " -> " + endingLL);

                LOG.info("Curve calibration starting params: " + params);
                LOG.warning("Starting entry: " + entry);
                LOG.warning("Ending params: " + model.getParams());

                throw new IllegalStateException("Impossible.");
            }

        }

        LOG.info("Finished curve calibration sweep: " + _fitter.getParams());

        return model;
    }

    public final ItemModel<S, R, T> generateCurve(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filter_) throws ConvergenceException
    {
        FitResult<S, R, T> best = findBest(fields_, filter_);

        if (null == best)
        {
            throw new ConvergenceException("Unable to improve model.");
        }

        LOG.info("Generated curve[" + best.aicPerParameter() + "][" + best.getStartingLogLikelihood() + " -> " + best.getLogLikelihood() + "][" + best.getToState() + "]: " + best.getCurveParams());

//        LOG.info("Best transformation: " + best.toString());
//        LOG.info("LL improvement: " + best._startingLogL + " -> " + best._logL + ": " + best._llImprovement);
//        LOG.info("Best to state: " + best.getToState());
        //final double aicDiff = best.calculateAicDifference();
        final double aicPP = best.aicPerParameter();

        if (aicPP > _settings.getAicCutoff())
        {
            //We demand that the AIC improvement is more than the bare minimum. 
            //We want this curve to be good enough to support at least N+5 parameters.
            LOG.info("AIC improvement is not large enough.");
            throw new ConvergenceException("No curves could be added with sufficient AIC improvement: " + aicPP);
        }

//        final double ll = computeLogLikelihood(this.getParams(), _grid);
//
//        if(Math.abs(ll - best._logL))
//        
//        
//        LOG.info("Log Likelihood check: " + ll);
        if (_settings.getAllowInteractionCurves())
        {
            LOG.info("Now calculating interactions.");

            for (int i = 0; i < MAX_INTERACTION_DEPTH; i++)
            {
                final FitResult<S, R, T> interactionResult = generateInteractionTerm(best);

                final double bestAicPP = best.aicPerParameter();
                final double interAicPP = interactionResult.aicPerParameter();

                if (interAicPP >= bestAicPP)
                {
                    LOG.info("Interaction terms were not better.");
                    break;
                }

                LOG.info("Added interaction term[" + bestAicPP + " -> " + interAicPP + "]");
                best = interactionResult;
            }
        }

        ItemModel<S, R, T> output = best.getModel();
        LOG.info("New Parameters[" + best.getLogLikelihood() + "]: \n" + output.getParams().toString());
        return output;
    }

    private ItemCurveParams<R, T> appendToCurveParams(final ItemCurveParams<R, T> initParams_, final ItemCurve<T> curve_, final R reg_)
    {
        final List<ItemCurve<T>> curveList = new ArrayList<>(initParams_.getCurves());
        final List<R> regs = new ArrayList<>(initParams_.getRegressors());

        curveList.add(curve_);
        regs.add(reg_);

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

    public FitResult<S, R, T> generateFlagInteraction(final double startingLL_, final int maxFlags_)
    {
        final ItemParameters<S, R, T> params = _fitter.getParams();
        final int entryCount = params.getEntryCount();

        final SortedSet<R> flagRegs = getFlagRegs(params);

        //FitResult<S, R, T> bestResult = null;
        final SortedMap<Double, FitResult<S, R, T>> viableResults = new TreeMap<>(Collections.reverseOrder());

        //Now, for each flag reg, attempt to interact it with each other regressor by simply adding one more entry...
        for (int i = 0; i < entryCount; i++)
        {
            final ItemCurveParams<R, T> curveParams = params.getEntryCurveParams(i, true);
            final TreeSet<R> curveRegs = new TreeSet<>(curveParams.getRegressors());

            for (final R nextReg : flagRegs)
            {
                if (curveRegs.contains(nextReg))
                {
                    continue;
                }

                final ItemCurveParams<R, T> testParams = appendToCurveParams(curveParams, null, nextReg);

                final S toStatus = params.getEntryStatusRestrict(i);

                FitResult<S, R, T> expandedResult = null;

                if (null == toStatus)
                {
                    //This is a flag-flag interaction term...
                    final ItemParameters<S, R, T> updatedParams = params.addBeta(testParams, null);

                    final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(updatedParams, _grid);
                    final ParamFitter<S, R, T> fitter = new ParamFitter<>(new ItemModel<>(updatedParams), _settings);

                    try
                    {
                        final ItemModel<S, R, T> model = fitter.fit(grid, null);

                        final double llValue = fitter.computeLogLikelihood(model.getParams(), grid, null);

                        if (llValue < startingLL_)
                        {
                            final ItemParameters<S, R, T> updated = model.getParams();
                            expandedResult = new FitResult<>(updated, updated.getEntryCurveParams(updated.getEntryCount() - 1, true), toStatus, llValue, startingLL_, grid.size());
                        }
                    }
                    catch (final ConvergenceException e)
                    {
                        LOG.info("Convergence exception, moving on: " + e.toString());
                        continue;
                    }
                }
                else
                {
                    //This is a flag-curve interaction term.
                    try
                    {
                        expandedResult = _fitter.expandParameters(params, testParams, toStatus, false, startingLL_);
                    }
                    catch (final ConvergenceException e)
                    {
                        LOG.info("Convergence exception, moving on: " + e.toString());
                        continue;
                    }
                }

                if (null == expandedResult)
                {
                    continue;
                }

                final double newPpAic = expandedResult.aicPerParameter();

                if (newPpAic >= 0.0)
                {
                    continue;
                }

                viableResults.put(newPpAic, expandedResult);
            }
        }

        if (viableResults.size() < 1)
        {
            return null;
        }

        FitResult<S, R, T> bestResult = null;

        for (final FitResult<S, R, T> val : viableResults.values())
        {
            if (null == bestResult)
            {
                bestResult = val;
                continue;
            }

            final ItemParameters<S, R, T> current = bestResult.getModel().getParams();

            final ItemCurveParams<R, T> expansion = val.getCurveParams();
            final ItemParameters<S, R, T> updatedParams = params.addBeta(expansion, val.getToState());

            final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(updatedParams, _grid);
            final ParamFitter<S, R, T> fitter = new ParamFitter<>(new ItemModel<>(updatedParams), _settings);

            try
            {
                final ItemModel<S, R, T> model = fitter.fit(grid, null);
                final ItemParameters<S, R, T> modParams = model.getParams();

                final double llValue = fitter.computeLogLikelihood(modParams, grid, null);

                final FitResult<S, R, T> expResults = new FitResult<>(modParams, modParams.getEntryCurveParams(modParams.getEntryCount() - 1, true), val.getToState(), llValue, bestResult.getLogLikelihood(), grid.size());

                final double ppAic = expResults.aicPerParameter();

                //Require some minimal level of goodness.
                if (ppAic > _settings.getAicCutoff())
                {
                    continue;
                }

                bestResult = expResults;
            }
            catch (final ConvergenceException e)
            {
                LOG.info("Convergence exception, moving on: " + e.toString());
                continue;
            }
        }

        return bestResult;
    }

    private FitResult<S, R, T> generateInteractionTerm(final ItemCurveParams<R, T> curveParams_, final S toStatus_, final double startingLL_)
    {
        final ItemParameters<S, R, T> params = _fitter.getParams();
        final int entryCount = params.getEntryCount();

        //We can't have one entry depend on a single regressor twice. 
        final SortedSet<R> usedRegs = new TreeSet<>();
        final SortedSet<R> nullCurveRegs = new TreeSet<>();

        for (int i = 0; i < curveParams_.getEntryDepth(); i++)
        {
            usedRegs.add(curveParams_.getRegressor(i));
        }

        FitResult<S, R, T> bestResult = null;

        for (int i = 0; i < entryCount; i++)
        {
            if (i == params.getInterceptIndex())
            {
                //Intercept is an implicit interaction curve with everything, can't add it.
                continue;
            }

            final int depth = params.getEntryDepth(i);

            for (int w = 0; w < depth; w++)
            {
                final R reg = params.getEntryRegressor(i, w);

                if (usedRegs.contains(reg))
                {
                    continue;
                }

                final ItemCurve<T> curve = params.getEntryCurve(i, w);

                if (null == curve)
                {
                    //This is a flag, don't try the same flag multiple times.
                    if (nullCurveRegs.contains(reg))
                    {
                        continue;
                    }

                    nullCurveRegs.add(reg);
                }

                final ItemCurveParams<R, T> testParams = appendToCurveParams(curveParams_, curve, reg);

                try
                {
                    final FitResult<S, R, T> expandedResult = _fitter.expandParameters(params, testParams, toStatus_, false, startingLL_);

                    if (null == bestResult)
                    {
                        bestResult = expandedResult;
                    }
                    else
                    {
                        final double origPpAic = bestResult.aicPerParameter();
                        final double newPpAic = expandedResult.aicPerParameter();

                        //LOG.info("Interaction term result: " + origPpAic + " -> " + newPpAic);
                        //AIC will be negative, better AIC is more negative.
                        if (newPpAic < origPpAic)
                        {
                            bestResult = expandedResult;
                            LOG.info("Found improved result[" + origPpAic + " -> " + newPpAic + "]: " + bestResult.getCurveParams());
                        }
                    }
                }
                catch (final ConvergenceException e)
                {
                    LOG.info("Convergence exception, moving on: " + e.toString());
                }
            }
        }

        if (null == bestResult)
        {
            return bestResult;
        }

        if (bestResult.aicPerParameter() >= _settings.getAicCutoff())
        {
            //This result didn't even meet the minimal standards needed for acceptance.
            return null;
        }

        //Unable to make any improvements, just return the original results.
        return bestResult;
    }

    private FitResult<S, R, T> generateInteractionTerm(final FitResult<S, R, T> currentResult_)
    {
        final double startingLL = currentResult_.getStartingLogLikelihood();
        //final ItemParameters<S, R, T> params = _fitter.getParams();
        final ItemCurveParams<R, T> expansionCurve = currentResult_.getCurveParams();
        final S toStatus = currentResult_.getToState();

        final FitResult<S, R, T> interactionResult = this.generateInteractionTerm(expansionCurve, toStatus, startingLL);

        if (null == interactionResult)
        {
            LOG.info("No good interaction results.");
            return currentResult_;
        }

        final double origPpAic = currentResult_.aicPerParameter();
        final double newPpAic = interactionResult.aicPerParameter();

        //LOG.info("Interaction term result: " + origPpAic + " -> " + newPpAic);
        //AIC will be negative, better AIC is more negative.
        if (newPpAic < origPpAic)
        {
            LOG.info("Found improved result[" + origPpAic + " -> " + newPpAic + "]: " + interactionResult.getCurveParams());
            return interactionResult;
        }
        else
        {
            LOG.info("Best interaction term isn't better[" + origPpAic + " -> " + newPpAic + "]: " + interactionResult.getCurveParams());
            return currentResult_;
        }

    }

    private FitResult<S, R, T> findBest(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        final ItemParameters<S, R, T> params = _fitter.getParams();
        final S fromStatus = params.getStatus();
        FitResult<S, R, T> bestResult = null;
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

                        if (params.curveIsForbidden(toStatus, vacuousParams, filters_))
                        {
                            continue;
                        }

                        final FitResult<S, R, T> res = _fitter.calibrateCurveAddition(curveType, field, toStatus);

                        if (params.curveIsForbidden(toStatus, res.getCurveParams(), filters_))
                        {
                            LOG.info("Generated curve, but it is forbidden by filters, dropping: " + res.getCurveParams());
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
                        LOG.info("Argument trouble (" + field + "), moving on to next curve.");
                        LOG.info(e.getMessage());
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
    protected FitResult<S, R, T> calibrateCurve(final int entryIndex_, final S toStatus_, final double startingLL_) throws ConvergenceException
    {
        FitResult<S, R, T> result = _fitter.calibrateExistingCurve(entryIndex_, toStatus_, startingLL_);

        if (null == result)
        {
            return null;
        }

        ItemModel<S, R, T> outputModel = result.getModel();

        this.setModel(outputModel);
        return result;
    }

//    protected ItemParameters<S, R, T> getParams()
//    {
//        return _fitter.getParams();
//    }
    //protected abstract FitResult<S, R, T> findBest(final T curveType_, final R field_, final S toStatus_) throws ConvergenceException;
    private final class ParamFilterImpl implements ParamFilter<S, R, T>
    {
        private final int _targetEntry;
        private final ItemCurveParams<R, T> _curveParams;

        public ParamFilterImpl(final int targetEntry_, final ItemCurveParams<R, T> curveParams_)
        {
            _targetEntry = targetEntry_;
            _curveParams = curveParams_;
        }

        @Override
        public boolean betaIsFrozen(ItemParameters<S, R, T> params_, S toStatus_, int paramEntry_)
        {
            //We freeze the betas except for the current entry, the new entry, and the intercept.
            if (params_.getInterceptIndex() == paramEntry_)
            {
                return false;
            }
            if (paramEntry_ == _targetEntry)
            {
                return false;
            }

            final int foundIndex = params_.getEntryIndex(_curveParams);

            if (foundIndex == paramEntry_)
            {
                return false;
            }

            return true;
        }

        @Override
        public boolean curveIsForbidden(ItemParameters<S, R, T> params_, S toStatus_, ItemCurveParams<R, T> curveParams_)
        {
            return false;
        }

    }

}
