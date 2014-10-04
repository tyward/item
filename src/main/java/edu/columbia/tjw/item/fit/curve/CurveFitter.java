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
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.MultiLogistic;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ItemWorkspace;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public class CurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final int BLOCK_SIZE = 200 * 1000;
    private static final Logger LOG = LogUtil.getLogger(CurveFitter.class);
    private static final double AIC_CUTOFF = -5.0;

    private final EnumFamily<T> _family;
    private final ItemCurveFactory<T> _factory;

    private final ItemModel<S, R, T> _model;
    private final ItemFittingGrid<S, R> _grid;
    private final RectangularDoubleArray _powerScores;
    private final RectangularDoubleArray _actualProbabilities;
    private final MultivariateOptimizer _optimizer;
    private final int[] _indexList;

    public CurveFitter(final ItemCurveFactory<T> factory_, final ItemModel<S, R, T> model_, final ItemFittingGrid<S, R> grid_)
    {
        _family = factory_.getFamily();
        _factory = factory_;
        _model = model_;
        _grid = grid_;
        _optimizer = new MultivariateOptimizer(BLOCK_SIZE, 300, 20, 0.1);

        int count = 0;

        final int gridSize = _grid.totalSize();
        final S fromStatus = model_.getParams().getStatus();
        final int fromStatusOrdinal = fromStatus.ordinal();

        for (int i = 0; i < gridSize; i++)
        {
            final int statOrdinal = _grid.getStatus(i);

            if (statOrdinal != fromStatusOrdinal)
            {
                continue;
            }
            if (!grid_.hasNextStatus(i))
            {
                continue;
            }

            count++;
        }

        final int reachableCount = fromStatus.getReachableCount();
        final ItemWorkspace<S> workspace = model_.generateWorkspace();
        final double[] probabilities = new double[reachableCount];

        _indexList = new int[count];
        _powerScores = new RectangularDoubleArray(count, reachableCount);
        _actualProbabilities = new RectangularDoubleArray(count, reachableCount);

        final List<S> reachable = fromStatus.getReachable();

        final int baseCase = fromStatus.getReachable().indexOf(fromStatus);
        int pointer = 0;

        for (int i = 0; i < gridSize; i++)
        {
            final int statOrdinal = _grid.getStatus(i);

            if (statOrdinal != fromStatusOrdinal)
            {
                continue;
            }
            if (!grid_.hasNextStatus(i))
            {
                continue;
            }

            _indexList[pointer] = i;

            model_.transitionProbability(_grid, workspace, i, probabilities);

            MultiLogistic.multiLogitFunction(baseCase, probabilities, probabilities);

            for (int w = 0; w < reachableCount; w++)
            {
                final double next = probabilities[w];
                _powerScores.set(pointer, w, next);

                final S stat = reachable.get(w);
                final int actualTrans = _grid.getNextStatus(i);

                if (actualTrans == stat.ordinal())
                {
                    _actualProbabilities.set(pointer, w, 1.0);
                }
                else
                {
                    _actualProbabilities.set(pointer, w, 0.0);
                }
            }

            pointer++;

        }
    }

//    private OptimizationGrid prepareData(final MortgageModelParameters params_, final DataGrid baseGrid_)
//    {
//        final ModelGrid rawGrid = new RawModelGrid(baseGrid_, params_);
//        final IntGridReader statusReader = (IntGridReader) baseGrid_.getReader(DataField.STATUS);
//        final OptimizationGrid oGrid = new ParamOptimizationGrid(rawGrid, statusReader);
//        return oGrid;
//    }
    public ItemModel<S, R, T> generateCurve(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filter_) throws ConvergenceException
    {
        final FitResult<S, R, T> best = findBest(fields_, filter_);

        if (null == best)
        {
            throw new ConvergenceException("Unable to improve model.");
        }

        final ItemCurve<?> trans = best._trans;

        LOG.info("Best transformation: " + trans);
        LOG.info("Best Field: " + best._field);
        LOG.info("LL improvement: " + best._startingLogL + " -> " + best._logL + ": " + best._llImprovement);
        LOG.info("Best to state: " + best._toState);
        LOG.info("Best point: " + best._point);

        final double aicDiff = best.calculateAicDifference();

        LOG.info("AIC diff: " + aicDiff);

        if (aicDiff > AIC_CUTOFF)
        {
            //We demand that the AIC improvement is more than the bare minimum. 
            //We want this curve to be good enough to support at least N+5 parameters.
            LOG.info("AIC improvement is not large enough.");
            throw new ConvergenceException("No curves could be added with sufficient AIC improvement: " + aicDiff);
        }

        ItemModel<S, R, T> output = best.getModel();
        final CurveFilter<S, R, T> filter = new CurveFilter<>(output.getParams().getStatus(), best._toState, best._field, best._trans);
        output = output.updateParameters(output.getParams().addFilter(filter));

//        System.out.println("Now refitting all parameters on model.");
//        final OptimizationGrid updated = prepareData(output.getParams(), this._grid.getUnderlying());
//
//        final MultiLogitParamFitter fitter = new MultiLogitParamFitter(output);
//        output = fitter.fit(updated, filter_);
        return output;
    }

    private FitResult<S, R, T> findBest(final Set<R> fields_, final Collection<ParamFilter<S, R, T>> filters_)
    {
        final ItemParameters<S, R, T> params = _model.getParams();
        final S fromStatus = params.getStatus();
        FitResult<S, R, T> bestResult = null;
        double bestImprovement = 0.0;

        final List<ParamFilter<S, R, T>> filters = new ArrayList<>();
        filters.addAll(this._model.getParams().getFilters());

        if (null != filters_)
        {
            filters.addAll(filters_);
        }

        for (final S toStatus : fromStatus.getReachable())
        {
            fieldLoop:
            for (final R field : fields_)
            {
                for (final ParamFilter<S, R, T> filter : filters)
                {
                    if (filter.isFiltered(fromStatus, toStatus, field, null))
                    {
                        continue fieldLoop;
                    }
                }

                for (final T curveType : _family.getMembers())
                {
                    final BaseParamGenerator<S, R, T> generator = new BaseParamGenerator<>(_factory, curveType, _model, toStatus);
                    final FitResult<S, R, T> res = findBest(generator, field, toStatus);

                    if (null != res)
                    {
                        final double improvement = res.calculateAicDifference();

                        if (improvement < bestImprovement)
                        {
                            LOG.info("New Best: " + res);
                            bestImprovement = improvement;
                            bestResult = res;
                        }
                    }
                }
            }
        }

        return bestResult;
    }

    private FitResult<S, R, T> findBest(final ParamGenerator<S, R, T> generator_, final R field_, final S toStatus_)
    {
        //LOG.info("\n\nFinding best: " + generator_ + " " + field_ + " " + toStatus_);

        final CurveOptimizerFunction<S, R, T> func = new CurveOptimizerFunction<>(generator_, field_, this._model.getParams().getStatus(), toStatus_, _powerScores, _actualProbabilities,
                _grid, _model, _indexList);

        final int dimension = generator_.paramCount();

        //Take advantage of the fact that this starts out as all zeros, and that all zeros
        //means no change....
        final MultivariatePoint startingPoint = new MultivariatePoint(dimension);

        func.prepare(startingPoint);
        final EvaluationResult res = func.generateResult();
        func.evaluate(0, func.numRows(), res);

        final double startingLL = res.getMean();

        final double mean = func.getMean();
        final double stdDev = func.getStdDev();

        final double[] starting = generator_.getStartingParams(mean, stdDev);

        for (int i = 0; i < dimension; i++)
        {
            startingPoint.setElement(i, starting[i]);
        }

        try
        {
            final OptimizationResult<MultivariatePoint> result = _optimizer.optimize(func, startingPoint);

            final MultivariatePoint best = result.getOptimum();
            final double[] bestVal = best.getElements();

            final ItemCurve<?> trans = generator_.generateTransformation(bestVal);

            final double bestLL = result.minValue();

//            final long start = System.currentTimeMillis();
//            final EvaluationResult tmp = func.generateResult();
//
//            for (int i = 0; i < 10; i++)
//            {
//                func.value(best, 0, func.numRows(), tmp);
//                tmp.clear();
//            }
//
//            final long end = System.currentTimeMillis();
//            final long elapsed = (end - start);
            final FitResult output = new FitResult(toStatus_, best, generator_, field_, trans, bestLL, startingLL, result.dataElementCount());

            LOG.info("\nFound Curve: " + generator_ + " " + field_ + " " + toStatus_);
            LOG.info("Best point: " + best);
            LOG.info("LL change: " + startingLL + " -> " + bestLL + ": " + (startingLL - bestLL));
            LOG.info("AIC diff: " + output.calculateAicDifference());
            LOG.info("\n\n");

            return output;
        }
        catch (final ConvergenceException e)
        {
            LOG.info("Convergence exception caught: " + e);
            return null;
        }
    }

    private static final class CurveFilter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements ParamFilter<S, R, T>
    {
        private final S _fromStatus;
        private final S _toStatus;
        private final R _field;
        private final ItemCurve _trans;

        public CurveFilter(final S fromStatus_, final S toStatus_, final R field_, final ItemCurve trans_)
        {
            _fromStatus = fromStatus_;
            _toStatus = toStatus_;
            _field = field_;
            _trans = trans_;
        }

        @Override
        public boolean isFiltered(S fromStatus_, S toStatus_, R field_, ItemCurve<T> trans_)
        {
            if (fromStatus_ != _fromStatus)
            {
                //not the same model, ignore. 
                return false;
            }
            if (field_ != _field)
            {
                //not the same field, ignore. 
                return false;
            }
            if (null == _trans)
            {
                //No fitting is allowed on raw regressors once we have curves on them.
                return true;
            }
            if (!_trans.equals(trans_))
            {
                //someone else's curve, leave it up to them to filter it.
                return false;
            }

            if (toStatus_ != _toStatus)
            {
                //Curves are good for only one from -> to pair.
                return true;
            }

            //OK, we're good, everything matches. 
            return false;
        }

    }

    private static final class FitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
    {
        private final double _startingLogL;
        private final double _logL;
        private final double _llImprovement;
        private final ItemCurve<?> _trans;
        private final ParamGenerator<S, R, T> _generator;
        private final R _field;
        private final MultivariatePoint _point;
        private final S _toState;
        private final int _rowCount;

        public FitResult(final S toState_, final MultivariatePoint point_, final ParamGenerator<S, R, T> generator_, final R field_,
                final ItemCurve<T> trans_, final double logLikelihood_, final double startingLL_, final int rowCount_)
        {
            _logL = logLikelihood_;
            _trans = trans_;
            _point = point_;
            _llImprovement = (startingLL_ - _logL);
            _generator = generator_;
            _field = field_;
            _startingLogL = startingLL_;
            _toState = toState_;
            _rowCount = rowCount_;
        }

        public ItemModel<S, R, T> getModel()
        {
            final double[] params = new double[_generator.paramCount()];

            for (int i = 0; i < params.length; i++)
            {
                params[i] = _point.getElement(i);
            }

            return (ItemModel<S, R, T>) _generator.generatedModel(params, _field);
        }

        public double getLogLikelihood()
        {
            return _logL;
        }

        public double improvementPerParameter()
        {
            return _llImprovement / getEffectiveParamCount();
        }

        public int getEffectiveParamCount()
        {
            return (1 + _trans.getCurveType().getParamCount());
        }

        public double calculateAicDifference()
        {
            final double scaledImprovement = _llImprovement * _rowCount;
            final double paramContribution = getEffectiveParamCount();
            final double aicDiff = 2.0 * (paramContribution - scaledImprovement);
            return aicDiff;
        }
    }

}
