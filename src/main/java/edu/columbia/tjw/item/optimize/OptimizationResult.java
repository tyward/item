/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.item.rigel.optimize.EvaluationPoint;

/**
 *
 * @author tyler
 */
public interface OptimizationResult<V extends EvaluationPoint<V>>
{
    public V getOptimum();

    public boolean converged();

    public int evaluationCount();

    public double minValue();

    public EvaluationResult minResult();

    public int dataElementCount();

}
