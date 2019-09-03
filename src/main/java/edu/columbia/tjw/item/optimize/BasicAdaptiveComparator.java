/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 *
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.fit.calculator.FitPoint;
import edu.columbia.tjw.item.fit.calculator.FitPointAnalyzer;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.logging.Logger;

/**
 * @param <V> The type of points on which this can be evaluated
 * @param <F> The type of optimization function which will be called
 * @author tyler
 */
public class BasicAdaptiveComparator<V extends EvaluationPoint<V>, F extends OptimizationFunction<V>> implements AdaptiveComparator<V, F>
{
    private static final Logger LOG = LogUtil.getLogger(BasicAdaptiveComparator.class);
    private final int _blockSize;
    private final double _stdDevThreshold;
    private final FitPointAnalyzer _analyzer;


    public BasicAdaptiveComparator(final int blockSize_, final double stdDevThreshold_)
    {
        _blockSize = blockSize_;
        _stdDevThreshold = stdDevThreshold_;
        _analyzer = new FitPointAnalyzer();
    }

    @Override
    public double compare(final FitPoint pointA_, final FitPoint pointB_)
    {
        if (pointA_ == pointB_)
        {
            return 0.0;
        }

        final double check = _analyzer.compareEntropies(pointA_, pointB_, _stdDevThreshold);
        return check;
    }

    @Override
    public double getSigmaTarget()
    {
        return this._stdDevThreshold;
    }

}
