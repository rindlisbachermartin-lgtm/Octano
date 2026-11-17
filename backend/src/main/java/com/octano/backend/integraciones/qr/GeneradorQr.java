package com.octano.backend.integraciones.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class GeneradorQr {
    public byte[] png(String url, int tamano) {
        try {
            var matriz = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, tamano, tamano,
                    Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            var imagen = new BufferedImage(matriz.getWidth(), matriz.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < matriz.getHeight(); y++) {
                for (int x = 0; x < matriz.getWidth(); x++) imagen.setRGB(x, y, matriz.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
            var salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar la imagen QR.", e);
        }
    }
}
