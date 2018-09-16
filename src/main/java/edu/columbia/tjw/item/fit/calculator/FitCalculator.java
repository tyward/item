package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;

import java.util.Collection;
import java.util.List;

public interface FitCalculator<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    public static final EntropyAnalysis VACUOUS_ENTROPY = new EntropyAnalysis(0.0, 0.0, 0);

    public EntropyAnalysis computeEntropy(final ItemParameters<S, R, T> params_);


    public final class EntropyAnalysis
    {
        private final double _sumEntropy;
        private final double _sumEntropy2;
        private final int _size;

        public EntropyAnalysis(final double sumEntropy_, final double sumEntropy2_, final int size_)
        {
            if (!(sumEntropy_ >= 0.0) || Double.isInfinite(sumEntropy_))
            {
                throw new IllegalArgumentException("Illegal entropy: " + sumEntropy_);
            }
            if (!(sumEntropy2_ >= 0.0) || Double.isInfinite(sumEntropy2_))
            {
                throw new IllegalArgumentException("Illegal entropy: " + sumEntropy2_);
            }
            if (size_ < 0.0)
            {
                throw new IllegalArgumentException("Size cannot be negative.");
            }

            _sumEntropy = sumEntropy_;
            _sumEntropy2 = sumEntropy2_;
            _size = size_;
        }

        public EntropyAnalysis(final Collection<EntropyAnalysis> analysisList_)
        {
            double h = 0.0;
            double h2 = 0.0;
            int count = 0;

            for (final EntropyAnalysis next : analysisList_)
            {
                h += next._sumEntropy;
                h2 += next._sumEntropy2;
                count += next._size;
            }

            _sumEntropy = h;
            _sumEntropy2 = h2;
            _size = count;
        }


        public double getEntropySum()
        {
            return _sumEntropy;
        }

        public double getEntropyMean()
        {
            return _sumEntropy / _size;
        }

        public double getSumVariance()
        {
            final double eX = getEntropyMean();
            final double eX2 = _sumEntropy2 / _size;
            final double var = Math.max(0.0, eX2 - (eX * eX));
            return var;
        }


        public double getMeanVariance()
        {
            return getSumVariance() / _size;
        }

        public double getMeanDev()
        {
            return Math.sqrt(getMeanVariance());
        }


        public int getSize()
        {
            return _size;
        }

    }
}
