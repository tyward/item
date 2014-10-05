/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util.random;

import edu.columbia.tjw.item.util.ByteTool;
import edu.columbia.tjw.item.util.HashTool;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

/**
 *
 * @author tyler
 */
public class RandomTool
{
    private static final SecureRandom CORE;

    static
    {
        try
        {
            CORE = SecureRandom.getInstance("NativePRNGNonBlocking");
        }
        catch (final NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    private RandomTool()
    {
    }

    public synchronized static double nextDouble()
    {
        return CORE.nextDouble();
    }

    /**
     *
     * @param max_
     * @return
     */
    public static int nextInt(final int max_)
    {
        if (max_ <= 0)
        {
            throw new IllegalArgumentException("Max must be positive.");
        }

        final double selector = nextDouble();
        final int selected = (int) (selector * max_);
        return selected;
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

    public static Random getRandom(final PrngType type_)
    {
        final byte[] seed = getStrong(32);
        final Random output = getRandom(type_, seed);
        return output;
    }

    public static Random getRandom(final PrngType type_, final byte[] seed_)
    {
        final byte[] whitened = HashTool.hash(seed_);
        final Random output;

        switch (type_)
        {
            case STANDARD:
                output = new Random(ByteTool.bytesToLong(whitened, 0));
                break;
            case SECURE:
                output = new SecureRandom(whitened);
                break;
            default:
                throw new IllegalArgumentException("Unknown PRNG type.");
        }

        return output;
    }
}
