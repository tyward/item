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
package edu.columbia.tjw.item.fit.param;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemFittingGrid;
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.ItemWorkspace;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.MultivariateGradient;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.ThreadedMultivariateFunction;
import java.util.List;

/**
 *
 * @author tyler
 * @param <S>
 * @param <R>
 * @param <T>
 */
public class LogisticModelFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final double[] _beta;
    private final int[] _statusPointers;
    private final int[] _regPointers;
    private final ItemFittingGrid<S, R> _grid;
    private ItemParameters<S, R, T> _params;
    private ItemModel<S, R, T> _model;
    //private MultivariateFiniteDiffDerivFunction _derivFunction;

    public LogisticModelFunction(final double[] beta_, final int[] statusPointers_, final int[] regPointers_,
            final ItemParameters<S, R, T> params_, final ItemFittingGrid<S, R> grid_, final ItemModel<S, R, T> model_)
    {
        _beta = beta_.clone();
        _statusPointers = statusPointers_;
        _regPointers = regPointers_;
        _params = params_;
        _grid = grid_;
        _model = model_;
    }

    @Override
    public int dimension()
    {
        return _beta.length;
    }

    @Override
    public int numRows()
    {
        return _grid.totalSize();
    }

    @Override
    protected void prepare(MultivariatePoint input_)
    {
        final int dimension = this.dimension();
        boolean changed = false;

        for (int i = 0; i < dimension; i++)
        {
            final double value = input_.getElement(i);

            if (value != _beta[i])
            {
                _beta[i] = value;
                changed = true;
            }
        }

        if (!changed)
        {
            return;
        }

        final ItemParameters updated = updateParams(_params, _statusPointers, _regPointers, _beta);
        _params = updated;
        _model = _model.updateParameters(_params);
    }

    @Override
    protected void evaluate(int start_, int end_, EvaluationResult result_)
    {
        if (start_ == end_)
        {
            return;
        }

        //Slightly wasteful, but we will reuse this for enough calcs it shouldn't matter...
        final ItemWorkspace<S> workspace = _model.generateWorkspace();
        final S fromStatus = this._model.getParams().getStatus();
        int count = 0;

        final int fromStatusOrdinal = fromStatus.ordinal();

        for (int i = start_; i < end_; i++)
        {
            final int statOrdinal = _grid.getStatus(i);

            if (statOrdinal != fromStatusOrdinal)
            {
                continue;
            }
            if (!_grid.hasNextStatus(i))
            {
                continue;
            }

            final double ll = _model.logLikelihood(_grid, workspace, i);
            count++;

            result_.add(ll, result_.getHighWater(), i + 1);
        }

        result_.setHighRow(end_);
    }

    private ItemParameters<S, R, T> updateParams(final ItemParameters<S, R, T> params_, final int[] rowPointers_, final int[] colPointers_, final double[] betas_)
    {
        final double[][] beta = params_.getBetas();

        for (int i = 0; i < betas_.length; i++)
        {
            final int row = rowPointers_[i];
            final int column = colPointers_[i];
            final double value = betas_[i];
            beta[row][column] = value;
        }

        final ItemParameters<S, R, T> updated = params_.updateBetas(beta);
        return updated;
    }

    @Override
    public MultivariateGradient calculateDerivative(MultivariatePoint input_, EvaluationResult result_, double precision_)
    {
        //final MultivariateGradient grad = _derivFunction.calculateDerivative(input_, result_, precision_);

        this.prepare(input_);

        final int start = 0;
        final int end = _grid.totalSize();
        final int dimension = input_.getDimension();

        final double[] derivative = new double[dimension];
        final ItemWorkspace<S> workspace = _model.generateWorkspace();

        final double[] computed = workspace.getComputedProbabilityWorkspace();
        final double[] actual = workspace.getActualProbabilityWorkspace();
        final double[] regressors = workspace.getRegressorWorkspace();
        final List<S> reachable = _model.getParams().getStatus().getReachable();
        int count = 0;

        final int fromOrdinal = _params.getStatus().ordinal();

        for (int i = start; i < end; i++)
        {
            if (_grid.getStatus(i) != fromOrdinal)
            {
                continue;
            }
            if (!_grid.hasNextStatus(i))
            {
                continue;
            }

            _model.transitionProbability(_grid, workspace, i, computed);
            _grid.getRegressors(i, regressors);

            final int actualTransition = _grid.getNextStatus(i);

            for (int w = 0; w < reachable.size(); w++)
            {
                final S next = reachable.get(w);

                if (next.ordinal() == actualTransition)
                {
                    actual[w] = 1.0;
                }
                else
                {
                    actual[w] = 0.0;
                }
            }

            for (int z = 0; z < dimension; z++)
            {
                //looping over the betas.
                double betaTerm = 0.0;

                for (int q = 0; q < reachable.size(); q++)
                {
                    //looping over the to-states.
                    final double betaDerivative = _model.betaDerivative(regressors, computed, _regPointers[z], q, _statusPointers[z]);
                    final double actualProbability = actual[q];
                    final double computedProb = computed[q];
                    final double contribution = actualProbability * betaDerivative / computedProb;
                    betaTerm += contribution;
                }

                derivative[z] += betaTerm;
            }

            count++;
        }

        //N.B: we are computing the negative log likelihood. 
        final double invCount = -1.0 / count;

        for (int i = 0; i < derivative.length; i++)
        {
            derivative[i] = derivative[i] * invCount;
        }

        final MultivariatePoint der = new MultivariatePoint(derivative);

        final MultivariateGradient grad = new MultivariateGradient(input_, der, null, 0.0);

        return grad;
    }

    @Override
    public int resultSize(int start_, int end_)
    {
        return (end_ - start_);
    }

}
