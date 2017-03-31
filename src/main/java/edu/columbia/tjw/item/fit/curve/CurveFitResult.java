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

import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.MathFunctions;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public final class CurveFitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final double _startingLogL;
    private final double _logL;
    private final double _llImprovement;
    private final int _rowCount;
    private final S _toState;
    private final ItemParameters<S, R, T> _params;
    private final ItemCurveParams<R, T> _curveParams;

    public CurveFitResult(final ItemParameters<S, R, T> params_, final ItemCurveParams<R, T> curveParams_, final S toState_, final double logLikelihood_, final double startingLL_, final int rowCount_)
    {
        _params = params_;
        _curveParams = curveParams_;
        _toState = toState_;
        _logL = logLikelihood_;
        _llImprovement = (startingLL_ - _logL);
        _startingLogL = startingLL_;
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

    public ItemParameters<S, R, T> getModelParams()
    {
        return _params;
    }

    public double getStartingLogLikelihood()
    {
        return _startingLogL;
    }

    public double getLogLikelihood()
    {
        return _logL;
    }

    public double improvementPerParameter()
    {
        return _llImprovement / getEffectiveParamCount();
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
        final double scaledImprovement = _llImprovement * _rowCount;
        final double paramContribution = getEffectiveParamCount();
        final double aicDiff = 2.0 * (paramContribution - scaledImprovement);

        final double check = MathFunctions.computeAicDifference(0, getEffectiveParamCount(), _startingLogL, _logL, _rowCount);

        if (Math.abs(check - aicDiff) > 1.0e-7)
        {
            final double check2 = MathFunctions.computeAicDifference(0, getEffectiveParamCount(), _startingLogL, _logL, _rowCount);
            throw new IllegalStateException("Impossible.");
        }

        return aicDiff;
    }

    @Override
    public String toString()
    {
        return "Fit result[" + _llImprovement + "]: \n" + _curveParams.toString();
    }

}
