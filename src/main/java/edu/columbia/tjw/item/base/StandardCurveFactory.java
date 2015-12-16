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
package edu.columbia.tjw.item.base;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveFactory;
import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.algo.QuantileDistribution;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.MathFunctions;
import java.util.Random;
import org.apache.commons.math3.util.FastMath;

/**
 * This is the default implementation of the curve factory.
 *
 * This provides curves for the standard curve types described in the paper
 *
 * Unless there is good reason, use this as the curve factory.
 *
 *
 * @author tyler
 */
public final class StandardCurveFactory implements ItemCurveFactory<StandardCurveType>
{
    /**
     * The singleton for this class. It has no free parameters, so no need for
     * more than one.
     */
    public static final StandardCurveFactory SINGLETON = new StandardCurveFactory();

    private StandardCurveFactory()
    {
    }

    @Override
    public ItemCurve<StandardCurveType> generateCurve(final ItemCurveParams<StandardCurveType> params_)
    {
        final double centralityVal = params_.getCurveParams(0);
        final double slopeParam = params_.getCurveParams(1);
        final StandardCurveType curveType = params_.getType();

        switch (curveType)
        {
            case LOGISTIC:
                return new LogisticCurve(centralityVal, slopeParam);
            case GAUSSIAN:
                return new GaussianCurve(centralityVal, slopeParam);
            default:
                throw new RuntimeException("Impossible, unknown type: " + curveType);
        }
    }

    @Override
    public ItemCurveParams<StandardCurveType> generateStartingParameters(final StandardCurveType type_, final QuantileDistribution dist_, final Random rand_)
    {
        final double[] curveParams = new double[2]; //== type_.getParamCount();

        //First, choose the mean uniformly....
        final int size = dist_.size();
        final double meanSelector = rand_.nextDouble();
        final double invCount = 1.0 / dist_.getTotalCount();
        double runningSum = 0.0;
        double xVal = 0.0;
        int xIndex = 0;

        for (int i = 0; i < size; i++)
        {
            final long bucketCount = dist_.getCount(i);
            final double frac = bucketCount * invCount;
            xVal = dist_.getMeanX(i);
            xIndex = i;

            runningSum += frac;

            if (runningSum > meanSelector)
            {
                break;
            }
        }

        curveParams[0] = xVal;

        final double distDev = dist_.getDevX();
        final double slopeParam;
        final double betaGuess;

        switch (type_)
        {
            case LOGISTIC:
                slopeParam = Math.sqrt(1.0 / (distDev + 1.0e-10));

                double xCorrelation = 0.0;
                double xVar = 0.0;
                final double meanY = dist_.getMeanY();
                final double meanX = dist_.getMeanX();

                for (int i = 0; i < size; i++)
                {
                    final double yDev = dist_.getMeanY(i) - meanY;
                    final double xDev = dist_.getMeanX(i) - meanX;
                    final double corr = yDev * xDev * dist_.getCount(i);
                    xCorrelation += corr;
                }

                final double xDev = dist_.getDevX();
                final double yDev = dist_.getDevY();
                final double obsCount = dist_.getTotalCount();

                //Between -1.0 and 1.0, a reasonable guess for beta...
                betaGuess = xCorrelation / (obsCount * xDev * yDev);

                break;
            case GAUSSIAN:
                slopeParam = distDev;
                betaGuess = dist_.getMeanY(xIndex) - dist_.getMeanY();
                break;
            default:
                throw new RuntimeException("Impossible.");
        }

        curveParams[1] = slopeParam;

        final double intercept = -0.5 * betaGuess;

        final ItemCurveParams<StandardCurveType> output = new ItemCurveParams<>(type_, intercept, betaGuess, curveParams);
        return output;

    }

    @Override
    public EnumFamily<StandardCurveType> getFamily()
    {
        return StandardCurveType.FAMILY;
    }

    private static final class GaussianCurve extends StandardCurve<StandardCurveType>
    {
        private final double _stdDev;
        private final double _invStdDev;
        private final double _mean;
        private final double _expNormalizer;

        public GaussianCurve(final double mean_, final double stdDev_)
        {
            super(StandardCurveType.GAUSSIAN);

            if (Double.isInfinite(mean_) || Double.isNaN(mean_))
            {
                throw new IllegalArgumentException("Invalid mean: " + mean_);
            }
            if (Double.isInfinite(stdDev_) || Double.isNaN(stdDev_))
            {
                throw new IllegalArgumentException("Invalid stdDev: " + stdDev_);
            }

            final double variance = (stdDev_ * stdDev_) + 1.0e-10;
            _stdDev = Math.sqrt(variance);
            _invStdDev = 1.0 / _stdDev;
            _mean = mean_;
            _expNormalizer = -1.0 / (2.0 * variance);
        }

        @Override
        public double transform(double input_)
        {
            final double centered = (input_ - _mean);
            final double expArg = _expNormalizer * centered * centered;
            final double expValue = FastMath.exp(expArg);
            return expValue;
        }

        @Override
        public double derivative(int index_, double input_)
        {
            final double centered = (input_ - _mean);
            final double base = 2.0 * centered * _expNormalizer;
            final double expContribution = transform(input_);

            final double paramContribution;

            switch (index_)
            {
                case 0:
                    paramContribution = base;
                    break;
                case 1:
                    paramContribution = base * centered * _invStdDev;
                    break;
                default:
                    throw new IllegalArgumentException("Bad index: " + index_);
            }

            final double output = paramContribution * expContribution;
            return output;
        }

        @Override
        public double getParam(int index_)
        {
            switch (index_)
            {
                case 0:
                    return _mean;
                case 1:
                    return _stdDev; //We do this to return the original slope value. 
                default:
                    throw new IllegalArgumentException("Bad index: " + index_);
            }
        }
    }

    private static final class LogisticCurve extends StandardCurve<StandardCurveType>
    {
        private final double _center;
        private final double _slope;
        private final double _slopeParam;
        private final double _origSlope;

        public LogisticCurve(final double center_, final double slope_)
        {
            super(StandardCurveType.LOGISTIC);

            if (Double.isInfinite(center_) || Double.isNaN(center_))
            {
                throw new IllegalArgumentException("Invalid center: " + center_);
            }
            if (Double.isInfinite(slope_) || Double.isNaN(slope_))
            {
                throw new IllegalArgumentException("Invalid slope: " + slope_);
            }

            //Slope must be squared so that we can ensure that it is positive. 
            //We cannot take an abs, because that is not an analytical transformation.
            _center = center_;
            _slope = (slope_ * slope_);
            _slopeParam = Math.sqrt(_slope);
            _origSlope = slope_;
        }

        @Override
        public double transform(double input_)
        {
            final double normalized = _slope * (input_ - _center);
            final double output = MathFunctions.logisticFunction(normalized);
            return output;
        }

        @Override
        public double derivative(int index_, double input_)
        {
            final double forward = _slope * (input_ - _center);
            final double backward = -_slope * (input_ - _center);

            final double fwdLogistic = MathFunctions.logisticFunction(forward);
            final double backLogistic = MathFunctions.logisticFunction(backward);
            final double combined = fwdLogistic * backLogistic;

            final double paramContribution;

            switch (index_)
            {
                case 0:
                    paramContribution = -_slope;
                    break;
                case 1:
                    paramContribution = 2.0 * _origSlope * (input_ - _center);
                    break;
                default:
                    throw new IllegalArgumentException("Bad index: " + index_);
            }

            final double derivative = combined * paramContribution;
            return derivative;
        }

        @Override
        public double getParam(int index_)
        {
            switch (index_)
            {
                case 0:
                    return _center;
                case 1:
                    return _slopeParam; //We do this to return the original slope value. 
                default:
                    throw new IllegalArgumentException("Bad index: " + index_);
            }
        }

    }

}
