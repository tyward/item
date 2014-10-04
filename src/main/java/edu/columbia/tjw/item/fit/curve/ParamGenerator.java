/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.fit.curve;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public interface ParamGenerator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    public ItemModel<S, R, T> generatedModel(final double[] params_, final R field_);

    public ItemCurve<?> generateTransformation(final double[] params_);

    public double getInterceptAdjustment(final double[] params_);

    public double getBeta(final double[] params_);

    public double[] getStartingParams(final double regressorMean_, final double regressorStdDev_);

    public int paramCount();

    public int getInterceptParamNumber();

    public int getBetaParamNumber();

    /**
     * Translates from the parameter number (as known by the underlying
     * transformation) to the parameter number corresponding in the params
     * passed to this generator.
     *
     * @param input_
     * @return
     */
    public int translateParamNumber(final int input_);

}
