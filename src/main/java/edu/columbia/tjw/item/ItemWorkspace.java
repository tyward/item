/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item;

import edu.columbia.tjw.item.ItemStatus;

/**
 *
 * @author tyler
 * @param <S>
 */
public final class ItemWorkspace<S extends ItemStatus<S>>
{

    private final double[] _regWorkspace;
    private final double[] _probWorkspace;
    private final double[] _actualProbWorkspace;

    public ItemWorkspace(final S status_, final int regressorCount_)
    {
        _regWorkspace = new double[regressorCount_];
        _probWorkspace = new double[status_.getReachable().size()];
        _actualProbWorkspace = new double[status_.getReachable().size()];
    }

    public double[] getRegressorWorkspace()
    {
        return _regWorkspace;
    }

    public double[] getComputedProbabilityWorkspace()
    {
        return _probWorkspace;
    }

    public double[] getActualProbabilityWorkspace()
    {
        return _actualProbWorkspace;
    }

}
