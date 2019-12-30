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
import edu.columbia.tjw.item.fit.calculator.BlockResult;
import edu.columbia.tjw.item.fit.curve.CurveFitResult;
import edu.columbia.tjw.item.util.LogUtil;
import edu.columbia.tjw.item.util.MathFunctions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * @param <S>
 * @param <R>
 * @param <T>
 * @author tyler
 */
public final class FittingProgressChain<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(FittingProgressChain.class);

    private final String _chainName;
    private final List<ParamProgressFrame<S, R, T>> _frameList;
    private final List<ParamProgressFrame<S, R, T>> _frameListReadOnly;
    private final int _rowCount;
    private final EntropyCalculator<S, R, T> _calc;
    private final boolean _validate;

    /**
     * Start a new chain from the latest entry of the current chain.
     *
     * @param chainName_
     * @param baseChain_
     */
    public FittingProgressChain(final String chainName_, final FittingProgressChain<S, R, T> baseChain_)
    {
        this(chainName_, baseChain_.getLatestResults(),
                baseChain_.getRowCount(),
                baseChain_._calc, baseChain_.isValidate());
    }

    public FittingProgressChain(final String chainName_, final FitResult<S, R, T> fitResult_,
                                final int rowCount_, final EntropyCalculator<S, R, T> calc_
            , final boolean validating_)
    {
        if (rowCount_ <= 0)
        {
            throw new IllegalArgumentException("Data set cannot be empty.");
        }

        this.validate(fitResult_);

        _chainName = chainName_;
        _rowCount = rowCount_;
        final ParamProgressFrame<S, R, T> frame = new ParamProgressFrame<>("Initial", fitResult_, null);

        _frameList = new ArrayList<>();
        _frameList.add(frame);
        _frameListReadOnly = Collections.unmodifiableList(_frameList);
        _calc = calc_;
        _validate = validating_;
    }

    public String getName()
    {
        return _chainName;
    }

    public boolean pushResults(final String frameName_, final CurveFitResult<S, R, T> curveResult_)
    {
        return this.pushResults(frameName_, curveResult_.getFitResult());
    }

    private synchronized void validate(final FitResult<S, R, T> fitResult_)
    {
        if (this.isValidate())
        {
            //Since the claim is that the LL improved, let's see if that's true...
            final BlockResult ea = _calc.computeEntropy(fitResult_.getParams());
            final double entropy = ea.getEntropyMean();

            //LOG.info("Params: " + fitResult_.hashCode() + " -> " + entropy);
            //LOG.info("Chain: " + this.toString());
            final int compare = MathFunctions.doubleCompareRounded(entropy, fitResult_.getEntropy());

            if (compare != 0)
            {
                //System.out.println("Found entropy mismatch: " + entropy + " != " + fitResult_.getEntropy());
                throw new IllegalStateException(
                        "Found entropy mismatch: " + entropy + " != " + fitResult_.getEntropy());
            }

            final int entropyCompare = MathFunctions.doubleCompareRounded(fitResult_.getPrev().getEntropy(),
                    this.getLatestResults().getEntropy());

            if (entropyCompare != 0)
            {
                //System.out.println("Found entropy mismatch: " + entropy + " != " + fitResult_.getEntropy());
                throw new IllegalStateException(
                        "Found entropy mismatch: " + entropy + " != " + fitResult_.getEntropy());
            }
        }
    }

    /**
     * Forces the given results onto the stack, regardless of quality...
     *
     * @param frameName_
     * @param fitResult_
     */
    public void forcePushResults(final String frameName_, final ItemParameters<S, R, T> fitResult_)
    {
        final FitResult<S, R, T> result = _calc.computeFitResult(fitResult_, this.getLatestResults());
        LOG.info("Force pushing params onto chain[" + result.getEntropy() + "]");
        final ParamProgressFrame<S, R, T> frame = new ParamProgressFrame<>(frameName_, result,
                getLatestFrame());
        _frameList.add(frame);
    }

    public boolean pushVacuousResults(final String frameName_, final ItemParameters<S, R, T> fitResult_)
    {
        final FitResult<S, R, T> result = _calc.computeFitResult(fitResult_, this.getLatestFrame().getFitResults());
        final ParamProgressFrame<S, R, T> frame = new ParamProgressFrame<>(frameName_, result,
                getLatestFrame());
        _frameList.add(frame);
        return true;
    }


    public boolean pushResults(final String frameName_, final FitResult<S, R, T> fitResult_)
    {
        final double currentBest = getLogLikelihood();

        this.validate(fitResult_);

        final double prevAic = this.getLatestResults().getAic();
        final double newAic = fitResult_.getAic();
        final double aicDifference = newAic - prevAic;
        final int compare = MathFunctions.doubleCompareRounded(prevAic, newAic);

        if (compare >= 0)
        {
            LOG.info(
                    "Discarding results, likelihood did not improve[" + aicDifference + "]: " + currentBest + " -> " + fitResult_
                            .getEntropy());
            return false;
        }

        LOG.info(
                "Log Likelihood improvement[" + frameName_ + "][" + aicDifference + "]: " + currentBest + " -> " + fitResult_
                        .getEntropy());

        if (aicDifference >= -5.0)
        {
            LOG.info("Insufficient AIC, discarding results: " + aicDifference);
            return false;
        }

        //This is an improvement. 
        final ParamProgressFrame<S, R, T> frame = new ParamProgressFrame<>(frameName_, fitResult_,
                getLatestFrame());
        _frameList.add(frame);

        LOG.info("Current chain: " + this.toString());

        return true;
    }

    public int getRowCount()
    {
        return _rowCount;
    }

    public boolean isValidate()
    {
        return _validate;
    }

    public EntropyCalculator<S, R, T> getCalculator()
    {
        return _calc;
    }

    public int size()
    {
        return _frameList.size();
    }

    public double getLogLikelihood()
    {
        return getLatestFrame().getCurrentLogLikelihood();
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();

        builder.append(this.getClass().getName());
        builder.append(" {\n chainName: " + _chainName + " \n");
        builder.append(" size: " + this._frameList.size());

        for (int i = 0; i < _frameList.size(); i++)
        {
            builder.append("\n\t frame[" + i + "]: " + _frameList.get(i).toString());
        }

        builder.append("\n}");

        return builder.toString();
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
    public FitResult<S, R, T> getConsolidatedResults()
    {
        final ParamProgressFrame<S, R, T> startFrame = _frameList.get(0);
        final ParamProgressFrame<S, R, T> endFrame = getLatestFrame();

        final FitResult<S, R, T> fitResult = new FitResult<>(endFrame.getFitResults(), startFrame.getFitResults());

        return fitResult;
    }

    public double getChainAicDiff()
    {
        return this.getAicDiff(_frameList.get(0), this.getLatestFrame());
    }

    public FitResult<S, R, T> getLatestResults()
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

    //
    public <S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>> double getAicDiff(
            final ParamProgressFrame<S, R, T> frame1_, final ParamProgressFrame<S, R, T> frame2_)
    {
        if (frame1_ == null)
        {
            return frame2_.getFitResults().getAic();
        }

        final double aicDiff = frame2_.getFitResults().getAic() - frame1_.getFitResults().getAic();
        return aicDiff;
    }

    public final class ParamProgressFrame<S1 extends ItemStatus<S1>, R1 extends ItemRegressor<R1>,
            T1 extends ItemCurveType<T1>>
    {
        private final ParamProgressFrame<S1, R1, T1> _startingPoint;
        private final FitResult<S1, R1, T1> _fitResult;
        private final long _entryTime;
        private final String _frameName;

        private ParamProgressFrame(final String frameName_, final FitResult<S1, R1, T1> current_,
                                   final ParamProgressFrame<S1, R1, T1> startingPoint_)
        {
            if (null == current_)
            {
                throw new NullPointerException("Parameters cannot be null.");
            }

            _frameName = frameName_;

            _entryTime = System.currentTimeMillis();

            if (null == startingPoint_)
            {
                _startingPoint = this;
                // This will take care of what would otherwise be some null pointer issues.
                _fitResult = new FitResult<>(current_, current_);
            }
            else
            {
                _fitResult = current_;
                _startingPoint = startingPoint_;
            }


            // Validate that the prev result from the fit result actually matches the starting point?
            if (null != startingPoint_ && MathFunctions
                    .doubleCompareRounded(startingPoint_.getFitResults().getEntropy(),
                            _fitResult.getPrev().getEntropy()) != 0)
            {
                throw new IllegalArgumentException("Mismatched starting points.");
            }
        }

        public long getElapsed()
        {
            final long prevEntry = _startingPoint.getEntryTime();
            final long elapsed = _entryTime - prevEntry;
            return elapsed;
        }

        public long getEntryTime()
        {
            return _entryTime;
        }

        public FitResult<S1, R1, T1> getFitResults()
        {
            return _fitResult;
        }

        public ItemParameters<S1, R1, T1> getCurrentParams()
        {
            return _fitResult.getParams();
        }

        public double getCurrentLogLikelihood()
        {
            return _fitResult.getEntropy();
        }

        public ParamProgressFrame<S1, R1, T1> getStartingPoint()
        {
            return _startingPoint;
        }

        public double getAicDiff()
        {
            return FittingProgressChain.this.getAicDiff(this.getStartingPoint(), this);
        }

        public int getRowCount()
        {
            return _rowCount;
        }

        @Override
        public String toString()
        {
            final StringBuilder builder = new StringBuilder();

            builder.append(
                    "frame[" + _frameName + "][" + this.getAicDiff() + "] {" + getCurrentLogLikelihood() + ", " + this
                            .getElapsed() + "}");

            return builder.toString();
        }

    }

}
