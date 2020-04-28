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
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.calculator.*;
import edu.columbia.tjw.item.util.MathTools;

/**
 * @param <S>
 * @param <R>
 * @param <T>
 * @author tyler
 */
public final class EntropyCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final double EPSILON = Math.ulp(4.0); // Just a bit bigger than machine epsilon.
    private final ItemSettings _settings;
    private final FitPointGenerator<S, R, T> _calc;
    private final ItemFittingGrid<S, R> _grid;

    public EntropyCalculator(final ItemFittingGrid<S, R> grid_)
    {
        this(grid_, new ItemSettings());
    }

    public EntropyCalculator(final ItemFittingGrid<S, R> grid_, final ItemSettings settings_)
    {
        _calc = new FitPointGenerator<>(grid_);
        _grid = grid_;
        _settings = settings_;
    }

    public ItemFittingGrid<S, R> getGrid()
    {
        return _grid;
    }

    public S getFromStatus()
    {
        return _grid.getFromStatus();
    }

    public int size()
    {
        return _grid.size();
    }

    public ItemFitPoint<S, R, T> generatePoint(final ItemParameters<S, R, T> params_)
    {
        return _calc.generatePoint(params_);
    }

    public synchronized FitResult<S, R, T> computeFitResult(final ItemParameters<S, R, T> params_,
                                                            FitResult<S, R, T> prevResult_)
    {
        final ItemFitPoint<S, R, T> point = _calc.generatePoint(params_);
        final FitResult<S, R, T> output = new FitResult<>(point, prevResult_, _settings.getComplexFitResults());
        return output;
    }

    public GradientResult computeGradients(final ItemParameters<S, R, T> params_)
    {
        final PackedParameters<S, R, T> origPacked = params_.generatePacked();
        return computeGradients(origPacked);
    }

    public GradientResult computeGradients(final PackedParameters<S, R, T> origPacked_)
    {
        final FitPointAnalyzer analyzer = new FitPointAnalyzer(_settings.getBlockSize(),
                _settings.getTarget(),
                _settings);
        final FitPoint point = _calc.generatePoint(origPacked_);
        final double objective = analyzer.computeObjective(point, point.getBlockCount());
        final double[] grad = analyzer.getDerivative(point);

        final int dimension = grad.length;
        final double gradEpsilon = EPSILON * MathTools.maxAbsElement(grad);
        final double[] fdGrad = new double[dimension];

        for (int i = 0; i < dimension; i++)
        {
            final double h = 1.0e-8;
            final double absGrad = Math.abs(grad[i]);
            final double shiftSize;

            if (absGrad > gradEpsilon)
            {
                // Generate an appropriate level of
                shiftSize = h / absGrad;
            }
            else
            {
                // Doesn't really matter what we put here...
                shiftSize = h / gradEpsilon;
            }

            final PackedParameters<S, R, T> repacked = origPacked_.clone();
            repacked.setParameter(i, origPacked_.getParameter(i) + shiftSize);
            final FitPoint shiftPoint = _calc.generatePoint(repacked.generateParams());

            final double shiftObjective = analyzer.computeObjective(shiftPoint, shiftPoint.getBlockCount());
            fdGrad[i] = (shiftObjective - objective) / shiftSize;
        }

        final double[] adj = analyzer.getDerivativeAdjustment(point, null);


        return new GradientResult(_settings.getTarget(), objective, grad, fdGrad, adj);
    }

    public FitPoint generateFitPoint(final ItemParameters<S, R, T> params_)
    {
        return _calc.generatePoint(params_);
    }

    public BlockResult computeRawGradient(final ItemParameters<S, R, T> params_)
    {
        final ItemFitPoint<S, R, T> point = _calc.generatePoint(params_);
        point.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
        return point.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);
    }

    public BlockResult computeEntropy(final ItemParameters<S, R, T> params_)
    {
        final ItemFitPoint<S, R, T> point = _calc.generatePoint(params_);
        point.computeAll(BlockCalculationType.VALUE);
        return point.getAggregated(BlockCalculationType.VALUE);
    }
}
