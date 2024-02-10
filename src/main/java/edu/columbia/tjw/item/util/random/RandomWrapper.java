package edu.columbia.tjw.item.util.random;

import org.apache.commons.math3.random.RandomGenerator;

import java.util.Random;

public class RandomWrapper implements RandomGenerator
{
    private final Random _rand;

    public RandomWrapper(final Random rand_) {
        if(rand_ == null) {
            throw new NullPointerException("Rand cannot be null.");
        }
        _rand = rand_;
    }

    @Override
    public void setSeed(int seed)
    {
        _rand.setSeed(seed);
    }

    @Override
    public void setSeed(int[] seed)
    {
        long mixed = 0;

        for(int i = 0; i < seed.length; i++) {
            mixed = 37 * mixed + seed[i];
        }

        setSeed(mixed);
    }

    @Override
    public void setSeed(long seed)
    {
        _rand.setSeed(seed);
    }

    @Override
    public void nextBytes(byte[] bytes)
    {
        _rand.nextBytes(bytes);
    }

    @Override
    public int nextInt()
    {
        return _rand.nextInt();
    }

    @Override
    public int nextInt(int n)
    {
        return _rand.nextInt(n);
    }

    @Override
    public long nextLong()
    {
        return _rand.nextLong();
    }

    @Override
    public boolean nextBoolean()
    {
        return _rand.nextBoolean();
    }

    @Override
    public float nextFloat()
    {
        return _rand.nextFloat();
    }

    @Override
    public double nextDouble()
    {
        return _rand.nextDouble();
    }

    @Override
    public double nextGaussian()
    {
        return _rand.nextGaussian();
    }
}
