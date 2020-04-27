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
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.algo.QuantileStatistics;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.MathFunctions;
import org.apache.commons.math3.util.FastMath;

import java.util.Random;

/**
 * This is the default implementation of the curve factory.
 * <p>
 * This provides curves for the standard curve types described in the paper
 * <p>
 * Unless there is good reason, use this as the curve factory.
 *
 * @param <R>
 * @author tyler
 */
public final class StandardCurveFactory<R extends ItemRegressor<R>> implements ItemCurveFactory<R, StandardCurveType>
{
    private static final double SLOPE_MULT = 10.0;
    private static final long serialVersionUID = 0xfa6df5b97c865a49L;

    public StandardCurveFactory()
    {
    }

    @Override
    public ItemCurve<StandardCurveType> generateCurve(StandardCurveType type_, int offset_, double[] params_)
    {
        final double centralityVal = params_[offset_];
        final double slopeParam = params_[offset_ + 1];

        switch (type_)
        {
            case LOGISTIC:
                return new LogisticCurve(centralityVal, slopeParam);
            case GAUSSIAN:
                return new GaussianCurve(centralityVal, slopeParam);
            default:
                throw new RuntimeException("Impossible, unknown type: " + type_);
        }
    }

    @Override
    public ItemCurveParams<R, StandardCurveType> generateStartingParameters(final StandardCurveType type_,
                                                                            final R field_,
                                                                            final QuantileStatistics dist_,
                                                                            final Random rand_)
    {
        final double[] curveParams = new double[2]; //== type_.getParamCount();

        //First, choose the mean uniformly....
        final int size = dist_.getSize();
        final double meanSelector = rand_.nextDouble();
        final double invCount = 1.0 / dist_.getQuantApprox().getTotalCount();
        double runningSum = 0.0;
        double xVal = 0.0;
        int xIndex = 0;

        for (int i = 0; i < size; i++)
        {
            final long bucketCount = dist_.getCount(i);
            final double frac = bucketCount * invCount;
            xVal = dist_.getQuantApprox().getBucketMean(i);
            xIndex = i;

            runningSum += frac;

            if (runningSum > meanSelector)
            {
                break;
            }
        }

        curveParams[0] = xVal;

        final double distDev = dist_.getQuantApprox().getMeanStdDev();

        //This is a reasonable estimate of how low the std. dev can realistically be. 
        //Given that we have seen only finitely many observations, we could never say with confidence that it's zero.
        //Also, in case the mean happens to be zero, and the dev is zero, then we add a tiny value to not divide by
        // zero.
        final double minDev =
                1.0e-10 + Math.abs(dist_.getQuantApprox().getMean() / dist_.getQuantApprox().getTotalCount());

        final double slopeParam;
        final double betaGuess;

        switch (type_)
        {
            case LOGISTIC:
                // The logic is that we'll want one unit of slope to occupy something 
                // like the midpoint between 1 bucket and all the buckets, so about sqrt(buckets)
                // However, randomize this a bit to give us more chances to get it right. 
                final double slopeScale = (0.5 + rand_.nextDouble()) * Math.sqrt(size);

                //The square root is because the slope is squared before being applied, to keep the logistic upward
                // sloping.
                slopeParam = Math.sqrt(slopeScale / Math.max(minDev, distDev));
                //slopeParam = Math.sqrt(1.0 / (distDev + 1.0e-10));

                //Compute the rough scale of beta needed to account for the given data.
                // Take the average of the buckets above the mean, and the average below.
                double aboveSum = 0.0;
                double belowSum = 0.0;
                double aboveMass = 0.0;
                double belowMass = 0.0;

                final double meanY = dist_.getMeanY();

                for (int i = 0; i < size; i++)
                {
                    final double yDev = dist_.getMeanY(i) - meanY;

                    if (Double.isNaN(yDev) || Double.isInfinite(yDev))
                    {
                        throw new IllegalStateException("Overflow error.");
                    }

                    if (i <= xIndex)
                    {
                        belowSum += yDev;
                        belowMass += 1;
                    }
                    else
                    {
                        aboveSum += yDev;
                        aboveMass += 1;
                    }
                }

                final double slope = (aboveSum - belowSum) / (aboveMass + belowMass);
                betaGuess = slope;

                if (Double.isNaN(betaGuess) || Double.isInfinite(betaGuess))
                {
                    throw new IllegalStateException("Overflow error.");
                }

                break;
            case GAUSSIAN:
                slopeParam = (0.5 + rand_.nextDouble()) * Math.max(minDev, distDev);
                betaGuess = dist_.getMeanY(xIndex) - dist_.getMeanY();
                break;
            default:
                throw new RuntimeException("Impossible.");
        }

        curveParams[1] = slopeParam;

        final double intercept = -0.5 * betaGuess;

        final ItemCurveParams<R, StandardCurveType> output = new ItemCurveParams<>(type_, field_, this, intercept,
                betaGuess, curveParams);
        return output;

    }

    @Override
    public EnumFamily<StandardCurveType> getFamily()
    {
        return StandardCurveType.FAMILY;
    }

    @Override
    public ItemCurve<StandardCurveType> boundCentrality(ItemCurve<StandardCurveType> inputCurve_, double lowerBound_,
                                                        double upperBound_)
    {
        if (null == inputCurve_)
        {
            return null;
        }

        final StandardCurveType type = inputCurve_.getCurveType();

        switch (type)
        {
            case LOGISTIC:
            {
                final LogisticCurve curve = (LogisticCurve) inputCurve_;

                final double center = curve._center;

                if (center < lowerBound_)
                {
                    return new LogisticCurve(lowerBound_, Math.sqrt(curve._slope));
                }
                if (center > upperBound_)
                {
                    return new LogisticCurve(upperBound_, Math.sqrt(curve._slope));
                }

                return curve;
            }
            case GAUSSIAN:
            {
                final GaussianCurve curve = (GaussianCurve) inputCurve_;
                final double center = curve._mean;

                if (center < lowerBound_)
                {
                    return new GaussianCurve(lowerBound_, Math.sqrt(curve._stdDev));
                }
                if (center > upperBound_)
                {
                    return new GaussianCurve(upperBound_, Math.sqrt(curve._stdDev));
                }

                return curve;
            }
            default:
                throw new RuntimeException("Impossible.");
        }

    }

    private static final class GaussianCurve extends StandardCurve<StandardCurveType>
    {
        private static final double SQRT_EPSILON = Math.sqrt(Math.ulp(4.0));
        private static final long serialVersionUID = 0xd1c81f26497f177fL;
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

            // The abs of mean, unless mean is really small, in which case this is a small positive value.
            final double absMean = Math.max(Math.abs(mean_), SQRT_EPSILON);

            // This is about the smallest that he sigma can reasonably be before we don't have much accuracy anymore.
            final double sigmaAdj = SQRT_EPSILON * absMean;

            _stdDev = Math.max(Math.abs(stdDev_), sigmaAdj);

            final double variance = (_stdDev * _stdDev);
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
            final double zVal = (input_ - _mean) * _invStdDev;
            final double base = zVal * _invStdDev;
            final double expContribution = transform(input_);

            final double paramContribution;

            switch (index_)
            {
                case 0:
                    paramContribution = base;
                    break;
                case 1:
                    paramContribution = base * zVal;
                    break;
                default:
                    throw new IllegalArgumentException("Bad index: " + index_);
            }

            final double output = paramContribution * expContribution;
            return output;
        }

        @Override
        public double secondDerivative(int w_, int z_, double x_)
        {
            if (z_ < w_)
            {
                return secondDerivative(z_, w_, x_);
            }
            if (z_ < 0)
            {
                throw new IllegalArgumentException("Bad index: " + z_);
            }
            if (w_ > 1)
            {
                throw new IllegalArgumentException("Bad index: " + w_);
            }

            final double zTerm = (x_ - _mean) * _invStdDev; // (x - a)/ b
            final double z2 = zTerm * zTerm;
            final double s2 = _invStdDev * _invStdDev;
            final double multiple;

            if (z_ == 0)
            {
                // w_ is also 0, second derivative w.r.t. index 0
                multiple = s2 * ((zTerm * zTerm) - 1);
            }
            else if (w_ == 1)
            {
                // z_ also is 1, second derivative w.r.t. index 1
                multiple = s2 * ((z2 * z2) - (3 * z2));
            }
            else
            {
                // mixed second derivative.
                multiple = (zTerm * s2) * (z2 - 2);
            }

            final double output = multiple * transform(x_);
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
        private static final long serialVersionUID = 0x1dd40a5f2f923106L;
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
            _slopeParam = Math.abs(slope_);
            _slope = (_slopeParam * _slopeParam);
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
            final double val = this.transform(input_);
            final double backVal = (1.0 - val);
            final double combined = val * backVal;

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
        public double secondDerivative(int w_, int z_, double x_)
        {
            if (z_ < w_)
            {
                return secondDerivative(z_, w_, x_);
            }
            if (z_ < 0)
            {
                throw new IllegalArgumentException("Bad index: " + z_);
            }
            if (w_ > 1)
            {
                throw new IllegalArgumentException("Bad index: " + w_);
            }
            if (_slopeParam == 0.0)
            {
                // This would be a common divide by zero situation, actual answer is zero.
                return 0.0;
            }

            final double val = this.transform(x_);

            if (val == 0.0 || val == 1.0)
            {
                // Another divide by zero situation.
                return 0.0;
            }

            final double derivative;

            if (z_ == 0)
            {
                // w_ is also 0, second derivative w.r.t. index 0
                final double db = this.derivative(0, x_);
                final double db2 = db * db;
                derivative = (db2 / val) - (db2 / (1.0 - val));
            }
            else if (w_ == 1)
            {
                // z_ also is 1, second derivative w.r.t. index 1
                final double da = this.derivative(1, x_);
                derivative = (da / _slopeParam) + (da * da / val) - (da * da / (1.0 - val));
            }
            else
            {
                // mixed second derivative.
                final double multiple = (-2.0 * _slopeParam)
                        + (-2.0 * _slopeParam * _slope * (x_ - _center)) * (1.0 - 2.0 * val);
                derivative = multiple * val * (1.0 - val);
            }

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
