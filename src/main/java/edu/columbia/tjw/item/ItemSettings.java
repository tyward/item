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
 *
 * @author tyler
 */
public final class ItemSettings implements Serializable
{
    private static final double STANDARD_AIC_CUTOFF = -5.0;
    private static final int BLOCK_SIZE = 200 * 1000;
    private static final int THREAD_BLOCK_SIZE = 10 * 1000;
    private static final int POLISH_MULTI_START_POINTS = 20;

    private static final long serialVersionUID = 6850856502170239624L;

    private final Random _rand;
    private final boolean _randomShuffle;
    private final boolean _useThreading;
    private final boolean _approximateDerivatives;
    private final boolean _polishStartingParams;
    private final int _polishMultStartPoints;

    private final double _aicCutoff;

    private final int _blockSize;
    private final int _threadBlockSize;

    public ItemSettings()
    {
        this(true, STANDARD_AIC_CUTOFF, RandomTool.getRandom(PrngType.STANDARD), BLOCK_SIZE, THREAD_BLOCK_SIZE, true, false);
    }

    public ItemSettings(final boolean randomShuffle_, final double aicCutoff_, final Random rand_, final int blockSize_, final int threadBlockSize_, final boolean useThreading_, final boolean polishStartingParams_)
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
    }

    public int getPolishMultiStartPoints()
    {
        return _polishMultStartPoints;
    }

    public boolean getPolishStartingParams()
    {
        return _polishStartingParams;
    }

    public boolean getUseThreading()
    {
        return _useThreading;
    }

    public int getBlockSize()
    {
        return _blockSize;
    }

    public int getThreadBlockSize()
    {
        return _threadBlockSize;
    }

    public double getAicCutoff()
    {
        return _aicCutoff;
    }

    public boolean isRandomShuffle()
    {
        return _randomShuffle;
    }

    public Random getRandom()
    {
        return _rand;
    }

    public boolean approximateDerivatives()
    {
        return _approximateDerivatives;
    }
}
