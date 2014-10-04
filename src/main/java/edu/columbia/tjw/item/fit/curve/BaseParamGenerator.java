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
import edu.columbia.tjw.item.ItemStatus;
import java.util.List;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public class BaseParamGenerator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements ParamGenerator<S, R, T>
{
    private final ItemCurveFactory<T> _factory;
    private final T _type;
    private final ItemModel<S, R, T> _baseModel;
    private final int _tranParamCount;
    private final int _paramCount;
    private final S _toStatus;

    public BaseParamGenerator(final ItemCurveFactory<T> factory_, final T curveType_, final ItemModel<S, R, T> baseModel_, final S toStatus_)
    {
        _factory = factory_;
        _type = curveType_;
        _baseModel = baseModel_;
        _tranParamCount = curveType_.getParamCount();
        _paramCount = _tranParamCount + 2;

        _toStatus = toStatus_;
    }

    public final ItemModel<S, R, T> generatedModel(double[] params_, final R field_)
    {
        final int paramCount = paramCount();

        final ItemCurve<T> curve = generateTransformation(params_);

        final ItemParameters<S, R, T> orig = _baseModel.getParams();
        final ItemParameters<S, R, T> updated = orig.addBeta(field_, curve);

        final int matchIndex = updated.getIndex(field_, curve);
        final int interceptIndex = updated.getIndex(field_.getIntercept(), null);

        final S status = updated.getStatus();
        final List<S> reachable = status.getReachable();
        final int toIndex = reachable.indexOf(_toStatus);

        final double interceptAdjustment = getInterceptAdjustment(params_);
        final double beta = getBeta(params_);

        final double[][] values = updated.getBetas();

        values[toIndex][interceptIndex] += interceptAdjustment;
        values[toIndex][matchIndex] = beta;

        final ItemParameters<S, R, T> adjusted = updated.updateBetas(values);
        final ItemModel<S, R, T> output = _baseModel.updateParameters(adjusted);
        return output;
    }

    @Override
    public final int paramCount()
    {
        return _paramCount;
    }

    @Override
    public final double getInterceptAdjustment(final double[] params_)
    {
        final double interceptAdjustment = params_[getInterceptParamNumber()];
        return interceptAdjustment;
    }

    @Override
    public final double getBeta(final double[] params_)
    {
        final double beta = params_[getBetaParamNumber()];
        return beta;
    }

    public final double[] getStartingParams(final double regressorMean_, final double regressorStdDev_)
    {
        final double[] output = new double[this._paramCount];
        fillStartingParams(regressorMean_, regressorStdDev_, output);
        output[getInterceptParamNumber()] = -0.5;
        output[getBetaParamNumber()] = 1.0;
        return output;
    }

    @Override
    public final int getInterceptParamNumber()
    {
        return _tranParamCount;
    }

    @Override
    public final int getBetaParamNumber()
    {
        return _tranParamCount + 1;
    }

    @Override
    public final int translateParamNumber(final int input_)
    {
        return input_;
    }

    public final void fillStartingParams(final double regressorMean_, final double regressorStdDev_, final double[] params_)
    {
        _factory.fillStartingParameters(_type, regressorMean_, regressorStdDev_, params_);
    }

    @Override
    public final ItemCurve<T> generateTransformation(double[] params_)
    {
        final ItemCurve<T> curve = _factory.generateCurve(_type, params_);
        return curve;
    }

}
