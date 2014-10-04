/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item;

import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemCurve;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public interface ParamFilter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{

    public boolean isFiltered(final S fromStatus_, final S toStatus_, final R field_, final ItemCurve<T> trans_);

}
