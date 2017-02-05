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
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 */
public abstract class CurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(CurveFitter.class);

    //How many interactions will we allow to be added in one batch...
    private static final int MAX_INTERACTION_DEPTH = 4;

    private final EnumFamily<T> _family;
    private final ItemSettings _settings;
    private final ItemStatusGrid<S, R> _grid;
    private final ItemCurveFactory<R, T> _factory;

    public CurveFitter(final ItemCurveFactory<R, T> factory_, final ItemSettings settings_, final ItemStatusGrid<S, R> grid_)
    {
        if (null == settings_)
        {
            throw new NullPointerException("Settings cannot be null.");
        }

        _factory = factory_;
        _family = factory_.getFamily();
        _settings = settings_;
        _grid = grid_;
    }

    private double computeLogLikelihood(final ItemParameters<S, R, T> params_, final ItemStatusGrid<S, R> grid_)
    {
        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, grid_);
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(new ItemModel<>(params_), _settings);
        final double ll = fitter.computeLogLikelihood(params_, grid, null);
        return ll;
    }

    public final ItemModel<S, R, T> calibrateCurves()
    {
        LOG.info("Starting curve calibration sweep.");
        final ItemParameters<S, R, T> initParams = getParams();

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

            try
            {
                model = calibrateCurve(entryIndex, status);
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

        LOG.info("Finished curve calibration sweep: " + getParams());

        return model;
    }

    public final ItemModel<S, R, T> generateCurve(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filter_) throws ConvergenceException
    {
        FitResult<S, R, T> best = findBest(fields_, filter_);

        if (null == best)
        {
            throw new ConvergenceException("Unable to improve model.");
        }

        LOG.info("Best transformation: " + best.toString());
        LOG.info("LL improvement: " + best._startingLogL + " -> " + best._logL + ": " + best._llImprovement);
        LOG.info("Best to state: " + best.getToState());

        final double aicDiff = best.calculateAicDifference();

        LOG.info("AIC diff: " + aicDiff);

        if (aicDiff > _settings.getAicCutoff())
        {
            //We demand that the AIC improvement is more than the bare minimum. 
            //We want this curve to be good enough to support at least N+5 parameters.
            LOG.info("AIC improvement is not large enough.");
            throw new ConvergenceException("No curves could be added with sufficient AIC improvement: " + aicDiff);
        }

//        if (_settings.getAllowInteractionCurves())
//        {
//            LOG.info("Now calculating interactions.");
//
//            for (int i = 0; i < MAX_INTERACTION_DEPTH; i++)
//            {
//                final FitResult<S, R, T> interactionResult = generateInteractionTerm(best);
//
//                final double bestAicPP = best.aicPerParameter();
//                final double interAicPP = interactionResult.aicPerParameter();
//
//                if (interAicPP >= bestAicPP)
//                {
//                    LOG.info("Interaction terms were not better.");
//                    break;
//                }
//
//                LOG.info("Added interaction term, improved results: " + bestAicPP + " -> " + interAicPP);
//                LOG.info("New Parameters: " + interactionResult.getModel().getParams().toString());
//                best = interactionResult;
//            }
//        }
        ItemModel<S, R, T> output = best.getModel();
        LOG.info("Updated parameters: \n" + output.getParams().toString());

        return output;
    }

    private FitResult<S, R, T> generateInteractionTerm(final FitResult<S, R, T> currentResult_)
    {
        LOG.info("Attempting to expand curve with interaction terms.");

        final ItemParameters<S, R, T> params = getParams();
        final ItemCurveParams<R, T> expansionCurve = currentResult_.getCurveParams();
        final S toStatus = currentResult_.getToState();
        //final ItemParameters<S, R, T> expansionParams = currentResult_.getModel().getParams();
        final int entryCount = params.getEntryCount();

        //We can't have one entry depend on a single regressor twice. 
        final SortedSet<R> usedRegs = new TreeSet<>();
        final SortedSet<R> nullCurveRegs = new TreeSet<>();

        for (int i = 0; i < expansionCurve.getEntryDepth(); i++)
        {
            usedRegs.add(expansionCurve.getRegressor(i));
        }

        final double intercept = expansionCurve.getIntercept();
        final double beta = expansionCurve.getBeta();

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

                final List<ItemCurve<T>> curveList = new ArrayList<>(expansionCurve.getCurves());
                final List<R> regs = new ArrayList<>(expansionCurve.getRegressors());

                curveList.add(curve);
                regs.add(reg);

                final ItemCurveParams<R, T> testParams = new ItemCurveParams<>(intercept, beta, regs, curveList);

                try
                {
                    final FitResult<S, R, T> expandedResult = fitEntryExpansion(params, testParams, toStatus, false);

                    final double origPpAic = currentResult_.aicPerParameter();
                    final double newPpAic = expandedResult.aicPerParameter();

                    LOG.info("Interaction term result: " + origPpAic + " -> " + newPpAic);

                    //AIC will be negative, better AIC is more negative.
                    if (newPpAic < origPpAic)
                    {
                        return expandedResult;
                    }
                }
                catch (final ConvergenceException e)
                {
                    LOG.info("Convergence exception, moving on: " + e.toString());
                }
            }
        }

        //Unable to make any improvements, just return the original results.
        return currentResult_;
    }

    private FitResult<S, R, T> findBest(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        final ItemParameters<S, R, T> params = getParams();
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

                        final FitResult<S, R, T> res = findBest(curveType, field, toStatus);

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
     * @return
     * @throws ConvergenceException
     */
    protected abstract ItemModel<S, R, T> calibrateCurve(final int entryIndex_, final S toStatus_) throws ConvergenceException;

    public abstract FitResult<S, R, T> fitEntryExpansion(final ItemParameters<S, R, T> params_, final ItemCurveParams<R, T> initParams_, S toStatus_,
            final boolean subtractStarting_) throws ConvergenceException;

    protected abstract ItemParameters<S, R, T> getParams();

    protected abstract FitResult<S, R, T> findBest(final T curveType_, final R field_, final S toStatus_) throws ConvergenceException;

    protected static final class FitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
    {
        private final double _startingLogL;
        private final double _logL;
        private final double _llImprovement;
        private final int _rowCount;
        private final S _toState;
        private final ItemParameters<S, R, T> _params;
        private final ItemCurveParams<R, T> _curveParams;

        public FitResult(final ItemParameters<S, R, T> params_, final ItemCurveParams<R, T> curveParams_, final S toState_, final double logLikelihood_, final double startingLL_, final int rowCount_)
        {
            _params = params_;
            _curveParams = curveParams_;
            _toState = toState_;
            _logL = logLikelihood_;
            _llImprovement = (startingLL_ - _logL);
            _startingLogL = startingLL_;
            _rowCount = rowCount_;
        }

        public S getToState()
        {
            return _toState;
        }

        public ItemCurveParams<R, T> getCurveParams()
        {
            return _curveParams;
        }

        public ItemModel<S, R, T> getModel()
        {
            return new ItemModel<>(_params);
        }

        public double getLogLikelihood()
        {
            return _logL;
        }

        public double improvementPerParameter()
        {
            return _llImprovement / getEffectiveParamCount();
        }

        public double aicPerParameter()
        {
            final double aic = calculateAicDifference();
            final double output = aic / getEffectiveParamCount();
            return output;
        }

        public int getEffectiveParamCount()
        {
            return (_curveParams.size() - 1);
        }

        public double calculateAicDifference()
        {
            final double scaledImprovement = _llImprovement * _rowCount;
            final double paramContribution = getEffectiveParamCount();
            final double aicDiff = 2.0 * (paramContribution - scaledImprovement);
            return aicDiff;
        }

        @Override
        public String toString()
        {
            return "Fit result[" + _llImprovement + "]: \n" + _curveParams.toString();
        }

    }

}
