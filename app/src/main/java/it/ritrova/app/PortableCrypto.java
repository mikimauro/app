package it.ritrova.app;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Formato portabile: 8 byte magic, 16 byte salt, 12 byte nonce, AES-256-GCM ciphertext+tag. */
public final class PortableCrypto {
    private static final byte[] MAGIC = "RITROVA1".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 210000;
    private PortableCrypto() { }

    private static SecretKeySpec key(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try { return new SecretKeySpec(bytes, "AES"); }
            finally { Arrays.fill(bytes, (byte) 0); }
        } finally { spec.clearPassword(); }
    }

    public static CipherOutputStream encrypted(OutputStream target, char[] password) throws IOException, GeneralSecurityException {
        byte[] salt = new byte[16], nonce = new byte[12];
        SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce);
        target.write(MAGIC); target.write(salt); target.write(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(128, nonce));
        return new CipherOutputStream(target, c);
    }

    public static CipherInputStream decrypted(InputStream source, char[] password) throws IOException, GeneralSecurityException {
        byte[] magic = bytes(source, MAGIC.length);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Non è un backup Ritrova valido");
        byte[] salt = bytes(source, 16), nonce = bytes(source, 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(password, salt), new GCMParameterSpec(128, nonce));
        return new CipherInputStream(source, c);
    }

    private static byte[] bytes(InputStream in, int length) throws IOException {
        byte[] b = new byte[length]; int offset = 0;
        while (offset < length) {
            int n = in.read(b, offset, length - offset);
            if (n == -1) throw new EOFException("Backup incompleto");
            offset += n;
        }
        return b;
    }
}
