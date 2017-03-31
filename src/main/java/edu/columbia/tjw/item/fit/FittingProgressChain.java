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
package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.fit.curve.CurveFitResult;
import edu.columbia.tjw.item.fit.param.ParamFitResult;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.MathFunctions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public final class FittingProgressChain<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(FittingProgressChain.class);

    private final List<ParamProgressFrame<S, R, T>> _frameList;
    private final List<ParamProgressFrame<S, R, T>> _frameListReadOnly;
    private final int _rowCount;

    /**
     * Start a new chain from the latest entry of the current chain.
     *
     * @param baseChain_
     */
    public FittingProgressChain(final FittingProgressChain<S, R, T> baseChain_)
    {
        this(baseChain_.getBestParameters(), baseChain_.getLogLikelihood(), baseChain_.getRowCount());
    }

    public FittingProgressChain(final ItemParameters<S, R, T> fitResult_, final double startingLL_, final int rowCount_)
    {
        if (rowCount_ <= 0)
        {
            throw new IllegalArgumentException("Data set cannot be empty.");
        }

        _rowCount = rowCount_;
        final ParamProgressFrame<S, R, T> frame = new ParamProgressFrame<>(fitResult_, startingLL_, null, _rowCount);

        _frameList = new ArrayList<>();
        _frameList.add(frame);
        _frameListReadOnly = Collections.unmodifiableList(_frameList);

    }

    public boolean pushResults(final CurveFitResult<S, R, T> curveResult_)
    {
        return this.pushResults(curveResult_.getModelParams(), curveResult_.getLogLikelihood());
    }

    public boolean pushResults(final ParamFitResult<S, R, T> fitResult_)
    {
        return this.pushResults(fitResult_.getEndingParams(), fitResult_.getEndingLL());
    }

    public boolean pushVacuousResults(final ItemParameters<S, R, T> fitResult_)
    {
        final double currentBest = getLogLikelihood();
        final ParamProgressFrame<S, R, T> frame = new ParamProgressFrame<>(fitResult_, currentBest, getLatestFrame(), _rowCount);
        _frameList.add(frame);
        return true;
    }

    public boolean pushResults(final ItemParameters<S, R, T> fitResult_, final double logLikelihood_)
    {
        final double currentBest = getLogLikelihood();

        // These are negative log likelihoods (positive numbers), a lower number is better.
        // So compare must be < 0, best must be more than the new value.
        final double compare = MathFunctions.doubleCompareRounded(currentBest, logLikelihood_);

        if (compare >= 0)
        {
            LOG.info("Discarding results, likelihood did not improve: " + currentBest + " -> " + logLikelihood_);
            return false;
        }

        LOG.info("Log Likelihood improvement: " + currentBest + " -> " + logLikelihood_);

        //This is an improvement. 
        final ParamProgressFrame<S, R, T> frame = new ParamProgressFrame<>(fitResult_, logLikelihood_, getLatestFrame(), _rowCount);
        _frameList.add(frame);
        return true;
    }

    public int getRowCount()
    {
        return _rowCount;
    }

    public int size()
    {
        return _frameList.size();
    }

    public double getLogLikelihood()
    {
        return getLatestFrame().getCurrentLogLikelihood();
    }

    public ItemParameters<S, R, T> getBestParameters()
    {
        return getLatestFrame().getCurrentParams();
    }

    /**
     * Gets the fitting results taking the start of the chain as the starting
     * point. So how much total progress was made over the course of the whole
     * chain.
     *
     * @return
     */
    public ParamFitResult<S, R, T> getConsolidatedResults()
    {
        final ParamProgressFrame<S, R, T> startFrame = _frameList.get(0);
        final ParamProgressFrame<S, R, T> endFrame = getLatestFrame();

        final ParamFitResult<S, R, T> output = new ParamFitResult<>(startFrame.getCurrentParams(), endFrame.getCurrentParams(), endFrame.getCurrentLogLikelihood(), startFrame.getCurrentLogLikelihood(), _rowCount);
        return output;
    }

    public ParamFitResult<S, R, T> getLatestResults()
    {
        return getLatestFrame().getFitResults();
    }

    public ParamProgressFrame<S, R, T> getLatestFrame()
    {
        return _frameListReadOnly.get(_frameListReadOnly.size() - 1);
    }

    public List<ParamProgressFrame<S, R, T>> getFrameList()
    {
        return _frameListReadOnly;
    }

    public final class ParamProgressFrame<S1 extends ItemStatus<S1>, R1 extends ItemRegressor<R1>, T1 extends ItemCurveType<T1>>
    {
        private final ItemParameters<S1, R1, T1> _current;
        private final double _currentLL;
        private final ParamProgressFrame<S1, R1, T1> _startingPoint;
        private final ParamFitResult<S1, R1, T1> _fitResult;

        private ParamProgressFrame(final ItemParameters<S1, R1, T1> current_, final double currentLL_, final ParamProgressFrame<S1, R1, T1> startingPoint_, final int rowCount_)
        {
            if (null == current_)
            {
                throw new NullPointerException("Parameters cannot be null.");
            }
            if (Double.isNaN(currentLL_) || Double.isInfinite(currentLL_) || currentLL_ < 0.0)
            {
                throw new IllegalArgumentException("Log Likelihood must be well defined.");
            }

            _current = current_;
            _currentLL = currentLL_;
            _startingPoint = startingPoint_;

            if (null == _startingPoint)
            {
                //This is basically a loopback fit result, but it's properly formed, so should be OK.
                _fitResult = new ParamFitResult<>(current_, current_, currentLL_, currentLL_, _rowCount);
            }
            else
            {
                _fitResult = new ParamFitResult<>(_startingPoint.getCurrentParams(), current_, currentLL_, _startingPoint.getCurrentLogLikelihood(), _rowCount);
            }
        }

        public ParamFitResult<S1, R1, T1> getFitResults()
        {
            return _fitResult;
        }

        public ItemParameters<S1, R1, T1> getCurrentParams()
        {
            return _current;
        }

        public double getCurrentLogLikelihood()
        {
            return _currentLL;
        }

        public ParamProgressFrame<S1, R1, T1> getStartingPoint()
        {
            return _startingPoint;
        }

    }

}
