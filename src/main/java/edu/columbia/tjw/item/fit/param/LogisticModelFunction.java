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
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.optimize.*;

/**
 * @param <S> The status type for this grid
 * @param <R> The regressor type for this grid
 * @param <T> The curve type for this grid
 * @author tyler
 */
public class LogisticModelFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final ParamFittingGrid<S, R, T> _grid;
    private final PackedParameters<S, R, T> _packed;

    private ItemModel<S, R, T> _model;

    public LogisticModelFunction(
            final ItemParameters<S, R, T> params_, final ParamFittingGrid<S, R, T> grid_,
            final ItemModel<S, R, T> model_, ItemSettings settings_, final PackedParameters<S, R, T> packed_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());
        _grid = grid_;
        _model = model_;
        _packed = packed_;
    }

    public double[] getBeta()
    {
        return _packed.getPacked();
    }

    public ItemParameters<S, R, T> generateParams(final double[] beta_)
    {
        _packed.updatePacked(beta_);
        final ItemParameters<S, R, T> p2 = _packed.generateParams();
        return p2;
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

        if (!changed)
        {
            return;
        }

        final ItemParameters<S, R, T> updated = _packed.generateParams();
        _model = new ItemModel<>(updated);
    }

    @Override
    protected void evaluate(int start_, int end_, EvaluationResult result_)
    {
        if (start_ == end_)
        {
            return;
        }

        final ItemModel<S, R, T> localModel = _model.clone();

        for (int i = start_; i < end_; i++)
        {
            final double ll = localModel.logLikelihood(_grid, i);

            result_.add(ll, result_.getHighWater(), i + 1);
        }

        result_.setHighRow(end_);
    }


    @Override
    protected MultivariateGradient evaluateDerivative(int start_, int end_, MultivariatePoint input_,
                                                      EvaluationResult result_)
    {
        final int dimension = input_.getDimension();
        final double[] derivative = new double[dimension];

        if (start_ >= end_)
        {
            final MultivariatePoint der = new MultivariatePoint(derivative);
            return new MultivariateGradient(input_, der, null, 0.0);
        }

        final ItemModel<S, R, T> localModel = _model.clone();

        final int count = localModel.computeDerivative(_grid, start_, end_, _packed, derivative);

        if (count > 0)
        {
            //N.B: we are computing the negative log likelihood. 
            final double invCount = -1.0 / count;

            for (int i = 0; i < dimension; i++)
            {
                derivative[i] = derivative[i] * invCount;
            }
        }

        final MultivariatePoint der = new MultivariatePoint(derivative);

        final MultivariateGradient grad = new MultivariateGradient(input_, der, null, 0.0);

        return grad;
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        return (end_ - start_);
    }

}
