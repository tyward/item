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
public class MultivariateOptimizationResult extends GeneralOptimizationResult<MultivariatePoint>
{
    public MultivariateOptimizationResult(final MultivariatePoint minX_, final EvaluationResult minValue_, final boolean converged_, final int evalCount_)
    {
        super(minX_, minValue_, converged_, evalCount_);
    }

}
