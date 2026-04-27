package ai.univs.gate.support.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class CryptoUtil {

    @Value("${file.secret-key}")
    private String _secretKey;
    @Value("${file.algorithm.way}")
    private String _algorithm;
    @Value("${file.algorithm.mod}")
    private String _algorithmMod;

    public byte[] encrypt(byte[] file) throws Exception {
        return crypto(Cipher.ENCRYPT_MODE, file);
    }

    public byte[] decrypt(byte[] file) throws Exception {
        return crypto(Cipher.DECRYPT_MODE, file);
    }

    private byte[] crypto(int cipherMode, byte[] inputBytes) throws Exception {
        SecretKey secretKey = new SecretKeySpec(Base64.getDecoder().decode(_secretKey), _algorithm);
        Cipher cipher = Cipher.getInstance(_algorithmMod);
        cipher.init(cipherMode, secretKey);
        return cipher.doFinal(inputBytes);
    }
}
