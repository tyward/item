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
import edu.columbia.tjw.item.ItemModel;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.QuantileDistribution;
import edu.columbia.tjw.item.util.LogUtil;
import java.util.Arrays;
import org.apache.commons.math3.analysis.MultivariateFunction;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.BaseMultivariateOptimizer;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultiStartMultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.random.RandomVectorGenerator;

/**
 *
 * @author tyler
 * @param <S> The status type for this generator
 * @param <R> The regressor type for this generator
 * @param <T> The curve type for this generator
 */
public class BaseParamGenerator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements ParamGenerator<S, R, T>
{
    private static final Logger LOG = LogUtil.getLogger(BaseParamGenerator.class);
    private final ItemSettings _settings;
    private final ItemCurveFactory<T> _factory;
    private final T _type;
    private final ItemModel<S, R, T> _baseModel;
    private final int _tranParamCount;
    private final int _paramCount;
    private final S _toStatus;
    private final R _intercept;

    public BaseParamGenerator(final ItemCurveFactory<T> factory_, final T curveType_, final ItemModel<S, R, T> baseModel_, final S toStatus_, final ItemSettings settings_, final R intercept_)
    {
        if (null == intercept_)
        {
            throw new NullPointerException("Intercept cannot be null.");
        }

        _intercept = intercept_;
        _factory = factory_;
        _type = curveType_;
        _baseModel = baseModel_;
        _tranParamCount = curveType_.getParamCount();
        _paramCount = _tranParamCount + 2;

        _toStatus = toStatus_;

        _settings = settings_;
    }

    public final double[] generateParamVector(final int entryIndex_)
    {
        final ItemParameters<S, R, T> params = _baseModel.getParams();
        //final int index = params.getIndex(field_, curve_);
        final int statusIndex = params.toStatusIndex(_toStatus);
        final double beta = params.getBeta(statusIndex, entryIndex_);

        //Start with no intercept adjustment, use existing intercept.
        final double interceptAdjustment = 0.0;

        final ItemCurve<T> curve = params.getEntryCurve(entryIndex_, 0);

        final ItemCurveParams<T> curveParams = new ItemCurveParams<>(interceptAdjustment, beta, _type, curve);

        final double[] output = curveParams.generatePoint();
        return output;
    }

    @Override
    public final ItemModel<S, R, T> generatedModel(double[] params_, final R field_)
    {
        final ItemCurveParams<T> curveParams = this.generateParams(params_);

        final ItemCurve<T> curve = generateTransformation(params_);
        final ItemParameters<S, R, T> orig = _baseModel.getParams();

        final S status = orig.getStatus();
        final List<S> reachable = status.getReachable();
        final int toIndex = reachable.indexOf(_toStatus);

        final ItemParameters<S, R, T> updated = orig.addBeta(field_, curve, toIndex);

        final int matchIndex = updated.getIndex(field_, curve);
        final int interceptIndex = updated.getIndex(_intercept, null);

        final double interceptAdjustment = curveParams.getIntercept();
        final double beta = curveParams.getBeta();

        final double[][] values = updated.getBetas();

        values[toIndex][interceptIndex] += interceptAdjustment;
        values[toIndex][matchIndex] = beta;

        final ItemParameters<S, R, T> adjusted = updated.updateBetas(values);
        final ItemModel<S, R, T> output = _baseModel.updateParameters(adjusted);
        return output;
    }

    @Override
    public final int paramCount()
    {
        return _paramCount;
    }

    public final double[] polishCurveParameters(final QuantileDistribution dist_, final double[] rawParams_)
    {
        final ItemCurveParams<T> params = _factory.generateStartingParameters(_type, dist_, _settings.getRandom());

        //Should use an optimizer to improve the starting parameters.
        final InnerFunction polishFunction = new InnerFunction(dist_, params);

        final RandomVectorGenerator gen = new VectorGenerator(rawParams_, _settings.getRandom());

        final int multiStarts = Math.max(1, _settings.getPolishMultiStartPoints());

        final MultivariateOptimizer baseOptimizer = new PowellOptimizer(1.0e-3, 1.0e-3);
        final BaseMultivariateOptimizer<PointValuePair> optim = new MultiStartMultivariateOptimizer(baseOptimizer, multiStarts, gen);

        final InitialGuess guess = new InitialGuess(rawParams_);
        final double start = polishFunction.value(rawParams_);

        try
        {
            final PointValuePair result = optim.optimize(new ObjectiveFunction(polishFunction), GoalType.MINIMIZE, guess, new MaxIter(multiStarts * 100), new MaxEval(multiStarts * 300));

            final double end = result.getValue();
            final double[] endPoint = result.getPointRef();

            LOG.info("Polish run completed (" + start + " -> " + end + ")[" + optim.getIterations() + "]: " + Arrays.toString(rawParams_) + " -> " + Arrays.toString(endPoint));

            if (end < start)
            {
                return endPoint;
            }
        }
        catch (final TooManyEvaluationsException e)
        {
            LOG.info("Polish failed, too many evaluations.");
        }

        return rawParams_.clone();
    }

    @Override
    public final double[] getStartingParams(final QuantileDistribution dist_)
    {
        final ItemCurveParams<T> params = _factory.generateStartingParameters(_type, dist_, _settings.getRandom());
        final double[] output = params.generatePoint();
        return output;
    }

    @Override
    public final int translateParamNumber(final int input_)
    {
        return input_;
    }

    @Override
    public final ItemCurve<T> generateTransformation(double[] params_)
    {
        return this.generateParams(params_).getCurve(0);
    }

    @Override
    public ItemCurveParams<T> generateParams(double[] params_)
    {
        final ItemCurveParams<T> params = new ItemCurveParams<>(_type, _factory, params_);
        return params;
    }

    private final class VectorGenerator implements RandomVectorGenerator
    {
        private final Random _rand;
        private final double[] _base;

        public VectorGenerator(final double[] base_, final Random rand_)
        {
            _base = base_.clone();
            _rand = rand_;
        }

        @Override
        public double[] nextVector()
        {
            final double[] output = _base.clone();

            for (int i = 0; i < output.length; i++)
            {
                output[i] = (_rand.nextDouble() - 0.5) * output[i];
            }

            return output;
        }

    }

    private final class InnerFunction implements MultivariateFunction
    {
        private final QuantileDistribution _dist;
        private final ItemCurveParams<T> _params;

        public InnerFunction(final QuantileDistribution dist_, final ItemCurveParams<T> params_)
        {
            _dist = dist_;
            _params = params_;
        }

        @Override
        public double value(double[] point)
        {
            final ItemCurveParams<T> params = new ItemCurveParams<>(_params.getType(0), _factory, point);
            final ItemCurve<T> curve = params.getCurve(0);

            final double totalCount = _dist.getTotalCount();

            if (totalCount < 1)
            {
                //Should never happen.
                return Double.NaN;
            }

            //System.out.println("mass, x, y, predicted, mse");
            double residSum = 0.0;

            for (int i = 0; i < _dist.size(); i++)
            {
                final double x = _dist.getMeanX(i);
                final double y = _dist.getMeanY(i);
                final double mass = _dist.getCount(i);
                final double devY = _dist.getDevY(i);

                // We think y ~ f(x), so let's do some calculations. 
                final double raw = curve.transform(x);

                final double beta = params.getBeta();
                final double intercept = params.getIntercept();

                final double predicted = intercept + (beta * raw);

                //We can't say anything with more confidence than this.
                final double minDev = 1.0 / mass;
                final double adjDev = Math.max(devY, minDev);

                //final double prob = FastMath.exp(y);
                //This will not be a proper log likelihood calc, but instead will use weighted Least Squares, which is close to the same thing in this case. 
                final double resid = (y - predicted);
                final double mse = mass * ((resid * resid)); // + (adjDev * adjDev));
                residSum += mse;
                //System.out.println(mass + ", " + x + ", " + y + ", " + predicted + ", " + mse);
            }

            final double normalized = residSum / totalCount;
            //System.out.println("Trying point[" + Arrays.toString(point) + "]: " + normalized);

            return normalized;
        }

    }

}
