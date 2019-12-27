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

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.calculator.BlockCalculationType;
import edu.columbia.tjw.item.fit.calculator.BlockResult;
import edu.columbia.tjw.item.fit.calculator.ItemFitPoint;
import edu.columbia.tjw.item.fit.calculator.FitPointGenerator;

/**
 * @param <S>
 * @param <R>
 * @param <T>
 * @author tyler
 */
public final class EntropyCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final FitPointGenerator<S, R, T> _calc;
    private final ItemFittingGrid<S, R> _grid;

    public EntropyCalculator(final ItemFittingGrid<S, R> grid_)
    {
        _calc = new FitPointGenerator<>(grid_);
        _grid = grid_;
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

    public BlockResult computeEntropy(final ItemParameters<S, R, T> params_)
    {
        final ItemFitPoint<S, R, T> point = _calc.generatePoint(params_);
        point.computeAll(BlockCalculationType.VALUE);
        return point.getAggregated(BlockCalculationType.VALUE);
    }

    public BlockResult computeDerivative(final ItemParameters<S, R, T> params_)
    {
        final ItemFitPoint<S, R, T> point = _calc.generatePoint(params_);
        point.computeAll(BlockCalculationType.FIRST_DERIVATIVE);
        return point.getAggregated(BlockCalculationType.FIRST_DERIVATIVE);
    }

    public double computeAic(final ItemParameters<S, R, T> params_)
    {
        final double entropy = computeEntropy(params_).getEntropySum();
        final double paramAdj = params_.getEffectiveParamCount();
        final double aic = entropy + paramAdj;
        return aic;
    }
}
