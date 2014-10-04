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
public interface EvaluationPoint<V extends EvaluationPoint<V>>
        extends Cloneable
{

    /**
     * Returns the scalar that when used to scalar multiply input_, produces the projection of this onto input.
     * 
     * @param input_
     * @return 
     */
    public double project(final V input_);
    
    public double getMagnitude();

    public double distance(final V point_);

    public void scale(final double input_);

    public void copy(final V point_);
    
    public void add(final V point_);
    
    public void normalize();

    public V clone();

}
