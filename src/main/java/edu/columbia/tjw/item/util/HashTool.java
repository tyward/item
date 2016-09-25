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
        }
        catch (final NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void updateString(final String input_)
    {
        _hash.update(input_.getBytes());
    }

    public void updateLong(final long input_)
    {
        final byte[] vals = ByteTool.longToBytes(input_);
        _hash.update(vals);
    }

    public byte[] doHash()
    {
        final byte[] output = _hash.digest();
        _hash.reset();
        return output;
    }

    public long doHashLong()
    {
        final byte[] hash = doHash();
        final long output = ByteTool.bytesToLong(hash, 0);
        return output;
    }

    public long stringToLong(final String input_)
    {
        final byte[] hash = this.hashBytes(input_.getBytes());

        //This is a strong hash, no need for mixing, just truncate.
        final long output = ByteTool.bytesToLong(hash, 0);
        return output;
    }

    public byte[] hashBytes(final byte[] input_)
    {
        _hash.reset();
        final byte[] output = _hash.digest(input_);
        return output;
    }

}
