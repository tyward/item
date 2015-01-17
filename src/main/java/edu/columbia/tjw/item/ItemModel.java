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

import edu.columbia.tjw.item.util.LogLikelihood;
import edu.columbia.tjw.item.util.MultiLogistic;
import java.util.Arrays;
import java.util.List;

/**
 *
 * A class representing an ITEM model.
 *
 * @author tyler
 * @param <S> The status type for this model
 * @param <R> The regressor type for this model
 * @param <T> The curve type for this model
 */
public final class ItemModel<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final double ROUNDING_TOLERANCE = 1.0e-8;
    private final LogLikelihood<S> _likelihood;
    private final ItemParameters<S, R, T> _params;
    private final List<S> _reachable;

    private final double[][] _betas;
    private final int _reachableSize;
    private final int[] _reachableMap;

    /**
     * Create a new item model from its parameters.
     *
     * @param params_
     */
    public ItemModel(final ItemParameters<S, R, T> params_)
    {
        _params = params_;
        final S status = params_.getStatus();

        _reachable = status.getReachable();
        _likelihood = new LogLikelihood<>(params_.getStatus().getFamily());

        //_status = params_.getStatus();
        _betas = params_.getBetas();
        _reachableSize = _params.getStatus().getReachableCount();

        _reachableMap = new int[status.getFamily().size()];
        Arrays.fill(_reachableMap, -1);

        for (int i = 0; i < _reachableSize; i++)
        {
            _reachableMap[_reachable.get(i).ordinal()] = i;
        }
    }

    public S getStatus()
    {
        return _params.getStatus();
    }

    public final int getRegressorCount()
    {
        return _params.regressorCount();
    }

    public final ItemParameters<S, R, T> getParams()
    {
        return _params;
    }

    public final double logLikelihood(final ItemFittingGrid<S, R> grid_, final ItemWorkspace<S> workspace_, final int index_)
    {
        if (!grid_.hasNextStatus(index_))
        {
            return 0.0;
        }

        final double[] computed = workspace_.getComputedProbabilityWorkspace();
        final double[] actual = workspace_.getActualProbabilityWorkspace();
        transitionProbability(grid_, workspace_, index_, computed);

        //Arrays.fill(actual, 0.0);
        final int actualOutcome = grid_.getNextStatus(index_);
        final int mapped = _reachableMap[actualOutcome];
        //actual[mapped] = 1.0;

        //If this item took a forbidden transition, ignore the data point.
        if (mapped < 0)
        {
            return 0.0;
        }

        final double logLikelihood = _likelihood.logLikelihood(_params.getStatus(), computed, mapped);
        return logLikelihood;
    }

    /**
     * This will take packed probabilities (only the reachable states for this
     * status), and fill out a vector of unpacked probabilities (all statuses
     * are represented).
     *
     * @param packed_ The probabilities for reachable statuses. (input)
     * @param unpacked_ A vector to hold the probabilities for all statuses.
     * Unreachable statuses will get 0.0.
     */
    public void unpackProbabilities(final double[] packed_, final double[] unpacked_)
    {
        final S status = getStatus();
        //final List<S> reachable = status.getReachable();
        final int reachableCount = status.getReachableCount();
        final int statCount = status.getFamily().size();

        if (packed_.length != reachableCount)
        {
            throw new IllegalArgumentException("Input is the wrong size: " + packed_.length);
        }
        if (unpacked_.length != statCount)
        {
            throw new IllegalArgumentException("Output is the wrong size: " + packed_.length);
        }

        Arrays.fill(unpacked_, 0.0);
        double sum = 0.0;

        for (int i = 0; i < statCount; i++)
        {
            final int reachableOrdinal = _reachableMap[i];

            if (reachableOrdinal < 0)
            {
                unpacked_[i] = 0.0;
                continue;
            }

            final double prob = packed_[reachableOrdinal];
            sum += prob;
            unpacked_[i] = prob;
        }

        final double diff = Math.abs(sum - 1.0);

        if (!(diff < ROUNDING_TOLERANCE))
        {
            throw new IllegalArgumentException("Rounding tolerance exceeded by probability vector: " + diff);
        }
    }

    public ItemWorkspace<S> generateWorkspace()
    {
        return new ItemWorkspace<>(_params.getStatus(), this.getRegressorCount());
    }

    public int transitionProbability(final ItemModelGrid<S, R> grid_, final ItemWorkspace<S> workspace_, final int index_, final double[] output_)
    {
        final double[] regressors = workspace_.getRegressorWorkspace();

        grid_.getRegressors(index_, regressors);

        multiLogisticFunction(regressors, _betas, output_);

        return _betas.length;
    }

    public void powerScores(final double[] regressors_, final double[][] betas_, final double[] workspace_)
    {
        final int inputSize = regressors_.length;
        final int outputSize = betas_.length;

        for (int i = 0; i < outputSize; i++)
        {
            double sum = 0.0;
            final double[] betaArray = betas_[i];

            for (int k = 0; k < inputSize; k++)
            {
                sum += regressors_[k] * betaArray[k];
            }

            workspace_[i] = sum;
        }
    }

    private void multiLogisticFunction(final double[] regressors_, final double[][] betas_, final double[] workspace_)
    {
        powerScores(regressors_, betas_, workspace_);
        MultiLogistic.multiLogisticFunction(workspace_, workspace_);
    }

    public ItemModel<S, R, T> updateParameters(ItemParameters<S, R, T> params_)
    {
        return new ItemModel<>(params_);
    }

    public double betaDerivative(final double[] regressors_, final double[] computedProbabilities_, final int regressorIndex_, final int toStateIndex_, final int toStateBetaIndex_)
    {
        final double output = MultiLogistic.multiLogisticBetaDerivative(regressors_, computedProbabilities_, regressorIndex_, toStateIndex_, toStateBetaIndex_);
        return output;
    }

    public void regressorDerivatives(final double[] powerScores_, final int regressorIndex_, final double[] workspace_, final double[] workspace2_, final double[] output_)
    {
        for (int i = 0; i < _reachableSize; i++)
        {
            workspace2_[i] = _betas[i][regressorIndex_];
        }

        MultiLogistic.multiLogisticRegressorDerivatives(powerScores_, workspace2_, workspace_, output_);
    }

}
