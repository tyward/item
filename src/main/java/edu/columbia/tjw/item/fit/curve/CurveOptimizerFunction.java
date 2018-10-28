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
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.optimize.*;

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
    private final double[] _workspace;
    private final ItemCurveParams<R, T> _initParams;
    private final PackedCurveFunction<S, R, T> _packed;
    private final ParamFittingGrid<S, R, T> _grid;

    private ItemCurveParams<R, T> _params;
    private MultivariatePoint _prevPoint;


    public CurveOptimizerFunction(final ItemCurveParams<R, T> initParams_, final ItemCurveFactory<R, T> factory_,
                                  final S toStatus_, final CurveParamsFitter<S, R, T> curveFitter_,
                                  final int[] actualOrdinals_, final ParamFittingGrid<S, R, T> grid_,
                                  final ItemSettings settings_, final ItemParameters<S, R, T> rawParams_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        _packed = new PackedCurveFunction<>(settings_, initParams_, toStatus_, rawParams_, grid_, curveFitter_);
        _factory = factory_;
        _initParams = initParams_;

        _size = actualOrdinals_.length;
        _workspace = new double[_initParams.size()];
        _grid = grid_;
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

        for (int i = 0; i < input_.getDimension(); i++)
        {
            _workspace[i] = input_.getElement(i);
        }

        _params = new ItemCurveParams<>(_initParams, _factory, _workspace);

        _packed.prepare(input_);
    }

    @Override
    protected void evaluate(int start_, int end_, EvaluationResult result_)
    {
        if (start_ == end_)
        {
            return;
        }

        _packed.evaluate(start_, end_, result_);
        return;
    }

    @Override
    protected MultivariateGradient evaluateDerivative(int start_, int end_, MultivariatePoint input_,
                                                      EvaluationResult result_)
    {
        final MultivariateGradient altGrad = _packed.evaluateDerivative(start_, end_, input_, result_);
        return altGrad;
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        final int size = (end_ - start_);
        return size;
    }

}
