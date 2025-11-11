/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.picnet.internal.util;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Utility class for PicNet data manipulation (bits, bytes, words, signed values)
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetDataUtils {

    /**
     * Get a specific bit from a word value
     *
     * @param wordValue the word value (0-65535)
     * @param bitPosition the bit position (1-16, 1 is LSB)
     * @return true if bit is 1, false if bit is 0
     */
    public static boolean getBit(int wordValue, int bitPosition) {
        if (bitPosition < 1 || bitPosition > 16) {
            throw new IllegalArgumentException("Bit position must be between 1 and 16");
        }
        return ((wordValue >> (bitPosition - 1)) & 1) == 1;
    }

    /**
     * Set a specific bit in a word value
     *
     * @param wordValue the word value (0-65535)
     * @param bitPosition the bit position (1-16, 1 is LSB)
     * @param bitValue the value to set (true=1, false=0)
     * @return the modified word value
     */
    public static int setBit(int wordValue, int bitPosition, boolean bitValue) {
        if (bitPosition < 1 || bitPosition > 16) {
            throw new IllegalArgumentException("Bit position must be between 1 and 16");
        }
        if (bitValue) {
            return wordValue | (1 << (bitPosition - 1));
        } else {
            return wordValue & ~(1 << (bitPosition - 1));
        }
    }

    /**
     * Get high byte from word
     *
     * @param wordValue the word value (0-65535)
     * @return the high byte (0-255)
     */
    public static int getHighByte(int wordValue) {
        return (wordValue >> 8) & 0xFF;
    }

    /**
     * Get low byte from word
     *
     * @param wordValue the word value (0-65535)
     * @return the low byte (0-255)
     */
    public static int getLowByte(int wordValue) {
        return wordValue & 0xFF;
    }

    /**
     * Convert unsigned word to signed word
     *
     * @param wordValue the unsigned word value (0-65535)
     * @return the signed word value (-32768 to 32767)
     */
    public static int toSignedWord(int wordValue) {
        if (wordValue > 32767) {
            return wordValue - 65536;
        }
        return wordValue;
    }

    /**
     * Convert signed word to unsigned word
     *
     * @param signedValue the signed word value (-32768 to 32767)
     * @return the unsigned word value (0-65535)
     */
    public static int toUnsignedWord(int signedValue) {
        if (signedValue < 0) {
            return signedValue + 65536;
        }
        return signedValue;
    }

    /**
     * Convert unsigned byte to signed byte
     *
     * @param byteValue the unsigned byte value (0-255)
     * @return the signed byte value (-128 to 127)
     */
    public static int toSignedByte(int byteValue) {
        if (byteValue > 127) {
            return byteValue - 256;
        }
        return byteValue;
    }

    /**
     * Convert signed byte to unsigned byte
     *
     * @param signedValue the signed byte value (-128 to 127)
     * @return the unsigned byte value (0-255)
     */
    public static int toUnsignedByte(int signedValue) {
        if (signedValue < 0) {
            return signedValue + 256;
        }
        return signedValue;
    }

    /**
     * Extract value based on subaddress specification
     *
     * @param wordValue the word value (0-65535)
     * @param subAddress the subaddress spec: *, H, L, +, H+, L+, or 1-16 for bit
     * @return the extracted value
     */
    public static int extractValue(int wordValue, String subAddress) {
        switch (subAddress) {
            case "*":
                return wordValue;
            case "H":
                return getHighByte(wordValue);
            case "L":
                return getLowByte(wordValue);
            case "+":
                return toSignedWord(wordValue);
            case "H+":
                return toSignedByte(getHighByte(wordValue));
            case "L+":
                return toSignedByte(getLowByte(wordValue));
            default:
                // Try to parse as bit number (1-16)
                try {
                    int bitPos = Integer.parseInt(subAddress);
                    return getBit(wordValue, bitPos) ? 1 : 0;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid subaddress: " + subAddress);
                }
        }
    }
}
