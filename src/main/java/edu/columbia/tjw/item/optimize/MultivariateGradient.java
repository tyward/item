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
public class MultivariateGradient
{
    private final MultivariatePoint _gradient;
    private final MultivariatePoint _secondDerivative;
    private final MultivariatePoint _center;
    private final double _stdDev;

    public MultivariateGradient(final MultivariatePoint center_, final MultivariatePoint gradient_, final MultivariatePoint secondDerivative_, final double stdDev_)
    {
        _gradient = new MultivariatePoint(gradient_);
        _center = new MultivariatePoint(center_);

        if (null == secondDerivative_)
        {
            _secondDerivative = null;
        }
        else
        {
            _secondDerivative = new MultivariatePoint(secondDerivative_);
        }

        _stdDev = stdDev_;
    }

    public MultivariatePoint getSecondDerivative()
    {
        return _secondDerivative;
    }

    public MultivariatePoint getGradient()
    {
        return _gradient;
    }

    public MultivariatePoint getCenter()
    {
        return _center;
    }

    public double getStdDev()
    {
        return _stdDev;
    }
}
