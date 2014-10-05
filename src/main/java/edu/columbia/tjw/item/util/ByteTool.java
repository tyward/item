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
public final class ByteTool
{
    private ByteTool()
    {
    }

    private static long fromBytes(final byte[] input_, final int offset_, final int byteCount_)
    {
        if ((byteCount_ < 1) || (byteCount_ > 8))
        {
            throw new IllegalArgumentException("Invalid byte count: " + byteCount_);
        }

        long output = 0;

        for (int i = 0; i < byteCount_; i++)
        {
            final int next = input_[offset_ + i];
            final int converted = (0xFF & next);
            output = output << 8;
            output += converted;
        }

        return output;
    }

    private static void toBytes(final long input_, final int offset_, final byte[] output_, final int byteCount_)
    {
        long workspace = input_;

        for (int i = 0; i < byteCount_; i++)
        {
            final byte thisByte = (byte) (workspace & 0xFFL);
            workspace = workspace >> 8;
            output_[offset_ + ((byteCount_ - 1) - i)] = thisByte;
        }
    }

    public static byte[] longToBytes(final long input_)
    {
        final byte[] output = new byte[8];
        longToBytes(input_, 0, output);
        return output;
    }

    public static void longToBytes(final long input_, final int offset_, final byte[] output_)
    {
        toBytes(input_, offset_, output_, 8);
    }

    public static long bytesToLong(final byte[] input_, final int offset_)
    {
        final long output = fromBytes(input_, offset_, 8);
        return output;
    }

}
