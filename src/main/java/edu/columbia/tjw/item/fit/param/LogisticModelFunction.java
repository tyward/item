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
package edu.columbia.tjw.item.fit.param;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.fit.calculator.FitPointGenerator;
import edu.columbia.tjw.item.fit.calculator.ItemFitPoint;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.ThreadedMultivariateFunction;

/**
 * @param <S> The status type for this grid
 * @param <R> The regressor type for this grid
 * @param <T> The curve type for this grid
 * @author tyler
 */
public class LogisticModelFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final FitPointGenerator<S, R, T> _generator;
    private final ParamFittingGrid<S, R, T> _grid;
    private final PackedParameters<S, R, T> _packed;


    public LogisticModelFunction(
            final ItemParameters<S, R, T> params_, final ItemFittingGrid<S, R> grid_,
            final ItemModel<S, R, T> model_, ItemSettings settings_, final PackedParameters<S, R, T> packed_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        final ParamFittingGrid<S, R, T> grid = new ParamFittingGrid<>(params_, grid_);

        _generator = new FitPointGenerator<>(grid_);
        _grid = grid;
        _packed = packed_;
    }

    public ItemFitPoint<S, R, T> evaluate(final MultivariatePoint input_)
    {
        prepare(input_);
        return _generator.generatePoint(_packed);
    }

    public ItemFitPoint<S, R, T> evaluateGradient(final MultivariatePoint input_)
    {
        prepare(input_);
        return _generator.generateGradient(_packed);
    }

    public DoubleVector getBeta()
    {
        return _packed.getPacked();
    }

    @Override
    public int dimension()
    {
        return _packed.size();
    }

    @Override
    public int numRows()
    {
        return _grid.size();
    }

    @Override
    protected void prepare(MultivariatePoint input_)
    {
        final int dimension = this.dimension();
        boolean changed = false;

        for (int i = 0; i < dimension; i++)
        {
            final double value = input_.getElement(i);

            if (value != _packed.getParameter(i))
            {
                _packed.setParameter(i, value);
                changed = true;
            }
        }
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        return (end_ - start_);
    }

}
