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
package edu.columbia.tjw.item.util;

import edu.columbia.tjw.item.ItemStatus;
import java.util.List;

/**
 *
 * @author tyler
 * @param <S>
 */
public final class LogLikelihood<S extends ItemStatus<S>>
{
    //Nothing is less likely than 1 in a million, roughly. 
    private static final double LOG_CUTOFF = 14;
    private final double EXP_CUTOFF = Math.exp(-LOG_CUTOFF);

    //This logic assumes all indistinguishable states are adjascent.
    private final int[][] _mapping;

    public LogLikelihood(final EnumFamily<S> family_)
    {
        final int count = family_.size();
        _mapping = new int[count][];

        for (int i = 0; i < count; i++)
        {
            final S next = family_.getFromOrdinal(i);
            final List<S> reachable = next.getReachable();
            final int reachableCount = reachable.size();

            _mapping[i] = new int[reachableCount];

            for (int k = 0; k < reachableCount; k++)
            {
                final S target = reachable.get(k);
                final List<S> indistingishable = target.getIndistinguishable();
                final int maxIndistinguishable = indistingishable.get(indistingishable.size() - 1).ordinal();
                _mapping[i][k] = maxIndistinguishable;
            }
        }
    }

    private final int[] getMapping(final S stat_)
    {
        return _mapping[stat_.ordinal()];
    }

    public final double logLikelihood(final S fromStatus_, final double[] computed_, final int actual_)
    {
        //TODO: Clean this up and improve performance.
        final int[] combineMapping = getMapping(fromStatus_);

        double computedVal = computed_[actual_];

        if (combineMapping[actual_] != actual_)
        {
            computedVal += computed_[combineMapping[actual_]];
        }

        final double output = logLikelihoodTerm(1.0, computedVal);
        return output;
    }

    public final double logLikelihood(final S fromStatus_, final double[] actual_, final double[] computed_)
    {
        final int[] combineMapping = getMapping(fromStatus_);

        double actSum = 0.0;
        double computedSum = 0.0;

        double sum = 0.0;

        for (int i = 0; i < actual_.length; i++)
        {
            actSum += actual_[i];
            computedSum += computed_[i];

            if (combineMapping[i] != i)
            {
                continue;
            }

            sum += logLikelihoodTerm(actSum, computedSum);
            actSum = 0.0;
            computedSum = 0.0;
        }

        return sum;
    }

    private final double logLikelihoodTerm(final double actual_, final double computed_)
    {
        if (actual_ <= 0.0)
        {
            return 0.0;
        }

        final double negLL;

        if (computed_ > EXP_CUTOFF)
        {
            negLL = -Math.log(computed_);
        }
        else
        {
            negLL = LOG_CUTOFF;
        }

        final double product = actual_ * negLL;
        return product;
    }

}
