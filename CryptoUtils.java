import java.io.*;
import java.util.zip.*;

public class CryptoUtils {
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static byte[] compress(String input) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DeflaterOutputStream deflater = new DeflaterOutputStream(byteArrayOutputStream);
        deflater.write(input.getBytes());
        deflater.close();
        return byteArrayOutputStream.toByteArray();
    }

    public static String decompress(byte[] input) throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(input);
        InflaterInputStream inflater = new InflaterInputStream(byteArrayInputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int b;
        while ((b = inflater.read()) != -1) {
            byteArrayOutputStream.write(b);
        }
        return byteArrayOutputStream.toString();
    }
}
