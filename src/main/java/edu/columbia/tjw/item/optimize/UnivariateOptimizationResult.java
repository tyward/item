/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.optimize.GeneralOptimizationResult;
import edu.columbia.tjw.item.optimize.EvaluationResult;

/**
 *
 * @author tyler
 */
public final class UnivariateOptimizationResult extends GeneralOptimizationResult<UnivariatePoint>
{
    public UnivariateOptimizationResult(final UnivariatePoint optimum_, final EvaluationResult minResult_, final boolean converged_, final int evalCount_)
    {
        super(optimum_, minResult_, converged_, evalCount_);
    }

    public double minX()
    {
        return this.getOptimum().getValue();
    }
}
