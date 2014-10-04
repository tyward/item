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
public interface MultivariateFunction extends OptimizationFunction<MultivariatePoint>
{
    public int dimension();

}
