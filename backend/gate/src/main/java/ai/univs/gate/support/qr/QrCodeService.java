package ai.univs.gate.support.qr;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class QrCodeService {

    @Value("${gate.demo.url}")
    private String GATE_DEMO_URL;

    public byte[] generateQrCode(String demoType, String code) {

        try {
            String url = buildUrl(demoType, code);

            int width = 300;
            int height = 300;

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(
                    url,
                    BarcodeFormat.QR_CODE,
                    width,
                    height
            );

            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bufferedImage.setRGB(
                            x,
                            y,
                            bitMatrix.get(x, y) ? 0x000000 : 0xFFFFFF
                    );
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "PNG", outputStream);

            return outputStream.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("Fail to create QR: {}", e.getMessage(), e);
            throw new CustomGateException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }

    public String buildUrl(String domain, String code) {
        return String.format("%s?token=%s", GATE_DEMO_URL + "/" + domain, code);
    }
}
