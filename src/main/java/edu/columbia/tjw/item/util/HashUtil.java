/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util;

/**
 *
 * @author tyler
 */
public final class HashUtil
{
    private static final int START_CONSTANT = 2309289;
    private static final int MIX_CONSTANT = 1091349811;
    private static final int MASK = 28329;

    private HashUtil()
    {
    }

    public static int startHash(final Class<?> clazz_)
    {
        final String className = clazz_.getCanonicalName();
        final int nameHash = className.hashCode();
        final int hash = mix(START_CONSTANT, nameHash);
        return hash;
    }

    public static int mix(final int hash_, final int mixIn_)
    {
        final int hash = MASK + MIX_CONSTANT * (hash_ + mixIn_);
        return hash;
    }

    public static int mix(final int hash_, final long input_)
    {
        final int input1 = (int) (input_ & 0xFFFFFFFFL);
        final int input2 = (int) ((input_ >> 32) & 0xFFFFFFFFL);
        int hash = mix(hash_, input1);
        hash = mix(hash, input2);
        return hash;
    }

}
