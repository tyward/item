/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.fit.param;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ParamFilter;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import java.util.Collection;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public class MultiLogitParamFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> extends ParamFitter<S, R, T>
{
    public MultiLogitParamFitter(final ItemModel<S, R, T> model_)
    {
        super(model_);
    }

    public ItemModel<S, R, T> fit(final ItemFittingGrid<S, R> grid_, final Collection<ParamFilter<S, R, T>> filters_) throws ConvergenceException
    {
        return (ItemModel<S, R, T>) super.fit(grid_, filters_);
    }

}
