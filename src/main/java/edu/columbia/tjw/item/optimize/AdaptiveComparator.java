/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.item.rigel.optimize.EvaluationPoint;

/**
 *
 * @author tyler
 */
public interface AdaptiveComparator<V extends EvaluationPoint<V>, F extends OptimizationFunction<V>>
{
    /**
     * Return (aRes - bRes) as a zScore.
     * @param function_
     * @param a_
     * @param b_
     * @param aResult_
     * @param bResult_
     * @return 
     */
    public double compare(final F function_, final V a_, final V b_, final EvaluationResult aResult_, final EvaluationResult bResult_);
    
    public double getSigmaTarget();

}
