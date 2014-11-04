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
 * @author tyler
 */
public final class ItemSettings implements Serializable
{
    private static final double STANDARD_AIC_CUTOFF = -5.0;
    
    private final Random _rand;
    private final boolean _randomScale;
    private final boolean _randomShuffle;
    private final boolean _randomCentrality;
    private final double _aicCutoff;

    public ItemSettings()
    {
        this(true, true, true, STANDARD_AIC_CUTOFF, RandomTool.getRandom(PrngType.STANDARD));
    }

    public ItemSettings(final boolean randomScale_, final boolean randomShuffle_, final boolean randomCentrality_, final double aicCutoff_, final Random rand_)
    {
        if(aicCutoff_ > 0.0)
        {
            throw new IllegalArgumentException("The AIC cutoff must be negative: " + aicCutoff_);
        }
        
        _rand = rand_;
        _randomScale = randomScale_;
        _randomShuffle = randomShuffle_;
        _randomCentrality = randomCentrality_;
        _aicCutoff = aicCutoff_;
    }

    public double getAicCutoff()
    {
        return _aicCutoff;
    }
    
    public boolean isRandomCentrality()
    {
        return _randomCentrality;
    }
    
    public boolean isRandomScale()
    {
        return _randomScale;
    }

    public boolean isRandomShuffle()
    {
        return _randomShuffle;
    }
    
    public Random getRandom()
    {
        return _rand;
    }

}
