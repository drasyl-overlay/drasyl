/*
 * Copyright (c) 2020-2021.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
import org.drasyl.util.InternPool;

import java.security.PublicKey;

/**
 * This interface models a compressed key that can be converted into a string and vice versa.
 * <p>
 * This is an immutable object.
 */
public class CompressedPublicKey extends AbstractCompressedKey<PublicKey> {
    @SuppressWarnings("unused")
    public static final short PUBLIC_KEY_LENGTH = 66;
    public static final InternPool<CompressedPublicKey> POOL = new InternPool<>();

    /**
     * Creates a new compressed public key from the given string.
     *
     * @param compressedKey compressed public key
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} does not conform to a valid string
     * @deprecated Use {@link #of(String)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.4.0", forRemoval = true)
    public CompressedPublicKey(final String compressedKey) {
        super(compressedKey);
    }

    /**
     * Creates a new compressed public key from the given public key.
     *
     * @param key compressed public key
     * @throws IllegalArgumentException if parameter does not conform to a valid hexadecimal string
     * @throws CryptoException          if the parameter does not conform to a valid key
     * @deprecated this will be removed in the next release.
     */
    @Deprecated(since = "0.4.0", forRemoval = true)
    public CompressedPublicKey(final PublicKey key) throws CryptoException {
        this(HexUtil.bytesToHex(Crypto.compressedKey(key)));
    }

    /**
     * Creates a new compressed public key from the given byte array.
     *
     * @param compressedKey compressed public key
     */
    private CompressedPublicKey(final byte[] compressedKey) {
        super(compressedKey);
    }

    /**
     * Converts a {@link String} into a {@link CompressedPublicKey}.
     *
     * @param compressedKey compressed key as String
     * @return {@link CompressedPublicKey}
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} does not conform to a valid key
     *                                  string
     */
    public static CompressedPublicKey of(final String compressedKey) {
        return new CompressedPublicKey(compressedKey).intern();
    }

    /**
     * Converts a byte[] into a {@link CompressedPublicKey}.
     *
     * @param compressedKey public key
     * @return {@link CompressedPublicKey}
     * @throws NullPointerException if {@code compressedKey} is {@code null}
     */
    @JsonCreator
    public static CompressedPublicKey of(final byte[] compressedKey) {
        return new CompressedPublicKey(compressedKey).intern();
    }

    /**
     * Returns the {@link PublicKey} object of this compressed public key.
     *
     * @throws IllegalStateException if uncompressed public key could not be generated
     */
    @Override
    public PublicKey toUncompressedKey() {
        if (key == null) {
            try {
                key = Crypto.getPublicKeyFromBytes(compressedKey);
            }
            catch (final CryptoException e) {
                throw new IllegalStateException("Uncompressed public key could not be generated", e);
            }
        }
        return this.key;
    }

    /**
     * See {@link InternPool#intern(Object)}
     */
    public CompressedPublicKey intern() {
        return POOL.intern(this);
    }
}
