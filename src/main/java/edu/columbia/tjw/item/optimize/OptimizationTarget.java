package edu.columbia.tjw.item.optimize;

public enum OptimizationTarget
{
    // Standard MLE
    ENTROPY,
    // Direct usage of ICE correction term as-is, but with derivatives as per ICE2.
    TIC,
    // Direct usage of diagonalized ICE correction, but with ICE2 derivatives.
    ICE,
    // Numerically stabilized ICE correction.
    ICE2,
    // Objective is same as ICE2, but with optimized derivative computation.
    ICE3;
}
