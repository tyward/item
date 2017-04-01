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
package edu.columbia.tjw.item;

import edu.columbia.tjw.item.util.random.PrngType;
import edu.columbia.tjw.item.util.random.RandomTool;
import java.io.Serializable;
import java.util.Random;

/**
 *
 * Some general settings that control how curves are constructed and fit.
 *
 * Generally, don't change these unless you know what you're doing.
 *
 * Also, all the members are final so that this thing is threadsafe. Use the
 * with* methods if you need to get settings with non-default values.
 *
 * One word of warning, these will share a single Random, typically this will
 * still be threadsafe, but not always. Be careful about that.
 *
 * @author tyler
 */
public final class ItemSettings implements Serializable
{
    private static final double STANDARD_AIC_CUTOFF = -5.0;
    private static final int BLOCK_SIZE = 200 * 1000;
    private static final int THREAD_BLOCK_SIZE = 10 * 1000;
    private static final int POLISH_MULTI_START_POINTS = 20;
    private static final boolean ALLOW_INTERACTION_CURVES = true;
    private static final boolean DEFAULT_USE_THREADING = true;
    private static final boolean DEFAULT_POLISH_STARTING_PARAMS = true;
    private static final boolean CENTRALITY_BOUND = true;
    private static final double Z_SCORE_CUTOFF = 1.0;
    private static final boolean DEFAULT_VALIDATE = true;

    private static final long serialVersionUID = 0x2bc96e9485221bf6L;

    private final Random _rand;
    private final boolean _randomShuffle;
    private final boolean _useThreading;
    private final boolean _approximateDerivatives;
    private final boolean _polishStartingParams;
    private final boolean _allowInteractionCurves;
    private final boolean _boundCentrality;
    private final int _polishMultStartPoints;

    private final int _minCalibrationCount = 1;
    private final double _improvementRatio = 0.2;
    private final double _exhaustiveImprovementLimit = 0.05;

    private final double _aicCutoff;

    //The minimum Z-score such that we will consider two results different. 
    // i.e. two points must differ by at least _zScoreCutoff std deviations to 
    // be considered meaningfully different. Use a number between 1 - 5 here. 1.0 
    // for minimal certainty, 5.0 for proof strong enough to be considered a scientific discovery... (less than 1 in a million to happen by chance)
    private final double _zScoreCutoff;

    private final int _blockSize;
    private final int _threadBlockSize;

    private final boolean _validate;

    public ItemSettings()
    {
        this(true, STANDARD_AIC_CUTOFF, RandomTool.getRandom(PrngType.STANDARD), BLOCK_SIZE, THREAD_BLOCK_SIZE, DEFAULT_USE_THREADING, DEFAULT_POLISH_STARTING_PARAMS, ALLOW_INTERACTION_CURVES);
    }

    public ItemSettings(final boolean randomShuffle_, final double aicCutoff_, final Random rand_, final int blockSize_, final int threadBlockSize_, final boolean useThreading_,
            final boolean polishStartingParams_, final boolean allowInteractionCurves_)
    {
        if (aicCutoff_ > 0.0)
        {
            throw new IllegalArgumentException("The AIC cutoff must be negative: " + aicCutoff_);
        }

        if (threadBlockSize_ < 100)
        {
            throw new IllegalArgumentException("Thread block size should not be too small: " + threadBlockSize_);
        }
        if (blockSize_ < threadBlockSize_)
        {
            throw new IllegalArgumentException("Block size cannot be less than ThreadBlockSize: " + blockSize_);
        }

        _rand = rand_;
        _randomShuffle = randomShuffle_;
        _aicCutoff = aicCutoff_;
        _blockSize = blockSize_;
        _threadBlockSize = threadBlockSize_;
        _useThreading = useThreading_;
        _approximateDerivatives = false;
        _polishStartingParams = polishStartingParams_;
        _polishMultStartPoints = POLISH_MULTI_START_POINTS;
        _allowInteractionCurves = allowInteractionCurves_;
        _boundCentrality = CENTRALITY_BOUND;

        _zScoreCutoff = Z_SCORE_CUTOFF;
        _validate = DEFAULT_VALIDATE;
    }

    public double getExhaustiveImprovementLimit()
    {
        return _exhaustiveImprovementLimit;
    }

    public int getCalibrateSize()
    {
        return _minCalibrationCount;
    }

    public double getImprovementRatio()
    {
        return _improvementRatio;
    }

    public boolean getBoundCentrality()
    {
        return _boundCentrality;
    }

    public int getPolishMultiStartPoints()
    {
        return _polishMultStartPoints;
    }

    public boolean getDoValidate()
    {
        return _validate;
    }

    public ItemSettings withAllowInteractionCurves(final boolean allowInteractionCurves_)
    {
        //return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(), this.getPolishStartingParams());
        return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(),
                this.getPolishStartingParams(), this.getAllowInteractionCurves());
    }

    public boolean getAllowInteractionCurves()
    {
        return _allowInteractionCurves;
    }

    public ItemSettings withPolishStartingParams(final boolean polishStartingParams_)
    {
        return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(),
                polishStartingParams_, this.getAllowInteractionCurves());
    }

    public boolean getPolishStartingParams()
    {
        return _polishStartingParams;
    }

    public ItemSettings withUseThreading(final boolean useThreading_)
    {
        //return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(), this.getPolishStartingParams());
        return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), useThreading_,
                this.getPolishStartingParams(), this.getAllowInteractionCurves());
    }

    public boolean getUseThreading()
    {
        return _useThreading;
    }

    public ItemSettings withBlockSize(final int blockSize_)
    {
        //return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(), this.getPolishStartingParams());
        return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), blockSize_, this.getThreadBlockSize(), this.getUseThreading(),
                this.getPolishStartingParams(), this.getAllowInteractionCurves());
    }

    public int getBlockSize()
    {
        return _blockSize;
    }

    public ItemSettings withThreadBlockSize(final int threadBlockSize_)
    {
        //return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(), this.getPolishStartingParams());
        return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), threadBlockSize_, this.getUseThreading(),
                this.getPolishStartingParams(), this.getAllowInteractionCurves());
    }

    public int getThreadBlockSize()
    {
        return _threadBlockSize;
    }

    public ItemSettings withAicCutoff(final double cutoff_)
    {
        //return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(), this.getPolishStartingParams());
        return new ItemSettings(this.isRandomShuffle(), cutoff_, this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(),
                this.getPolishStartingParams(), this.getAllowInteractionCurves());
    }

    public double getAicCutoff()
    {
        return _aicCutoff;
    }

    public ItemSettings withRandomShuffle(final boolean randomShuffle_)
    {
        //return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(), this.getPolishStartingParams());
        return new ItemSettings(randomShuffle_, this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(),
                this.getPolishStartingParams(), this.getAllowInteractionCurves());
    }

    public boolean isRandomShuffle()
    {
        return _randomShuffle;
    }

    public ItemSettings withRandom(final Random random_)
    {
        //return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), this.getRandom(), this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(), this.getPolishStartingParams());
        return new ItemSettings(this.isRandomShuffle(), this.getAicCutoff(), random_, this.getBlockSize(), this.getThreadBlockSize(), this.getUseThreading(),
                this.getPolishStartingParams(), this.getAllowInteractionCurves());
    }

    public Random getRandom()
    {
        return _rand;
    }

    public double getZScoreCutoff()
    {
        return _zScoreCutoff;
    }

    public boolean approximateDerivatives()
    {
        return _approximateDerivatives;
    }
}
