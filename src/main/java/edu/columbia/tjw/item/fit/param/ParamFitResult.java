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

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.util.MathFunctions;

/**
 * @param <S>
 * @param <R>
 * @param <T>
 * @author tyler
 */
public final class ParamFitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final int _rowCount;
    private final FitResult<S, R, T> _fitResult;

    public ParamFitResult(final FitResult<S, R, T> fitResult_, final int rowCount_)
    {
        _fitResult = fitResult_;
        _rowCount = rowCount_;
    }

//    public ParamFitResult(final ItemParameters<S, R, T> starting_, final ItemParameters<S, R, T> ending_,
//                          final double logLikelihood_, final double startingLL_, final int rowCount_)
//    {
//        final FitResult<S, R, T> starting = new FitResult<>(starting_, startingLL_, rowCount_);
//        final FitResult<S, R, T> ending;
//
//        if (starting_ == ending_)
//        {
//            //Don't let minor rounding errors throw us off.
//            ending = new FitResult<>(ending_, startingLL_, rowCount_, starting);
//        }
//        else
//        {
//            ending = new FitResult<>(ending_, logLikelihood_, rowCount_, starting);
//        }
//
//        _fitResult = ending;
//        _rowCount = rowCount_;
//    }

    public FitResult<S, R, T> getFitResult()
    {
        return _fitResult;
    }

    public boolean isBetter()
    {
        return MathFunctions.isAicWorse(getEndingLL(), getStartingLL());
    }

    public boolean isUnchanged()
    {
        return (getEndingLL() == getStartingLL());
    }

    public double getAic()
    {
        final double scaledImprovement = getLLImprovement() * _rowCount;
        final double paramContribution =
                (getEndingParams().getEffectiveParamCount() - getStartingParams()
                        .getEffectiveParamCount());
        final double aicDiff = 2.0 * (paramContribution - scaledImprovement);
        return aicDiff;
    }

    public double getStartingLL()
    {
        return _fitResult.getPrev().getEntropy();
    }

    public double getEndingLL()
    {
        return _fitResult.getEntropy();
    }

    public double getLLImprovement()
    {
        final double llImprovement = getEndingLL() - getStartingLL();
        return llImprovement;
    }

    public ItemParameters<S, R, T> getStartingParams()
    {
        return _fitResult.getPrev().getParams();
    }

    public ItemParameters<S, R, T> getEndingParams()
    {
        return _fitResult.getParams();
    }

}
