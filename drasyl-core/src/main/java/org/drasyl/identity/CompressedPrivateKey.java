/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.identity;

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;

import java.security.PrivateKey;

/**
 * This interface models a compressed key that can be converted into a string and vice versa.
 * <p>
 * This is an immutable object.
 */
public class CompressedPrivateKey extends AbstractCompressedKey<PrivateKey> {
    /**
     * Creates a new compressed private key from the given string.
     *
     * @param compressedKey compressed private key
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     * @throws CryptoException          if the string parameter does not conform to a valid key
     */
    public CompressedPrivateKey(String compressedKey) throws CryptoException {
        super(compressedKey);
    }

    /**
     * Creates a new compressed private key from the given private key.
     *
     * @param key compressed private key
     * @throws IllegalArgumentException if parameter does not conform to a valid hexadecimal string
     * @throws CryptoException          if the parameter does not conform to a valid key
     */
    public CompressedPrivateKey(PrivateKey key) throws CryptoException {
        this(HexUtil.bytesToHex(Crypto.compressedKey(key)), key);
    }

    /**
     * Creates a new compressed private key from the given string and private key.
     *
     * @param compressedKey compressed private key
     * @param key           private key
     */
    CompressedPrivateKey(String compressedKey, PrivateKey key) {
        super(compressedKey, key);
    }

    /**
     * Returns the {@link PrivateKey} object of this compressed private key.
     *
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     * @throws CryptoException          if the string parameter does not conform to a valid key
     */
    @Override
    public PrivateKey toUncompressedKey() throws CryptoException {
        if (key == null) {
            key = Crypto.getPrivateKeyFromBytes(HexUtil.fromString(compressedKey));
        }
        return this.key;
    }

    /**
     * Converts a {@link String} into a {@link CompressedPrivateKey}.
     *
     * @param compressedKey compressed key as String
     * @return {@link CompressedPublicKey}
     * @throws CryptoException          if string parameter does not conform to a valid key
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     */
    public static CompressedPrivateKey of(String compressedKey) throws CryptoException {
        return new CompressedPrivateKey(compressedKey);
    }

    /**
     * Converts a {@link PrivateKey} into a {@link CompressedPrivateKey}.
     *
     * @param key private key
     * @return {@link CompressedPublicKey}
     * @throws CryptoException          if string parameter does not conform to a valid key
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     */
    public static CompressedPrivateKey of(PrivateKey key) throws CryptoException {
        return new CompressedPrivateKey(key);
    }
}