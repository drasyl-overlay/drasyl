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
package org.drasyl.crypto;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.math.ec.ECCurve;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * Util class that provides cryptography functions for drasyl.
 */
public class Crypto {
    public static final SecureRandom CSPRNG;
    private static final Logger LOG = LoggerFactory.getLogger(Crypto.class);
    private static final String PROVIDER = "BC";
    private static final String ECDSA = "ECDSA";
    private static final String SHA256_WITH_ECDSA = "SHA256withECDSA";
    private static final String CURVE_NAME = "curve25519";
    public static final int COMPRESSED_KEY_LENGTH = 33;

    static {
        Security.addProvider(new BouncyCastleProvider());

        // check for the optimal cryptographically secure pseudorandom number generator for the current platform
        SecureRandom prng;
        try {
            prng = SecureRandom.getInstance("Windows-PRNG");
        }
        catch (final Throwable e) { //NOSONAR
            // the windows PRNG is not available switch over to default provider
            // default for Unix-like systems is NativePRNG
            prng = new SecureRandom();
        }

        CSPRNG = prng;
    }

    Crypto() {
        // util class
    }

    /**
     * Generates an asymmetric curve key pair for signing.
     *
     * @return asymmetric key pair
     */
    public static KeyPair generateKeys() {
        try {
            final X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);
            final ECParameterSpec ecSpec = new ECParameterSpec(ecP.getCurve(), ecP.getG(), ecP.getN(), ecP.getH(), ecP.getSeed());

            final KeyPairGenerator keygen = KeyPairGenerator.getInstance(ECDSA, PROVIDER);
            keygen.initialize(ecSpec, CSPRNG);
            return keygen.generateKeyPair();
        }
        catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new InternalError("Cannot Generate Keys", e);
        }
    }

    /**
     * Generates an asymmetric curve public key from the given bytes.
     *
     * @param pubKey public key as byte array
     * @return asymmetric curve public key
     * @throws CryptoException if public key could not be generated
     */
    public static PublicKey getPublicKeyFromBytes(final byte[] pubKey) throws CryptoException {
        if (pubKey.length <= COMPRESSED_KEY_LENGTH) {
            return parseCompressedPublicKey(pubKey);
        }
        else {
            try {
                final KeyFactory kf = KeyFactory.getInstance(ECDSA, PROVIDER);
                return kf.generatePublic(getKeySpec(pubKey));
            }
            catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
                throw new CryptoException("Could not parse Key: " + HexUtil.toString(pubKey), e);
            }
        }
    }

    /**
     * Generates an asymmetric curve private key from the given bytes.
     *
     * @param privKey private key as byte array
     * @return asymmetric curve private key
     * @throws CryptoException if private key could not be generated
     */
    public static PrivateKey getPrivateKeyFromBytes(final byte[] privKey) throws CryptoException {
        if (privKey.length <= COMPRESSED_KEY_LENGTH) {
            return parseCompressedPrivateKey(privKey);
        }
        else {
            try {
                final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privKey);
                final KeyFactory factory = KeyFactory.getInstance(ECDSA, PROVIDER);
                return factory.generatePrivate(spec);
            }
            catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
                throw new CryptoException("Could not parse Key: " + HexUtil.toString(privKey), e);
            }
        }
    }

    /**
     * Generates an asymmetric curve public key from the given compressed public key.
     *
     * @param compressedPubKey compressed public key
     * @return asymmetric curve public key
     * @throws CryptoException if public key could not be generated
     */
    public static ECPublicKey parseCompressedPublicKey(final byte[] compressedPubKey) throws CryptoException {
        try {
            final X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);

            final KeyFactory fact = KeyFactory.getInstance(ECDSA, PROVIDER);
            final ECCurve curve = ecP.getCurve();
            final java.security.spec.EllipticCurve ellipticCurve = EC5Util.convertCurve(curve, ecP.getSeed());

            final java.security.spec.ECParameterSpec ecSpec = EC5Util.convertToSpec(ecP);
            final ECPoint point = ECPointUtil.decodePoint(ellipticCurve, compressedPubKey);
            final ECPublicKeySpec keySpec = new ECPublicKeySpec(point, ecSpec);
            return (ECPublicKey) fact.generatePublic(keySpec);
        }
        catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
            throw new CryptoException("Unable to parse compressed public key.", e);
        }
    }

    /**
     * Generates the curve key specs from the given public or private key byte array.
     *
     * @param pubOrPrivKey public or private key byte array
     * @return curve key specs
     */
    private static ECPublicKeySpec getKeySpec(final byte[] pubOrPrivKey) {
        final X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);
        final ECParameterSpec ecSpec = new ECParameterSpec(ecP.getCurve(), ecP.getG(), ecP.getN(), ecP.getH(), ecP.getSeed());
        final ECNamedCurveSpec params = new ECNamedCurveSpec(CURVE_NAME, ecSpec.getCurve(), ecSpec.getG(), ecSpec.getN(), ecSpec.getH(), ecSpec.getSeed());
        final java.security.spec.ECParameterSpec actualParams = new java.security.spec.ECParameterSpec(params.getCurve(), params.getGenerator(), params.getOrder(), params.getCofactor());
        final ECPoint point = ECPointUtil.decodePoint(actualParams.getCurve(), pubOrPrivKey);
        return new ECPublicKeySpec(point, actualParams);
    }

    /**
     * Generates an asymmetric curve private key from the given compressed private key.
     *
     * @param compressedPrivateKey compressed private key
     * @return asymmetric curve private key
     * @throws CryptoException if private key could not be generated
     */
    public static ECPrivateKey parseCompressedPrivateKey(final byte[] compressedPrivateKey) throws CryptoException {
        final X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);
        final ECParameterSpec ecSpec = new ECParameterSpec(ecP.getCurve(), ecP.getG(), ecP.getN(), ecP.getH(), ecP.getSeed());
        final ECPrivateKeySpec privkeyspec = new ECPrivateKeySpec(new BigInteger(compressedPrivateKey), ecSpec);
        try {
            return (ECPrivateKey) KeyFactory.getInstance(ECDSA, PROVIDER).generatePrivate(privkeyspec);
        }
        catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
            throw new CryptoException("Unable to parse compressed private key.", e);
        }
    }

    /**
     * Generates an asymmetric, compressed curve public key from the given public key.
     *
     * @param key the public key
     * @return compressed public key
     * @throws CryptoException if the public key was not in ECPublicKey format
     */
    public static byte[] compressedKey(final PublicKey key) throws CryptoException {
        if (key instanceof ECPublicKey) {
            return ((ECPublicKey) key).getQ().getEncoded(true);
        }
        throw new CryptoException(new IllegalArgumentException("Can only compress ECPublicKey"));
    }

    /**
     * Generates an asymmetric, compressed curve private key from the given private key.
     *
     * @param privkey the private key
     * @return compressed private key
     * @throws CryptoException if the public key was not in ECPrivateKey format
     */
    public static byte[] compressedKey(final PrivateKey privkey) throws CryptoException {
        if (privkey instanceof ECPrivateKey) {
            return ((ECPrivateKey) privkey).getD().toByteArray();
        }
        throw new CryptoException(new IllegalArgumentException("Can only compress ECPrivateKey"));
    }

    /**
     * Creates signature from the given message with the PrivateKey.
     *
     * @param key     Key to use
     * @param message message to sign
     * @throws CryptoException on failure
     */
    public static byte[] signMessage(final PrivateKey key,
                                     final byte[] message) throws CryptoException {
        try {
            final Signature ecdsaSign = Signature.getInstance(SHA256_WITH_ECDSA, PROVIDER);
            ecdsaSign.initSign(key);
            ecdsaSign.update(message);
            return ecdsaSign.sign();
        }
        catch (final NoSuchAlgorithmException | NoSuchProviderException | SignatureException | InvalidKeyException e) {
            throw new CryptoException("Unable to sign message.", e);
        }
    }

    /**
     * Verify the signature of the given message with the signature and public key.
     *
     * @param pubkey    the public key
     * @param message   the message to verify
     * @param signature the signature of the message
     * @return if the message is valid or not
     */
    public static boolean verifySignature(final PublicKey pubkey,
                                          final byte[] message,
                                          final byte[] signature) {
        try {
            final Signature ecdsaVerify = Signature.getInstance(SHA256_WITH_ECDSA, PROVIDER);
            ecdsaVerify.initVerify(pubkey);
            ecdsaVerify.update(message);
            return ecdsaVerify.verify(signature);
        }
        catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException e) {
            LOG.error("Unable to verify signature.", e);
        }
        return false;
    }

    /**
     * Generates a secure random HEX String with the given {@code entropy} of bytes.
     *
     * <p>
     * Recommendation:
     *     <ul>
     *         <li>4 byte for small sets</li>
     *         <li>8 bytes for unique internal strings, e.g. hash tables</li>
     *         <li>16 bytes for global uniqueness, e.g. auth token</li>
     *     </ul>
     * <p>
     * You can also use the following probability table for the "Birthday problem", as a starting point for a suitable
     * entropy size:
     * <a href="https://en.wikipedia.org/wiki/Birthday_problem#Probability_table">Birthday problem probability table</a>
     * </p>
     *
     * @param entropy entropy in bytes
     * @return a secure random HEX String
     */
    public static String randomString(final int entropy) {
        return HexUtil.bytesToHex(randomBytes(entropy));
    }

    /**
     * Generates a secure random bytes with the given {@code entropy}.
     *
     * <p>
     * Recommendation:
     *     <ul>
     *         <li>4 byte for small sets</li>
     *         <li>8 bytes for unique internal strings, e.g. hash tables</li>
     *         <li>16 bytes for global uniqueness, e.g. auth token</li>
     *     </ul>
     * <p>
     * You can also use the following probability table for the "Birthday problem", as a starting point for a suitable
     * entropy size:
     * <a href="https://en.wikipedia.org/wiki/Birthday_problem#Probability_table">Birthday problem probability table</a>
     * </p>
     *
     * @param entropy entropy in bytes
     * @return a secure random bytes
     */
    public static byte[] randomBytes(final int entropy) {
        final byte[] token = new byte[entropy];
        CSPRNG.nextBytes(token);

        return token;
    }

    /**
     * Generates a random number with the static {@link SecureRandom} of this class. Avoids overhead
     * of generating a new instance of {@link SecureRandom}.
     *
     * @param bound the upper bound (exclusive).  Must be positive.
     * @return the next pseudorandom, uniformly distributed {@code int} value between zero
     * (inclusive) and {@code bound} (exclusive) from this random number generator's sequence
     */
    public static int randomNumber(final int bound) {
        return CSPRNG.nextInt(bound);
    }
}
