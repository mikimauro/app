import it.ritrova.app.PortableCrypto;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Test standalone eseguibile con javac, senza Android SDK. */
public class CryptoSmokeTest {
    private static byte[] encrypt(byte[] input, char[] password) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try (OutputStream encrypted=PortableCrypto.encrypted(out, password)) { encrypted.write(input); }
        return out.toByteArray();
    }
    private static byte[] decrypt(byte[] input, char[] password) throws Exception {
        try (InputStream decrypted=PortableCrypto.decrypted(new ByteArrayInputStream(input), password)) {
            return decrypted.readAllBytes();
        }
    }
    public static void main(String[] args) throws Exception {
        byte[] original="Ritrova: messaggi, foto e video. 🧡".getBytes(StandardCharsets.UTF_8);
        byte[] enc=encrypt(original,"Password-di-prova-2026".toCharArray());
        if (!Arrays.equals(original,decrypt(enc,"Password-di-prova-2026".toCharArray()))) throw new AssertionError("Roundtrip non riuscito");
        boolean wrongRejected=false;
        try { decrypt(enc,"Password-errata-2026".toCharArray()); }
        catch (Exception expected) { wrongRejected=true; }
        if (!wrongRejected) throw new AssertionError("Password errata non rifiutata");
        byte[] changed=enc.clone(); changed[changed.length-8]^=0x01;
        boolean tamperRejected=false;
        try { decrypt(changed,"Password-di-prova-2026".toCharArray()); }
        catch (Exception expected) { tamperRejected=true; }
        if (!tamperRejected) throw new AssertionError("Modifica ai dati non rifiutata");
        System.out.println("OK: roundtrip, password errata e manomissione AES-GCM rifiutate. File " + enc.length + " byte.");
    }
}
