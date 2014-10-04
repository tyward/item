/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

/**
 *
 * @author tyler
 */
public interface OptimizationFunction<V extends EvaluationPoint<V>>
{
    public void value(final V input_, final int start_, final int end_, final EvaluationResult result_);

    public int numRows();

    public EvaluationResult generateResult(final int start_, final int end_);

    public EvaluationResult generateResult();

}
