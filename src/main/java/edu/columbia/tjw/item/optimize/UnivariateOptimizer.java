/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.optimize.UnivariateFunction;
import edu.columbia.tjw.item.optimize.GoldenSectionOptimizer;

/**
 *
 * @author tyler
 */
public class UnivariateOptimizer extends GoldenSectionOptimizer<UnivariatePoint, UnivariateFunction>
{

    public UnivariateOptimizer(final int blockSize_, int maxEvalCount_)
    {
        super(blockSize_, maxEvalCount_);
    }

}
