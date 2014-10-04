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

import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemGridFactory;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.fit.curve.CurveFitter;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import java.util.Collection;
import java.util.Set;

/**
 *
 * A class designed to expand the model by adding curves.
 *
 * In addition, it may be used to fit only coefficients if needed.
 *
 *
 * @author tyler
 * @param <S> The status type
 * @param <R> The regressor type
 * @param <T> The curve type
 */
public final class ItemFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final ItemCurveFactory<T> _factory;

    public ItemFitter(final ItemCurveFactory<T> factory_)
    {
        _factory = factory_;
    }

    public ItemModel<S, R, T> fitCoefficients(final ItemParameters<S, R, T> params_, final ItemFittingGrid<S, R> fittingGrid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        final ItemModel<S, R, T> model = new ItemModel<>(params_);
        final ParamFitter<S, R, T> fitter = new ParamFitter<>(model);

        final ItemModel<S, R, T> m2 = fitter.fit(fittingGrid_, null);

        if (null == m2)
        {
            throw new ConvergenceException("Unable to improve parameter fit.");
        }

        return m2;
    }

    public ItemModel<S, R, T> expandModel(final ItemParameters<S, R, T> params_, final ItemGridFactory<S, R, T> gridFactory_, final Set<R> curveFields_, final Collection<ParamFilter<S, R, T>> filters_, final int curveCount_)
    {
        ItemModel<S, R, T> model = new ItemModel<>(params_);
        ItemFittingGrid<S, R> grid = gridFactory_.prepareGrid(params_);

        for (int i = 0; i < curveCount_; i++)
        {
            try
            {
                model = fitCoefficients(params_, grid, filters_);
            }
            catch (final ConvergenceException e)
            {
                //Ignore this exception, just move on.
            }

            final CurveFitter<S, R, T> fitter = new CurveFitter<>(_factory, model, grid);

            try
            {
                model = fitter.generateCurve(curveFields_, filters_);
            }
            catch (final ConvergenceException e)
            {
                break;
            }
        }

        return model;
    }
}
