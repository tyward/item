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

import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S> The status type for this fitter
 * @param <R> The regressor type for this fitter
 * @param <T> The curve type for this fitter
 */
public class BaseCurveFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends CurveFitter<S, R, T>
{
    private static final Logger LOG = LogUtil.getLogger(BaseCurveFitter.class);

    private final ItemCurveFactory<R, T> _factory;
    private final ItemSettings _settings;
    private final ItemStatusGrid<S, R> _grid;

    private ItemModel<S, R, T> _model;
    private CurveParamsFitter<S, R, T> _fitter;

    public BaseCurveFitter(final ItemCurveFactory<R, T> factory_, final ItemModel<S, R, T> model_, final ItemStatusGrid<S, R> grid_, final ItemSettings settings_, final R intercept_)
    {
        super(factory_, settings_, grid_);

        _fitter = new CurveParamsFitter<>(factory_, model_, grid_, settings_);

        if (null == intercept_)
        {
            throw new NullPointerException("Intercept cannot be null.");
        }

        _settings = settings_;
        _factory = factory_;
        _model = model_;
        _grid = grid_;

        _fitter = new CurveParamsFitter<>(_factory, model_, _grid, _settings);
    }

    private void setModel(final ItemModel<S, R, T> model_)
    {
        _model = model_;
        //_fitter = new CurveParamsFitter<>(_factory, _model, _grid, _settings);
        _fitter = new CurveParamsFitter<>(_fitter, _model.getParams());
    }

    @Override
    protected synchronized ItemParameters<S, R, T> getParams()
    {
        return _model.getParams();
    }

    @Override
    protected FitResult<S, R, T> findBest(T curveType_, R field_, S toStatus_) throws ConvergenceException
    {
        return _fitter.calibrateCurveAddition(curveType_, field_, toStatus_);
    }

    @Override
    public FitResult<S, R, T> fitEntryExpansion(final ItemParameters<S, R, T> params_, final ItemCurveParams<R, T> initParams_, S toStatus_,
            final boolean subtractStarting_, final double startingLL_) throws ConvergenceException
    {
        return _fitter.expandParameters(params_, initParams_, toStatus_, subtractStarting_, startingLL_);
    }

    @Override
    protected ItemModel<S, R, T> calibrateCurve(final int entryIndex_, final S toStatus_) throws ConvergenceException
    {
        FitResult<S, R, T> result = _fitter.calibrateExistingCurve(entryIndex_, toStatus_);

        if (null == result)
        {
            return _model;
        }

        ItemModel<S, R, T> outputModel = result.getModel();

        this.setModel(outputModel);
        return outputModel;
    }

}
