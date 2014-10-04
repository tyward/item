/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.optimize.EvaluationResult;

/**
 *
 * @author tyler
 */
public interface MultivariateDifferentiableFunction extends MultivariateFunction
{

    public MultivariateGradient calculateDerivative(final MultivariatePoint input_, final EvaluationResult result_, final double precision_);
    
}
