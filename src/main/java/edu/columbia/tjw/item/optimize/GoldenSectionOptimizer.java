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

import edu.columbia.tjw.item.util.LogUtil;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <V>
 * @param <F>
 */
public class GoldenSectionOptimizer<V extends EvaluationPoint<V>, F extends OptimizationFunction<V>> extends Optimizer<V, F>
{
    private static final double STD_DEV_CUTOFF = 1.0;
    private static final Logger LOG = LogUtil.getLogger(GoldenSectionOptimizer.class);
    private static final double PHI = 0.5 * (1.0 + Math.sqrt(5.0));
    private static final double INV_PHI = 1.0 / PHI;

    public GoldenSectionOptimizer(final int bloackSize_, int maxEvalCount_)
    {
        super(bloackSize_, maxEvalCount_);
    }

    public GoldenSectionOptimizer(final double xTol_, final double yTol_, final int bloackSize_, int maxEvalCount_)
    {
        super(xTol_, yTol_, bloackSize_, maxEvalCount_);
    }

    @Override
    public OptimizationResult<V> optimize(final F f_, final V startingPoint_, final V scaleStep_) throws ConvergenceException
    {
        final AdaptiveComparator<V, F> comparator = this.getComparator();
        V scaleStep = scaleStep_;
        V a = startingPoint_.clone();
        V b = a.clone();
        V c = a.clone();

        final EvaluationResult aRes = f_.generateResult();
        final EvaluationResult bRes = f_.generateResult();
        final EvaluationResult cRes = f_.generateResult();

        a.add(scaleStep);
        scaleStep.scale(-1.0);
        c.add(scaleStep);

        final Bracket<V> b1 = new Bracket<>(a, b, c, aRes, bRes, cRes);

        final Bracket<V> bracket = this.bracket(f_, b1);

        final OptimizationResult<V> result = this.optimize(f_, bracket);
        return result;
    }

    public OptimizationResult<V> optimize(final F f_, final V a_, final EvaluationResult aRes_,
            final V b_, final EvaluationResult bRes_) throws ConvergenceException
    {
        //Just need to fill this in.....
        final double comparison = this.getComparator().compare(f_, a_, b_, aRes_, bRes_);

        if (comparison < 0)
        {
            return optimize(f_, b_, bRes_, a_, aRes_);
        }

        final V direction = a_.clone();
        direction.scale(-1.0);
        direction.add(b_);

        //vector from a -> b. 
        if (0 == comparison)
        {
            return optimize(f_, a_, direction);
        }

        //A is greater than B. 
        final EvaluationResult cRes = f_.generateResult();
        final V c = b_.clone();
        c.add(direction);

        final Bracket<V> b1 = new Bracket<>(a_, b_, c, f_.generateResult(), f_.generateResult(), cRes);
        final Bracket<V> bracket = this.bracket(f_, b1);
        return this.optimize(f_, bracket);
    }

    private Bracket<V> bracket(final F f_, final Bracket<V> bracket_) throws ConvergenceException
    {
        final AdaptiveComparator<V, F> comparator = this.getComparator();

        //We know that the three points are in order, but don't know how they compare. 
        final V a = bracket_.getA().clone();
        final V b = bracket_.getB().clone();
        final V c = bracket_.getC().clone();
        final V ac = bracket_.getDirection().clone();
        final V ca = bracket_.getDirection().clone();
        ca.scale(-1.0);

        EvaluationResult aRes = bracket_.getaRes();
        EvaluationResult bRes = bracket_.getbRes();
        EvaluationResult cRes = bracket_.getcRes();

        //A negative value here indicates that a is lower than b. 
        double comparison = comparator.compare(f_, a, b, aRes, bRes);
        double scale = 0.5;

        boolean isCSet = false;

        while (comparison < 0.0)
        {
            isCSet = true;
            c.copy(b);
            b.copy(a);
            final EvaluationResult temp = cRes;
            cRes = bRes;
            bRes = aRes;
            aRes = temp;
            aRes.clear();

            ca.scale(2.0);
            a.add(ca);
            scale *= 2.0;

            if (scale > 200.0)
            {
                throw new ConvergenceException("Unable to bracket root.");
            }

            comparison = comparator.compare(f_, a, b, aRes, bRes);
        }

        if (!isCSet)
        {
            //We have a > b. now let's make sure e get c > b. 
            comparison = comparator.compare(f_, c, b, cRes, bRes);
            scale = 0.5;

            while (comparison < 0.0)
            {
                a.copy(b);
                b.copy(c);
                final EvaluationResult temp = aRes;
                aRes = bRes;
                bRes = cRes;
                cRes = temp;
                cRes.clear();

                ac.scale(2.0);
                c.add(ac);
                scale *= 2.0;

                if (scale > 200.0)
                {
                    throw new ConvergenceException("Unable to bracket root.");
                }

                comparison = comparator.compare(f_, c, b, cRes, bRes);
            }
        }

        final Bracket<V> output = new Bracket<>(a, b, c, aRes, bRes, cRes);
        return output;
    }

    private OptimizationResult<V> optimize(final F f_, final Bracket<V> bracket_) throws ConvergenceException
    {
        //LOG.info("Starting Golden Section optimization: [" + a_ + "][" + b_ + "][" + c_ + "]");
        V scaleStep = bracket_.getDirection();
        V a = bracket_.getA().clone();
        V b = bracket_.getB().clone();
        V c = bracket_.getC().clone();

        EvaluationResult aRes = bracket_.getaRes();
        EvaluationResult bRes = bracket_.getbRes();
        EvaluationResult cRes = bracket_.getcRes();
        int evalCount = 0;

        //While either tolerance condition fails, continue to loop.
        while (!(this.checkXTolerance(a, c) || this.checkYTolerance(aRes, bRes, cRes)))
        {
            V next;
            EvaluationResult nextRes = f_.generateResult();
            final double abDistance = a.distance(b);
            final double bcDistance = b.distance(c);

            scaleStep.normalize();

            final boolean aSide = (abDistance > bcDistance);

            //always want it to go a, b, next, c.
            if (aSide)
            {
                next = a.clone();
                scaleStep.scale(abDistance * INV_PHI);
                next.add(scaleStep);

                //Swap next and b, let's always have b < next.
                final V temp = b;
                final EvaluationResult tempRes = bRes;

                b = next;
                bRes = nextRes;
                next = temp;
                nextRes = tempRes;
            }
            else
            {
                next = b.clone();
                scaleStep.scale(bcDistance * INV_PHI);
                next.add(scaleStep);
                nextRes.clear();
            }

            final AdaptiveComparator<V, F> comparator = this.getComparator();
            final double comparison = comparator.compare(f_, b, next, bRes, nextRes);
            evalCount++;

            final boolean nextLower = (comparison > 0);
            //final boolean dropA = (aSide ^ nextLower);

            //First, assume not aSide (so b < next), will fix later.
            if (nextLower)
            {
                //B is greater than next, so keep b, next, c.
                a = b;
                aRes = bRes;
                b = next;
                bRes = nextRes;
            }
            else
            {
                //B is less than next, keep a, b, next
                c = next;
                cRes = nextRes;
            }

            //System.out.println("Best point: " + b);
            //Make sure our new middle point is meaningfully better than one of the end points
            //otherwise, get out. This probably won't require any actual calculation...
            final double aCompare = comparator.compare(f_, a, b, aRes, bRes);
            final double cCompare = comparator.compare(f_, c, b, cRes, bRes);

            if ((aCompare < STD_DEV_CUTOFF) && (cCompare < STD_DEV_CUTOFF))
            {
                LOG.info("Unable to make further progress: " + a + " - " + c);
                //We are done here, nothing else to be found.
                break;
            }
        }

        //System.out.println("Returning from golden section.");
        //We passed some tolerance tests, let's return the answer.
        final GeneralOptimizationResult<V> output = new GeneralOptimizationResult<>(b, bRes, true, evalCount);
        return output;
    }

    private static final class Bracket<V extends EvaluationPoint<V>>
    {
        private final V _a;
        private final V _b;
        private final V _c;
        private final V _direction;
        private final V _negDirection;
        private final EvaluationResult _aRes;
        private final EvaluationResult _bRes;
        private final EvaluationResult _cRes;

        public Bracket(final V a_, final V b_, final V c_, final EvaluationResult aRes_, final EvaluationResult bRes_, final EvaluationResult cRes_)
        {
            _a = a_;
            _b = b_;
            _c = c_;
            _aRes = aRes_;
            _bRes = bRes_;
            _cRes = cRes_;

            //We require that a_, b_, and c_ be distinct and in order. 
            _direction = a_.clone();
            _direction.scale(-1.0);
            _direction.add(c_);

            final V ab = _a.clone();
            ab.scale(-1.0);
            ab.add(_b);

            final double aProj = _a.project(_direction);
            final double bProj = _b.project(_direction);
            final double cProj = _c.project(_direction);

            if ((aProj < bProj) != (bProj < cProj))
            {
                throw new IllegalArgumentException("Not in order!");
            }

            _negDirection = _direction.clone();
            _negDirection.scale(-1.0);
        }

        public V getA()
        {
            return _a;
        }

        public V getB()
        {
            return _b;
        }

        public V getC()
        {
            return _c;
        }

        public V getDirection()
        {
            return _direction;
        }

        public V getNegDirection()
        {
            return _negDirection;
        }

        public EvaluationResult getaRes()
        {
            return _aRes;
        }

        public EvaluationResult getbRes()
        {
            return _bRes;
        }

        public EvaluationResult getcRes()
        {
            return _cRes;
        }
    }

}
