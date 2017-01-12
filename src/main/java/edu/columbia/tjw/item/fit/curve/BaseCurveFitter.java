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
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.QuantileDistribution;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.MultiLogistic;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 */
public class BaseCurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends CurveFitter<S, R, T>
{
    //private static final int BLOCK_SIZE = 200 * 1000;
    private static final Logger LOG = LogUtil.getLogger(BaseCurveFitter.class);

    private final ItemCurveFactory<T> _factory;

    private final ItemSettings _settings;

    private final ItemStatusGrid<S, R> _grid;
    private final RectangularDoubleArray _powerScores;

    // Getting pretty messy, we can now reset our grids and models, recache the power scores, etc...
    private ParamFittingGrid<S, R, T> _paramGrid;
    private ItemModel<S, R, T> _model;

    //N.B: These are ordinals, not offsets.
    private final int[] _actualOutcomes;
    private final MultivariateOptimizer _optimizer;
    private final int[] _indexList;
    private final R _intercept;
    private final S _fromStatus;

    public BaseCurveFitter(final ItemCurveFactory<T> factory_, final ItemModel<S, R, T> model_, final ItemStatusGrid<S, R> grid_, final ItemSettings settings_, final R intercept_)
    {
        super(factory_, settings_);

        if (null == intercept_)
        {
            throw new NullPointerException("Intercept cannot be null.");
        }

        _intercept = intercept_;
        _settings = settings_;
        _factory = factory_;
        _model = model_;
        _grid = grid_;
        _optimizer = new MultivariateOptimizer(settings_.getBlockSize(), 300, 20, 0.1);

        int count = 0;

        final int gridSize = _grid.size();
        _fromStatus = model_.getParams().getStatus();
        final int fromStatusOrdinal = _fromStatus.ordinal();
        final int[] indexList = new int[gridSize];

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

            indexList[count++] = i;
        }

        final int reachableCount = _fromStatus.getReachableCount();

        _indexList = Arrays.copyOf(indexList, count);
        _powerScores = new RectangularDoubleArray(count, reachableCount);
        _actualOutcomes = new int[count];

        for (int i = 0; i < count; i++)
        {
            final int index = _indexList[i];
            _actualOutcomes[i] = _grid.getNextStatus(index);
        }

        regenPowerScores();
    }

    private QuantileDistribution generateDistribution(R field_, S toStatus_)
    {
        final ItemQuantileDistribution<S, R> quantGenerator = new ItemQuantileDistribution<>(getParamGrid(), _powerScores, _fromStatus, field_, toStatus_, _indexList);
        final QuantileDistribution dist = quantGenerator.getAdjusted();
        return dist;
    }

    private void setModel(final ItemModel<S, R, T> model_)
    {
        _model = model_;
        _paramGrid = null;
        regenPowerScores();
    }

    private synchronized ParamFittingGrid<S, R, T> getParamGrid()
    {
        if (null == _paramGrid)
        {
            _paramGrid = new ParamFittingGrid<>(_model.getParams(), _grid);
        }

        return _paramGrid;
    }

    private synchronized void regenPowerScores()
    {
        final int reachableCount = _fromStatus.getReachableCount();
        final double[] probabilities = new double[reachableCount];

        final int baseCase = _fromStatus.getReachable().indexOf(_fromStatus);
        final int count = _actualOutcomes.length;

        final ParamFittingGrid<S, R, T> paramGrid = getParamGrid();

        for (int i = 0; i < count; i++)
        {
            final int index = _indexList[i];

            _model.transitionProbability(paramGrid, index, probabilities);

            MultiLogistic.multiLogitFunction(baseCase, probabilities, probabilities);

            for (int w = 0; w < reachableCount; w++)
            {
                final double next = probabilities[w];
                _powerScores.set(i, w, next);
            }
        }
    }

    public synchronized RectangularDoubleArray getPowerScores()
    {
        return _powerScores;
    }

    private BaseParamGenerator<S, R, T> buildGenerator(T curveType_, S toStatus_, final ItemModel<S, R, T> model_)
    {
        final BaseParamGenerator<S, R, T> generator = new BaseParamGenerator<>(_factory, curveType_, model_, toStatus_, _settings, _intercept);
        return generator;
    }

    private CurveOptimizerFunction<S, R, T> generateFunction(T curveType_, R field_, S toStatus_, final BaseParamGenerator<S, R, T> generator_)
    {
        final ParamFittingGrid<S, R, T> paramGrid = getParamGrid();
        final CurveOptimizerFunction<S, R, T> func = new CurveOptimizerFunction<>(curveType_, generator_, field_, _fromStatus, toStatus_, this, _actualOutcomes,
                paramGrid, _indexList, _settings);

        return func;
    }

    @Override
    protected synchronized ItemParameters<S, R, T> getParams()
    {
        return _model.getParams();
    }

    @Override
    protected FitResult<S, R, T> findBest(T curveType_, R field_, S toStatus_) throws ConvergenceException
    {
        LOG.info("\nCalculating Curve[" + curveType_ + ", " + field_ + ", " + toStatus_ + "]");

        final QuantileDistribution dist = generateDistribution(field_, toStatus_);
        final BaseParamGenerator<S, R, T> generator = buildGenerator(curveType_, toStatus_, _model);
        final CurveOptimizerFunction<S, R, T> func = generateFunction(curveType_, field_, toStatus_, generator);

        final int dimension = generator.paramCount();

        //Take advantage of the fact that this starts out as all zeros, and that all zeros
        //means no change....
        final MultivariatePoint startingPoint = new MultivariatePoint(dimension);
        final EvaluationResult res = func.generateResult();
        func.value(startingPoint, 0, func.numRows(), res);

        final double startingLL = res.getMean();

        final double[] starting = generator.getStartingParams(dist);

        final FitResult<S, R, T> output = generateFit(generator, func, field_, startingLL, starting);

        if (_settings.getPolishStartingParams())
        {
            try
            {
                final double[] polished = generator.polishCurveParameters(dist, starting);

                if (!Arrays.equals(starting, polished))
                {
                    LOG.info("Have polished parameters, testing.");

                    final FitResult<S, R, T> output2 = generateFit(generator, func, field_, startingLL, polished);

                    //LOG.info("Fit comparison: " + output.getLogLikelihood() + " <> " + output2.getLogLikelihood());
                    final double aic1 = output.calculateAicDifference();
                    final double aic2 = output2.calculateAicDifference();
                    final String resString;

                    if (aic1 > aic2)
                    {
                        resString = "BETTER";
                    }
                    else if (aic2 > aic1)
                    {
                        resString = "WORSE";
                    }
                    else
                    {
                        resString = "SAME";
                    }

                    LOG.info("Polished params[" + aic1 + " <> " + aic2 + "]: " + resString);

                    if (aic1 > aic2)
                    {
                        //If the new results are actually better, return those instead.
                        return output2;
                    }
                }
            }
            catch (final Exception e)
            {
                LOG.info("Exception during polish: " + e.toString());
            }
        }

        return output;
    }

    private FitResult<S, R, T> generateFit(final BaseParamGenerator<S, R, T> generator_, final CurveOptimizerFunction<S, R, T> func_, R field_, final double startingLL_, final double[] starting_) throws ConvergenceException
    {
        final int dimension = generator_.paramCount();

        //Take advantage of the fact that this starts out as all zeros, and that all zeros
        //means no change....
        final MultivariatePoint startingPoint = new MultivariatePoint(dimension);

        for (int i = 0; i < dimension; i++)
        {
            startingPoint.setElement(i, starting_[i]);
        }

        OptimizationResult<MultivariatePoint> result = _optimizer.optimize(func_, startingPoint);

        final MultivariatePoint best = result.getOptimum();
        final double[] bestVal = best.getElements();

        final ItemCurve<T> trans = generator_.generateTransformation(bestVal);

        final double bestLL = result.minValue();

        final EvaluationResult res = func_.generateResult();
        func_.value(best, 0, func_.numRows(), res);

        final double trueBestLL = res.getMean();

        LOG.info("New LL: " + trueBestLL + " (+/- " + res.getStdDev() + "), estimate: " + bestLL + " difference Z-Score: " + ((bestLL - trueBestLL) / res.getStdDev()));

        LOG.info("Improvement Z-score: " + ((startingLL_ - trueBestLL) / res.getStdDev()));

        final FitResult<S, R, T> output = new FitResult<>(generator_.getToStatus(), best, generator_, field_, trans, bestLL, startingLL_, result.dataElementCount());

        LOG.info("Found Curve[" + generator_.getCurveType() + ", " + field_ + ", " + generator_.getToStatus() + "][" + output.calculateAicDifference() + "]: " + Arrays.toString(bestVal));
        LOG.info("LL change: " + startingLL_ + " -> " + bestLL + ": " + (startingLL_ - bestLL) + " \n\n");

        return output;
    }

    @Override
    protected ItemModel<S, R, T> calibrateCurve(R field_, S toStatus_, ItemCurve<T> targetCurve_) throws ConvergenceException
    {
        final ItemParameters<S, R, T> params = _model.getParams();

        if (params.isFiltered(_fromStatus, toStatus_, field_, targetCurve_))
        {
            return _model;
        }

        LOG.info("Calibrating curve: " + targetCurve_ + ", " + field_ + ", " + toStatus_);

        final T curveType = targetCurve_.getCurveType();

        final BaseParamGenerator<S, R, T> g1 = buildGenerator(curveType, toStatus_, _model);

        //Extract from the original params, we will strike it out of the updated params.
        final double[] starting = g1.generateParamVector(field_, targetCurve_);

        //Changes are allowed...
        final int index = params.getIndex(field_, targetCurve_);
        final ItemParameters<S, R, T> reduced = params.dropIndex(index);
        final ItemModel<S, R, T> model = new ItemModel<>(reduced);

        final BaseParamGenerator<S, R, T> generator = buildGenerator(curveType, toStatus_, model);
        final CurveOptimizerFunction<S, R, T> func = generateFunction(curveType, field_, toStatus_, generator);

        //Take advantage of the fact that this starts out as all zeros, and that all zeros
        //means no change....
        final int dimension = generator.paramCount();
        final MultivariatePoint startingPoint = new MultivariatePoint(dimension);
        startingPoint.setElements(starting);

        final EvaluationResult res = func.generateResult();
        func.value(startingPoint, 0, func.numRows(), res);
        final double startingLL = res.getMean();

        LOG.info("Starting LL: " + startingLL + " (+/- " + res.getStdDev() + ")");

        final FitResult<S, R, T> result = generateFit(generator, func, field_, startingLL, starting);

        final double endingLL = result.getLogLikelihood();

        LOG.info("LL improvement: " + startingLL + " -> " + result.getLogLikelihood());

        final double aicDiff = result.calculateAicDifference();

        LOG.info("AIC diff: " + aicDiff);

        if (aicDiff > _settings.getAicCutoff())
        {
            //We demand that the AIC improvement is more than the bare minimum. 
            //We want this curve to be good enough to support at least N+5 parameters.
            LOG.info("AIC improvement is not large enough, keeping old curve.");
            return _model;
        }

        ItemModel<S, R, T> outputModel = result.getModel();

        //Potentially, we can accumulate filters here that are not needed, but for now just live with it.
        final CurveFilter<S, R, T> filter = new CurveFilter<>(outputModel.getParams().getStatus(), toStatus_, field_, result.getTransformation());
        outputModel = outputModel.updateParameters(outputModel.getParams().addFilter(filter));

        this.setModel(outputModel);

        final ParamFitter<S, R, T> fitter = new ParamFitter<>(outputModel, _settings);
        final double paramLL = fitter.computeLogLikelihood(outputModel.getParams(), new ParamFittingGrid<>(outputModel.getParams(), _grid), null);

        LOG.info("Regenerated Log Likelihood: " + paramLL + " -> " + endingLL);

        if (Math.abs(paramLL - endingLL) > 0.0001)
        {
            LOG.info("LL mismatch.");
        }

        return outputModel;
    }

}
