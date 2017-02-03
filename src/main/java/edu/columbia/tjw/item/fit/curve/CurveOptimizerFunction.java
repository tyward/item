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
package edu.columbia.tjw.item.fit.curve;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.fit.ParamFittingGrid;
import edu.columbia.tjw.item.util.RectangularDoubleArray;
import edu.columbia.tjw.item.util.LogLikelihood;
import edu.columbia.tjw.item.util.MultiLogistic;
import edu.columbia.tjw.item.optimize.EvaluationResult;
import edu.columbia.tjw.item.optimize.MultivariateDifferentiableFunction;
import edu.columbia.tjw.item.optimize.MultivariateGradient;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.ThreadedMultivariateFunction;
import java.util.List;

/**
 *
 * @author tyler
 * @param <S> The status type for this optimizer function
 * @param <R> The regressor type for this optimizer function
 * @param <T> THe curve type for this optimizer function
 */
public class CurveOptimizerFunction<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        extends ThreadedMultivariateFunction implements MultivariateDifferentiableFunction
{
    private final LogLikelihood<S> _likelihood;
    //private final ParamGenerator<S, R, T> _generator;
    final ItemCurveFactory<R, T> _factory;
    private final double[] _regressor;
    private final int _size;
    private final double[] _workspace;
    private final int _toIndex;

    private final BaseCurveFitter<S, R, T> _curveFitter;

    private final S _status;
    private final int[] _actualOffsets;
    private final R _field;

    private ItemCurve<?> _trans;
    private double _interceptAdjustment;
    private double _beta;
    private MultivariatePoint _prevPoint;

    private final ItemSettings _settings;

    private final ItemCurveParams<R, T> _initParams;
    private final boolean _subtractStarting;

    private ItemCurveParams<R, T> _params;

    public CurveOptimizerFunction(final ItemCurveParams<R, T> initParams_, final ItemCurveFactory<R, T> factory_, final S fromStatus_, final S toStatus_, final BaseCurveFitter<S, R, T> curveFitter_,
            final int[] actualOrdinals_, final ParamFittingGrid<S, R, T> grid_, final int[] indexList_, final ItemSettings settings_, final boolean subtractStarting_)
    {
        super(settings_.getThreadBlockSize(), settings_.getUseThreading());

        _field = initParams_.getRegressor(0);

        _factory = factory_;
        _initParams = initParams_;
        _subtractStarting = subtractStarting_;

        _curveFitter = curveFitter_;
        _settings = settings_;
        _likelihood = new LogLikelihood<>(fromStatus_);
        _actualOffsets = new int[actualOrdinals_.length];

        //Convert ordinals to offsets. 
        for (int i = 0; i < _actualOffsets.length; i++)
        {
            _actualOffsets[i] = _likelihood.ordinalToOffset(actualOrdinals_[i]);
        }

        _status = fromStatus_;
        _size = _actualOffsets.length;
        _workspace = new double[_initParams.size()];
        _regressor = new double[_size];
        _toIndex = fromStatus_.getReachable().indexOf(toStatus_);

        final ItemRegressorReader reader = grid_.getRegressorReader(_field);

        for (int i = 0; i < _size; i++)
        {
            final int mapped = indexList_[i];
            final double regressor = reader.asDouble(mapped);

            _regressor[i] = regressor;
        }
    }

    @Override
    public int dimension()
    {
        return _initParams.size();
    }

    @Override
    public int numRows()
    {
        return _size;
    }

    @Override
    protected void prepare(MultivariatePoint input_)
    {
        if (null != _prevPoint)
        {
            if (_prevPoint.equals(input_))
            {
                return;
            }
        }

        _prevPoint = input_.clone();

        for (int i = 0; i < input_.getDimension(); i++)
        {
            _workspace[i] = input_.getElement(i);
        }

        _params = new ItemCurveParams<>(_initParams, _factory, _workspace);

        //_params = _generator.generateParams(_workspace, _field);
        _trans = _params.getCurve(0);
        _interceptAdjustment = _params.getIntercept();
        _beta = _params.getBeta();
    }

    @Override
    protected void evaluate(int start_, int end_, EvaluationResult result_)
    {
        if (start_ == end_)
        {
            return;
        }

        final RectangularDoubleArray powerScores = _curveFitter.getPowerScores();
        final int cols = powerScores.getColumns();

        final double[] computed = new double[cols];
        //final double[] actual = new double[cols];

        for (int i = start_; i < end_; i++)
        {
            for (int k = 0; k < cols; k++)
            {
                computed[k] = powerScores.get(i, k);
                //actual[k] = _actualProbabilities.get(i, k);
            }

            final int actualOffset = _actualOffsets[i];
            final double regressor = _regressor[i];

            final double transformed = _trans.transform(regressor);
            final double contribution = (_beta * transformed);

            final double prevContribution;

            if (_subtractStarting)
            {
                final double prevBeta = _initParams.getBeta();
                final double prevTransformed = _initParams.getCurve(0).transform(regressor);

                prevContribution = prevBeta * prevTransformed;

                //prevContribution = _prevBeta * _prevCurve.transform(regressor);
            }
            else
            {
                prevContribution = 0.0;
            }

            //We are replacing one curve with another (if _prevCurve != null), so subtract off the 
            // curve we previously had before adding this new one.
            final double totalContribution = _interceptAdjustment + contribution - prevContribution;

            computed[_toIndex] += totalContribution;

            //Converte these power scores into probabilities.
            MultiLogistic.multiLogisticFunction(computed, computed);

            final double logLikelihood = _likelihood.logLikelihood(computed, actualOffset);

            result_.add(logLikelihood, result_.getHighWater(), i + 1);

        }
    }

    @Override
    protected MultivariateGradient evaluateDerivative(int start_, int end_, MultivariatePoint input_, EvaluationResult result_)
    {
        final int dimension = input_.getDimension();
        final double[] derivative = new double[dimension];

        if (start_ >= end_)
        {
            final MultivariatePoint der = new MultivariatePoint(derivative);
            return new MultivariateGradient(input_, der, null, 0.0);
        }

        final List<S> reachable = _status.getReachable();
        int count = 0;
        final int reachableCount = reachable.size();

        final double[] scores = new double[reachableCount];

        //We are only interested in the specific element being curved....
        //Therefore, set the beta to 1.0, the result is a multiple of beta
        //for the special case where only one beta is set. We will scale afterwards. 
        final double[] betas = new double[reachableCount];
        betas[this._toIndex] = 1.0;

        final double[] workspace1 = new double[reachableCount];
        final double[] output = new double[reachableCount];
        //final double[] actual = new double[reachableCount];

        final int interceptIndex = _params.getInterceptIndex();
        final int betaIndex = _params.getBetaIndex();

        final RectangularDoubleArray powerScores = _curveFitter.getPowerScores();

        for (int i = start_; i < end_; i++)
        {
            for (int w = 0; w < reachableCount; w++)
            {
                scores[w] = powerScores.get(i, w);
                //actual[w] = _actualProbabilities.get(i, w);
            }

            //After this, workspace1 holds the model probabilities, output holds the xDerivatives of the probabilities.
            MultiLogistic.multiLogisticRegressorDerivatives(scores, betas, workspace1, output);
            MultiLogistic.multiLogisticFunction(scores, workspace1);

            double xDerivative = 0.0;

            //for (int w = 0; w < reachableCount; w++)
            //{
            //    final double probRatio = actual[w] / workspace1[w];
            //    final double derivTerm = output[w] * probRatio;
            final int actualOffset = _actualOffsets[i];

            if (actualOffset >= 0)
            {
                //N.B: Ignore any transitions that we know to be impossible.
                final double derivTerm = output[actualOffset] / workspace1[actualOffset];
                xDerivative += derivTerm;
            }
            //}

            final double regressor = this._regressor[i];

            final double transformed = this._trans.transform(regressor);

            //In our special case, the derivative is directly proportional to beta, because we apply it to only one state. 
            final double interceptDerivative = xDerivative;

            final double betaDerivative = xDerivative * transformed;

            derivative[interceptIndex] += interceptDerivative;
            derivative[betaIndex] += betaDerivative;

            for (int w = 0; w < _trans.getCurveType().getParamCount(); w++)
            {
                final double paramDerivative = _trans.derivative(w, regressor);
                final double combined = xDerivative * this._beta * paramDerivative;
                derivative[w] += combined;
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
        final int size = (end_ - start_);
        return size;
    }

}
