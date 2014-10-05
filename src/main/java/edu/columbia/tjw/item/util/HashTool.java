/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author tyler
 */
public final class HashTool
{
    private static final HashTool HASHER = new HashTool();
    private final MessageDigest _hash;

    public HashTool()
    {
        _hash = getHashFunction();
    }

    public synchronized static byte[] hash(final byte[] input_)
    {
        final byte[] output = HASHER.hashBytes(input_);
        return output;
    }

    public static MessageDigest getHashFunction()
    {
        try
        {
            final MessageDigest output = MessageDigest.getInstance("SHA-256");
            return output;
        } catch (final NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    public byte[] hashBytes(final byte[] input_)
    {
        _hash.reset();
        final byte[] output = _hash.digest(input_);
        return output;
    }

}
