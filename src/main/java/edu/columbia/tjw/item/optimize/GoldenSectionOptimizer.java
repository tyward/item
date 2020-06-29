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
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.fit.calculator.BlockCalculationType;
import edu.columbia.tjw.item.fit.calculator.FitPoint;
import edu.columbia.tjw.item.fit.calculator.FitPointAnalyzer;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.logging.Logger;

/**
 * @author tyler
 */
public final class GoldenSectionOptimizer
{
    private static final double MAX_BRACKET_SCALE = 2000.0;
    private static final double STD_DEV_CUTOFF = 1.0;
    private static final Logger LOG = LogUtil.getLogger(GoldenSectionOptimizer.class);
    private static final double PHI = 0.5 * (1.0 + Math.sqrt(5.0));
    private static final double INV_PHI = 1.0 / PHI;
    private static final double LINE_SEARCH_REL_TOL = 0.00001;

    private final double _stdDevThreshold;
    private final double _xTol;
    private final double _yTol;
    private final int _blockSize;
    private final int _maxEvalCount;

    private final FitPointAnalyzer _comparator;

    public GoldenSectionOptimizer(final double xTol_, final double yTol_, final int blockSize_, int maxEvalCount_,
                                  final OptimizationTarget target_, ItemSettings settings_)
    {
        _blockSize = blockSize_;
        _xTol = xTol_;
        _yTol = yTol_;
        _maxEvalCount = maxEvalCount_;
        _stdDevThreshold = settings_.getZScoreCutoff();

        _comparator = new FitPointAnalyzer(_blockSize, target_, settings_);
    }

    public OptimizationResult optimize(final UnivariateOptimizationFunction f_)
            throws ConvergenceException
    {
        //final AdaptiveComparator<V, F> comparator = this.getComparator();
//        MultivariatePoint scaleStep = scaleStep_;
//        MultivariatePoint a = startingPoint_.clone();
//        MultivariatePoint b = a.clone();
//        MultivariatePoint c = a.clone();
//
//        a.add(scaleStep);
//        scaleStep.scale(-1.0);
//        c.add(scaleStep);

        final FitPoint pointA = f_.evaluate(-1.0);
        final FitPoint pointB = f_.evaluate(0.0);
        final FitPoint pointC = f_.evaluate(1.0);

        final Bracket b1 = new Bracket(-1.0, 0.0, 1.0, pointA, pointB, pointC);

        final Bracket bracket = this.bracket(f_, b1);

        final OptimizationResult result = this.optimize(f_, bracket);
        return result;
    }

    public OptimizationResult optimize(final UnivariateOptimizationFunction f_, final double a_,
                                       final FitPoint aRes_,
                                       final double b_, final FitPoint bRes_)
            throws ConvergenceException
    {
        if (a_ == b_)
        {
            throw new IllegalArgumentException("A cannot equal B.");
        }
//        if (a_ > b_)
//        {
//            return optimize(f_, b_, bRes_, a_, aRes_);
//        }

        //Just need to fill this in.....
        // TODO: This should not be needed....
        final FitPoint pointA = f_.evaluate(a_);
        final FitPoint pointB = f_.evaluate(b_);
        final double comparison = _comparator.compare(pointA, pointB);

        if (comparison < 0)
        {
            return optimize(f_, b_, bRes_, a_, aRes_);
        }
        if (0 == comparison)
        {
            return optimize(f_);
        }

        final double direction = (b_ - a_);
//        final MultivariatePoint direction = a_.clone();
//        direction.scale(-1.0);
//        direction.add(b_);

        //vector from a -> b. 


        //A is greater than B.
        final double c = b_ + direction;
        final FitPoint pointC = f_.evaluate(c);
//        final MultivariatePoint c = b_.clone();
//        c.add(direction);
//        final FitPoint pointC = f_.evaluate(c.getElements());
        final Bracket b1;

        if (c > a_)
        {
            b1 = new Bracket(a_, b_, c, pointA, pointB, pointC);
        }
        else
        {
            b1 = new Bracket(c, b_, a_, pointC, pointB, pointA);
        }

        final Bracket bracket = this.bracket(f_, b1);
        return this.optimize(f_, bracket);
    }


    private static double computeQuadraticMinimum(final double a, final double b, final double c,
                                                  final double aVal, final double bVal, final double cVal)
    {
        final double ac = c - a;

        //Assume f(x) is quadratic, (alpha)a^2 + (beta)a + (gamma) = value(a).
        //Now we have three equations and three unknowns, solve....
        final double a2 = a * a;
        final double b2 = b * b;
        final double c2 = c * c;
        final double dab = (a - b);
        final double dac = (a - c);
        final double d2ab = (a2 - b2);
        final double d2ac = (a2 - c2);
        final double dvac = (aVal - cVal);
        final double dvab = (aVal - bVal);

        final double dRatio = dab / dac;
        final double alphaNum = (dvac * dRatio) - dvab;
        final double alphaDenom = (d2ac * dRatio) - d2ab;
        final double alpha = alphaNum / alphaDenom;

        final double presumedMinimum;

        //Alpha is the 2nd derivative of this function, let's see if it's positive (indicating we will find a
        // minimum).
        if (alpha <= 0.0)
        {
            //Drat, this has the wrong convexity.
            presumedMinimum = c;
        }
        else
        {
            final double beta = (dvab - (d2ab * alpha)) / dab;

            //We don't need gamma.
            //f'(x) = 2*alpha*x + beta, set that to zero.....
            final double extremum = -beta / (2.0 * alpha);

            final double sanityValue = Math.abs(a) + Math.abs(c);

            // True if the extremum is on the opposite side of C as A. we have [a, b, c, extremum]
            final boolean beyondC = ((extremum - c) * ac) > 0.0;

            if (Math.abs(extremum) > 100.0 * sanityValue)
            {
                //Root is too far away, we don't actually believe these results.
                //Fall back.
                presumedMinimum = c;
            }
            else if (!beyondC)
            {
                presumedMinimum = c;
            }
            else
            {
                presumedMinimum = extremum;
            }
        }

        return presumedMinimum;
    }


    /**
     * We start with a bracket where we know that a > b (to at least
     * comparator.sigmaTarget())
     * <p>
     * We then need only to compute a C that is known to be greater than b (we
     * may also adjust b).
     *
     * @param f_       The function to bracket
     * @param bracket_ The initial guess for the bracket, with f(a) > f(b)
     * @return A new bracket [a, b, c] with f(a) > f(b) and f(c) > f(b)
     * @throws ConvergenceException If no such bracket can be constructed
     */
    private Bracket completeBracket(final UnivariateOptimizationFunction f_, final Bracket bracket_)
            throws ConvergenceException
    {
        BlockCalculationType valType = BlockCalculationType.VALUE;

        //We know that the three points are in order, but don't know how they compare. 
        double a = bracket_.getA();
        double b = bracket_.getB();
        double c = bracket_.getC();
        double ac = (c - a);

        final double initMag = Math.abs(ac);

        //Compute the vector from a -> b. 
        double ab = (b - a);

        FitPoint pointA = bracket_.getaRes();
        FitPoint pointB = bracket_.getbRes();
        FitPoint pointC = bracket_.getcRes();

        double comparisonAB = _comparator.compare(pointA, pointB);
        final double sigmaScale = _comparator.getSigmaTarget();

        if (comparisonAB < sigmaScale)
        {
            throw new IllegalArgumentException("Impossible.");
        }

        double comparisonCB = _comparator.compare(pointC, pointB);
        double scale = 0.5;

        while (comparisonCB < sigmaScale)
        {
            if (ab > MAX_BRACKET_SCALE * initMag)
            {
                throw new ConvergenceException("Unable to bracket root.");
            }

            while (Math.abs(comparisonCB) < sigmaScale)
            {
                c += ab;
                ab *= 2;
                pointC = f_.evaluate(c);
                scale *= 2.0;

                if (scale > 2000.0)
                {
                    throw new ConvergenceException("Unable to bracket root.");
                }

                comparisonCB = _comparator.compare(pointC, pointB);
            }

            if ((a < b) != (b < c))
            {
                throw new IllegalArgumentException("Not in order!");
            }

            if (comparisonCB >= sigmaScale)
            {
                //We are done, return the bracket. 
                return new Bracket(a, b, c, pointA, pointB, pointC);
            }

            //We are sure the C differs from B, but it is less than B instead of greater. 
//            final double aScalar = a.project(ab);
//            final double bScalar = b.project(ab);
//            final double cScalar = c.project(ab);

            int highWater = Math.max(pointA.getNextBlock(valType), pointB.getNextBlock(valType));
            highWater = Math.max(highWater, pointC.getNextBlock(valType));


            final double aVal = _comparator.computeObjective(pointA, highWater);
            final double bVal = _comparator.computeObjective(pointB, highWater);
            final double cVal = _comparator.computeObjective(pointC, highWater);

            final double presumedMinimum = computeQuadraticMinimum(a, b, c, aVal, bVal, cVal);

            // Presumed minimum is either c, or > c.


            //We know that f(a) > f(b) > f(c), and we have a new estimate for the minimum.
            //Therefore, b -> a (since we know b > c), c -> b
            //Now, if min != c, then min is past c, so we can put in the minimum and check if min > b. 
            //Now, if min < b, it becomes the new b, and c becomes b + (b - a). 
            final double aClone = a;
            final double pointerScale = (presumedMinimum - a) / ab;
            ab *= pointerScale;

            a = b;
            b = c;

            pointA = pointB;
            pointB = pointC;

            final boolean beyondC = ((presumedMinimum - c) * ac) > 0.0;

            if (!beyondC)
            {
                //Compute new value of c, the old value got assigned to b.
                ab = (b - a);
                c += ab;
                pointC = f_.evaluate(c);
            }
            else
            {
                c = aClone + ab;
                pointC = f_.evaluate(c);
                comparisonCB = _comparator.compare(pointC, pointB);

                //Assumed minimum higher than previous value of b. We are done.
                if (comparisonCB > sigmaScale)
                {
                    return new Bracket(a, b, c, pointA, pointB, pointC);
                }
                if (comparisonCB < -sigmaScale)
                {
                    //Minimum definitely lower than b, we can rotate b -> a, c -> b, compute new c.
                    a = b;
                    b = c;

                    ab = b - a;

                    c += ab;
                    pointA = pointB;
                    pointB = pointC;
                    pointC = f_.evaluate(c);
                }
                else
                {
                    //Minimum likely lower than b, replace b only, compute new c, don't update ab. 
                    b = c;
                    c = c + ab;
                    pointB = pointC;
                    pointC = f_.evaluate(c);
                }
            }


            //Now we know that a > b, and we think that c is probably higher than b, but haven't checked. 
            comparisonCB = _comparator.compare(pointC, pointB);
        }

        final Bracket output = new Bracket(a, b, c, pointA, pointB, pointC);
        return output;
    }

    private Bracket bracket(final UnivariateOptimizationFunction f_, final Bracket bracket_)
            throws ConvergenceException
    {
        //final FitPointAnalyzer comparator = this.getComparator();

        //We know that the three points are in order, but don't know how they compare. 
        double a = bracket_.getA();
        double b = bracket_.getB();
        double c = bracket_.getC();
        double ac = (c - a);
        double ca = -ac;

        FitPoint pointA = bracket_.getaRes();
        FitPoint pointB = bracket_.getbRes();
        FitPoint pointC = bracket_.getcRes();

        //A negative value here indicates that a is lower than b. 
        double comparisonAB = _comparator.compare(pointA, pointB);
        final double sigmaScale = _comparator.getSigmaTarget();
        double scale = 0.5;

        while (Math.abs(comparisonAB) < sigmaScale)
        {
            //We will move both endpoints out trying to find one that is materially different from b. 
            //Perhaps B and C are sufficiently different, let's try that. 
            final double comparisonBC = _comparator.compare(pointB, pointC);

            if (Math.abs(comparisonBC) > sigmaScale)
            {
                //Swap the bracket order, and restart now that we know c and b are different.
                final Bracket swapped = new Bracket(c, b, a, pointC, pointB, pointA);
                return bracket(f_, swapped);
            }

            if (Math.abs(comparisonBC + comparisonAB) > sigmaScale)
            {
                //Both endpoints failed, but we think they differ enough from each other. 
                final double comparisonAC = _comparator.compare(pointA, pointC);

                if (Math.abs(comparisonAC) > sigmaScale)
                {
                    //Success
                    //b.copy(c);
                    //b.add(ac);
                    b = c + ac;
                    pointB = f_.evaluate(b);
                    final Bracket expanded = new Bracket(a, c, b, pointA, pointC, pointB);
                    return bracket(f_, expanded);
                }
            }

            //move them both out and try again.
            ca *= 2.0;
            ac *= 2.0;
            a += ca;
            c += ac;
            scale *= 2.0;
            pointA = f_.evaluate(a);
            pointC = f_.evaluate(c);

            if (scale > MAX_BRACKET_SCALE)
            {
                throw new ConvergenceException("Unable to bracket root.");
            }

            comparisonAB = _comparator.compare(pointA, pointB);
        }

        //OK, we know that a and b are significantly different. 
        //Get the ordering right.
        if (comparisonAB > 0)
        {
            //A is higher than B. 
            final Bracket expanded = new Bracket(a, b, c, pointA, pointB, pointC);
            return completeBracket(f_, expanded);
        }
        else
        {
            //B is higher than A, so C needs to switch sides.
            c = a + ca;
            //B is higher than A, move to the other side.
            final Bracket expanded = new Bracket(b, a, c, pointB, pointA, f_.evaluate(c));
            return completeBracket(f_, expanded);
        }
    }

    private OptimizationResult optimize(final UnivariateOptimizationFunction f_, final Bracket bracket_)
            throws ConvergenceException
    {
        final double bracketSize = bracket_.getBracketSize();
        final double targetSize = LINE_SEARCH_REL_TOL * bracketSize;

        //LOG.info("Starting Golden Section optimization: [" + a_ + "][" + b_ + "][" + c_ + "]");
        //MultivariatePoint scaleStep = bracket_.getDirection();
        double a = bracket_.getA();
        double b = bracket_.getB();
        double c = bracket_.getC();

        FitPoint pointA = bracket_.getaRes();
        FitPoint pointB = bracket_.getbRes();
        FitPoint pointC = bracket_.getcRes();

        int evalCount = 0;
        boolean xTolCheck = this.checkXTolerance(a, c, targetSize);
        boolean yTolCheck = this.checkYTolerance(pointA, pointB, pointC);

        //While either tolerance condition fails, continue to loop.
        while (!(xTolCheck || yTolCheck) && evalCount < this._maxEvalCount)
        {
            double next;
            final double abDistance = Math.abs(b - a);
            final double bcDistance = Math.abs(c - b);

            final boolean aSide = (abDistance > bcDistance);

            //always want it to go a, b, next, c.
            if (aSide)
            {
                next = a + (abDistance * INV_PHI);

                //Swap next and b, let's always have b < next.
                final double temp = b;
                b = next;
                pointB = f_.evaluate(b);
                next = temp;
            }
            else
            {
                next = b + (bcDistance * INV_PHI);
            }

            if (Double.isNaN(next) || Double.isInfinite(next))
            {
                throw new IllegalStateException("Undefined value.");
            }

            final FitPoint nextPoint = f_.evaluate(next);
            final FitPointAnalyzer.FitPointComparison comparison = _comparator.generateComparision(pointB, nextPoint);
            evalCount++;

            final boolean nextLower = (comparison.getZScore() > 0);

            //First, assume not aSide (so b < next), will fix later.
            if (nextLower)
            {
                //B is greater than next, so keep b, next, c.
                a = b;
                pointA = pointB;
                b = next;
                pointB = nextPoint;
            }
            else
            {
                //B is less than next, keep a, b, next
                c = next;
                pointC = nextPoint;
            }

            xTolCheck = this.checkXTolerance(a, c, targetSize);
            yTolCheck = this.checkYTolerance(pointA, pointB, pointC);

            //System.out.println("Best point: " + b);
            //Make sure our new middle point is meaningfully better than one of the end points
            //otherwise, get out. This probably won't require any actual calculation...
            final double aCompare = _comparator.compare(pointA, pointB);
            final double cCompare = _comparator.compare(pointC, pointB);

            if ((aCompare < STD_DEV_CUTOFF) && (cCompare < STD_DEV_CUTOFF))
            {
                LOG.info("Unable to make further progress: " + a + " - " + c);
                //We are done here, nothing else to be found.
                break;
            }
        }

        //System.out.println("Returning from golden section.");
        //We passed some tolerance tests, let's return the answer.
        final GeneralOptimizationResult output =
                new GeneralOptimizationResult(new MultivariatePoint(f_.generatePoint(b)), pointB,
                        true,
                        evalCount);
        return output;
    }


    private boolean checkXTolerance(final double a_, final double b_, final double target_)
    {
        final double distance = Math.abs(a_ - b_);
        return distance < target_;
    }

    private boolean checkYTolerance(final FitPoint aResult_, final FitPoint bResult_)
    {
        // Make sure everything has the same (approximate) level of computed results.
        final int highWater = Math.max(aResult_.getNextBlock(BlockCalculationType.VALUE),
                bResult_.getNextBlock(BlockCalculationType.VALUE));
        final double meanA = _comparator.computeObjective(aResult_, highWater);
        final double meanB = _comparator.computeObjective(bResult_, highWater);

        final double scaledDiff = Math.abs(meanA - meanB) / (meanA + meanB);
        return scaledDiff < this._yTol;
    }

    private boolean checkYTolerance(final FitPoint aResult_, final FitPoint bResult_,
                                    final FitPoint cResult_)
    {
        // Make sure everything has the same (approximate) level of computed results.
        final BlockCalculationType valType = BlockCalculationType.VALUE;

        int highWater = Math.max(aResult_.getNextBlock(valType),
                bResult_.getNextBlock(valType));
        highWater = Math.max(cResult_.getNextBlock(valType), highWater);
        aResult_.computeUntil(highWater, valType);
        bResult_.computeUntil(highWater, valType);
        cResult_.computeUntil(highWater, valType);

        final boolean checkA = checkYTolerance(aResult_, bResult_);
        final boolean checkB = checkYTolerance(bResult_, cResult_);

        final boolean output = checkA && checkB;
        return output;
    }


    private static final class Bracket
    {
        private final double _a;
        private final double _b;
        private final double _c;
        //        private final MultivariatePoint _direction;
//        private final MultivariatePoint _negDirection;
        private final FitPoint _aRes;
        private final FitPoint _bRes;
        private final FitPoint _cRes;

        public Bracket(final double a_, final double b_, final double c_,
                       final FitPoint aRes_, final FitPoint bRes_
                , final FitPoint cRes_)
        {
            checkWellDefined(a_);
            checkWellDefined(b_);
            checkWellDefined(c_);
//            checkOrdered(a_, b_);
//            checkOrdered(b_, c_);

            if ((a_ < b_) != (b_ < c_))
            {
                throw new IllegalArgumentException("Not in order!");
            }

            // We now put these in order such that a_ < b_ < c_;

//            if (a_ < c_)
//            {
            _a = a_;
            _b = b_;
            _c = c_;
            _aRes = aRes_;
            _bRes = bRes_;
            _cRes = cRes_;
//            }
//            else
//            {
//                _c = a_;
//                _b = b_;
//                _a = c_;
//                _cRes = aRes_;
//                _bRes = bRes_;
//                _aRes = cRes_;
//            }
        }

        private static void checkOrdered(final double a_, final double b_)
        {
            if (a_ >= b_)
            {
                throw new IllegalArgumentException("Incorrect order: " + a_ + " >= " + b_);
            }
        }

        private static void checkWellDefined(final double val_)
        {
            if (Double.isNaN(val_))
            {
                throw new IllegalArgumentException("Value is NaN.");
            }
            if (Double.isInfinite(val_))
            {
                throw new IllegalArgumentException("Value is infinite.");
            }
        }

        public double getA()
        {
            return _a;
        }

        public double getB()
        {
            return _b;
        }

        public double getC()
        {
            return _c;
        }

//        public MultivariatePoint getDirection()
//        {
//            return _direction;
//        }
//
//        public MultivariatePoint getNegDirection()
//        {
//            return _negDirection;
//        }

        public FitPoint getaRes()
        {
            return _aRes;
        }

        public FitPoint getbRes()
        {
            return _bRes;
        }

        public FitPoint getcRes()
        {
            return _cRes;
        }

        public double getBracketSize()
        {
            return Math.abs(_a - _c);
        }
    }

}
