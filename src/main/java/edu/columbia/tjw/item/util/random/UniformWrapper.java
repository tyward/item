package edu.columbia.tjw.item.util.random;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.rng.UniformRandomProvider;

import java.util.Random;

/**
 * Wrap a commons-rng in a RandomGenerator wrapper.
 */
public class UniformWrapper implements RandomGenerator
{
    private final UniformRandomProvider _provider;

    public UniformWrapper(final UniformRandomProvider provider_)
    {
        _provider = provider_;
    }

    @Override
    public void setSeed(int seed)
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setSeed(int[] seed)
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setSeed(long seed)
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void nextBytes(byte[] bytes)
    {
        _provider.nextBytes(bytes);
    }

    @Override
    public int nextInt()
    {
        return _provider.nextInt();
    }

    @Override
    public int nextInt(int n)
    {
        return _provider.nextInt(n);
    }

    @Override
    public long nextLong()
    {
        return _provider.nextLong();
    }

    @Override
    public boolean nextBoolean()
    {
        return _provider.nextBoolean();
    }

    @Override
    public float nextFloat()
    {
        return _provider.nextFloat();
    }

    @Override
    public double nextDouble()
    {
        return _provider.nextDouble();
    }


    private double nextNextGaussian;
    private boolean haveNextNextGaussian = false;

    /**
     * Cloned from java.util.Random.
     */
    @Override
    synchronized public double nextGaussian()
    {
        // See Knuth, ACP, Section 3.4.1 Algorithm C.
        if (haveNextNextGaussian)
        {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        else
        {
            double v1, v2, s;
            do
            {
                v1 = 2 * nextDouble() - 1; // between -1 and 1
                v2 = 2 * nextDouble() - 1; // between -1 and 1
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);
            double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
            nextNextGaussian = v2 * multiplier;
            haveNextNextGaussian = true;
            return v1 * multiplier;
        }
    }
}
