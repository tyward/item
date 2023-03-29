/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util.random;

import edu.columbia.tjw.item.util.ByteTool;
import edu.columbia.tjw.item.util.HashTool;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.rng.RestorableUniformRandomProvider;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source64.MersenneTwister64;
import org.apache.commons.rng.simple.RandomSource;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/**
 * @author tyler
 */
public class RandomTool
{
    private static final SecureRandom CORE;

    static
    {
        // generate a core source of randomness, and try to do it without causing blocking due to
        // underlying OS /dev/random implementation (essentially a bug in Linux).
        try
        {
            final SecureRandom seedGenerator = SecureRandom.getInstance("NativePRNGNonBlocking");
            CORE = SecureRandom.getInstance("SHA1PRNG");
            CORE.setSeed(seedGenerator.generateSeed(32));
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Unable to generate random generator: " + e, e);
        }
    }

    private RandomTool()
    {
    }

    public static void main(final String[] args_)
    {
        for (int i = 0; i < 100; i++)
        {
            final long next = CORE.nextLong();

            System.out.println(next + "L, 0x" + Long.toHexString(next) + "L");
        }
    }

    public synchronized static String randomString(final int length_)
    {
        final int longLength = 1 + (length_ / 8);

        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < longLength; i++)
        {
            builder.append(Long.toHexString(CORE.nextLong()));
        }

        final String output = builder.substring(0, length_);
        return output;
    }

    /**
     * Generates an integer in the range [0, max_)
     *
     * @param max_  The max (exclusive) of the range
     * @param rand_ The PRNG used to generate these random numbers
     * @return An integer in the range [0, max_), uniformly distributed
     */
    public static int nextInt(final int max_, final RandomGenerator rand_)
    {
        if (max_ <= 0)
        {
            throw new IllegalArgumentException("Max must be positive.");
        }

        final double selector = rand_.nextDouble();
        final int selected = (int) (selector * max_);
        return selected;
    }

    /**
     * @param input_ The array to be shuffled
     * @param rand_  The PRNG to use for the shuffle
     */
    public static void shuffle(final int[] input_, final RandomGenerator rand_)
    {
        for (int i = 0; i < input_.length; i++)
        {
            final int swapIndex = nextInt(input_.length, rand_);

            final int a = input_[i];
            final int b = input_[swapIndex];
            input_[swapIndex] = a;
            input_[i] = b;
        }
    }

    /**
     * Cloned from Collections in base package to adapt for RandomGenerator interface.
     * @param list
     * @param rnd
     */
    public static void shuffle(List<?> list, RandomGenerator rnd) {
        int size = list.size();
        if (size < 5 || list instanceof RandomAccess) {
            for (int i=size; i>1; i--)
            {
                Collections.swap(list, i - 1, rnd.nextInt(i));
            }
        } else {
            Object arr[] = list.toArray();

            // Shuffle array
            for (int i=size; i>1; i--)
            {
                swap(arr, i - 1, rnd.nextInt(i));
            }

            // Dump array back into list
            // instead of using a raw type here, it's possible to capture
            // the wildcard but it will require a call to a supplementary
            // private method
            ListIterator it = list.listIterator();
            for (int i=0; i<arr.length; i++) {
                it.next();
                it.set(arr[i]);
            }
        }
    }

    private static void swap(Object[] arr, int i, int j) {
        Object tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

    public synchronized static int nextInt()
    {
        final int output = CORE.nextInt();
        return output;
    }

    public synchronized static byte[] getStrong(final int bytes_)
    {
        final byte[] output = new byte[bytes_];
        CORE.nextBytes(output);
        return output;
    }

    public static RandomGenerator getRandomGenerator()
    {
        return getRandomGenerator(PrngType.STANDARD);
    }

    public static RandomGenerator getRandomGenerator(final PrngType type_)
    {
        final byte[] seed = getStrong(32);
        final RandomGenerator output = getRandomGenerator(type_, seed);
        return output;
    }

    public static RandomGenerator getRandomGenerator(final PrngType type_, final long seed_)
    {
        return getRandomGenerator(type_, ByteTool.longToBytes(seed_));
    }

    public static RandomGenerator getRandomGenerator(final PrngType type_, final byte[] seed_)
    {
        final byte[] whitened = HashTool.hash(seed_);

        switch (type_)
        {
            case STANDARD:
            {
                final Random rnd = new Random(ByteTool.bytesToLong(whitened, 0));
                return new RandomWrapper(rnd);
            }
            case SECURE:
            {
                try
                {
                    final SecureRandom output = SecureRandom.getInstance("SHA1PRNG");
                    output.setSeed(whitened);
                    return new RandomWrapper(output);
                }
                catch (final NoSuchAlgorithmException e)
                {
                    throw new RuntimeException(e);
                }
            }
            case MERSENNE_TWISTER:
            {
                final MersenneTwister64 tmp = null;
                final UniformRandomProvider source = RandomSource.MT_64.create(whitened);
                return new UniformWrapper(source);
            }
            default:
                throw new IllegalArgumentException("Unknown PRNG type.");
        }
    }
}
