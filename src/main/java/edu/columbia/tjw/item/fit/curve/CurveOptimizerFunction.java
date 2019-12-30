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
import edu.columbia.tjw.item.fit.calculator.ItemFitPoint;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.ThreadedMultivariateFunction;

/**
 * @param <S> The status type for this optimizer function
 * @param <R> The regressor type for this optimizer function
 * @param <T> THe curve type for this optimizer function
 * @author tyler
 */
public class CurveOptimizerFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final ItemCurveFactory<R, T> _factory;
    private final int _size;
    private final ItemCurveParams<R, T> _initParams;
    private final PackedCurveFunction<S, R, T> _packed;

    private MultivariatePoint _prevPoint;


    public CurveOptimizerFunction(final ItemCurveParams<R, T> initParams_, final ItemCurveFactory<R, T> factory_,
                                  final S toStatus_, final CurveParamsFitter<S, R, T> curveFitter_,
                                  final int[] actualOrdinals_, final ItemFittingGrid<S, R> grid_,
                                  final ItemSettings settings_, final ItemParameters<S, R, T> rawParams_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        _packed = new PackedCurveFunction<>(settings_, initParams_, toStatus_, rawParams_, grid_);
        _factory = factory_;
        _initParams = initParams_;

        _size = actualOrdinals_.length;
    }

    public ItemFitPoint<S, R, T> evaluate(final MultivariatePoint input_)
    {
        prepare(input_);
        return _packed.evaluate(input_);
    }

    public ItemFitPoint<S, R, T> evaluateGradient(final MultivariatePoint input_)
    {
        prepare(input_);
        return _packed.evaluateGradient(input_);
    }

    @Override
    public int dimension()
    {
        return _initParams.size();
    }

    @Override
    public int numRows()
    {
        return _size;
    }

    @Override
    protected void prepare(MultivariatePoint input_)
    {
        if (null != _prevPoint)
        {
            if (_prevPoint.equals(input_))
            {
                return;
            }
        }

        _prevPoint = input_.clone();
        _packed.prepare(input_);
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        final int size = (end_ - start_);
        return size;
    }

}
