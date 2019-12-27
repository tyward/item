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
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.util.MathFunctions;

/**
 * @param <S>
 * @param <R>
 * @param <T>
 * @author tyler
 */
public final class CurveFitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final FitResult<S, R, T> _fitResult;
    private final int _rowCount;
    private final S _toState;
    private final ItemCurveParams<R, T> _curveParams;

    public CurveFitResult(final ItemParameters<S, R, T> startingParams_, final ItemParameters<S, R, T> params_,
                          final ItemCurveParams<R, T> curveParams_, final S toState_,
                          final double logLikelihood_, final double startingLL_, final int rowCount_)
    {
        FitResult<S, R, T> prevResult = new FitResult<>(startingParams_, startingLL_, rowCount_);
        _fitResult = new FitResult<>(params_, logLikelihood_, rowCount_, prevResult);


        _curveParams = curveParams_;
        _toState = toState_;
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

    public ItemParameters<S, R, T> getStartingParams()
    {
        return _fitResult.getPrev().getParams();
    }

    public ItemParameters<S, R, T> getModelParams()
    {
        return _fitResult.getParams();
    }

    public int getRowCount()
    {
        return _rowCount;
    }

    public double getStartingLogLikelihood()
    {
        return _fitResult.getPrev().getEntropy();
    }

    public double getLogLikelihood()
    {
        return _fitResult.getEntropy();
    }

    public double getImprovement()
    {
        return getStartingLogLikelihood() - getLogLikelihood();
    }

    public double improvementPerParameter()
    {
        return getImprovement() / getEffectiveParamCount();
    }

    public double aicPerParameter()
    {
        final double aic = calculateAicDifference();
        final double output = aic / getEffectiveParamCount();
        return output;
    }

    public int getEffectiveParamCount()
    {
        return _curveParams.getEffectiveParamCount();
    }

    public double calculateAicDifference()
    {
        final double aicDiff = MathFunctions.computeAicDifference(0,
                getEffectiveParamCount(), getStartingLogLikelihood(), getLogLikelihood(), _rowCount);
        return aicDiff;
    }

    @Override
    public String toString()
    {
        return "Fit result[" + getImprovement() + "]: \n" + _curveParams.toString();
    }

}
