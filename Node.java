import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Scanner;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class Node {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ServerSocket serverSocket = null;
        Socket socket = null;

        try {
            System.out.print("Are you Alice or Bob? (Alice/Bob): ");
            String role = scanner.nextLine();

            // Generate RSA Key Pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair rsaKeyPair = keyGen.generateKeyPair();

            if (role.equalsIgnoreCase("Alice")) {
                // Connect to Bob
                socket = new Socket("localhost", 12345);
                System.out.println("Connected to Bob.");

                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                PublicKey partnerPublicKey = (PublicKey) in.readObject();
                System.out.println("Received Bob's public key.");

                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(rsaKeyPair.getPublic());
                out.flush();
                System.out.println("Sent Alice's public key to Bob.");

                while (true) {
                    System.out.print("Enter message to send (or type 'exit' to quit): ");
                    String message = scanner.nextLine();

                    if (message.equalsIgnoreCase("exit")) {
                        System.out.println("Exiting...");
                        break;
                    }

                    // Step 1: Create SHA-512 Hash
                    MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
                    byte[] hash = sha512.digest(message.getBytes());
                    System.out.println("Step 1: SHA-512 Hash: " + bytesToHex(hash));

                    // Step 2: Digitally Sign the Hash
                    Signature signature = Signature.getInstance("SHA512withRSA");
                    signature.initSign(rsaKeyPair.getPrivate());
                    signature.update(hash);
                    byte[] signedHash = signature.sign();
                    System.out.println("Step 2: Digitally Signed Hash: " + bytesToHex(signedHash));

                    // Step 3: Compress Data (Simulated by combining message and signed hash)
                    String compressedData = message + "::" + bytesToHex(signedHash);
                    System.out.println("Step 3: Compressed Data: " + compressedData);

                    // Step 4: Encrypt with DES
                    KeyGenerator keyGenDES = KeyGenerator.getInstance("DES");
                    SecretKey desKey = keyGenDES.generateKey();
                    Cipher desCipher = Cipher.getInstance("DES");
                    desCipher.init(Cipher.ENCRYPT_MODE, desKey);
                    byte[] encryptedData = desCipher.doFinal(compressedData.getBytes());
                    System.out.println("Step 4: DES Encrypted Data: " + bytesToHex(encryptedData));

                    // Step 5: Encrypt DES Key with Bob's Public Key
                    Cipher rsaCipher = Cipher.getInstance("RSA");
                    rsaCipher.init(Cipher.ENCRYPT_MODE, partnerPublicKey);
                    byte[] encryptedDESKey = rsaCipher.doFinal(desKey.getEncoded());
                    System.out.println("Step 5: Encrypted DES Key: " + bytesToHex(encryptedDESKey));

                    // Send Data to Bob
                    out.writeObject(encryptedDESKey);
                    out.writeObject(encryptedData);
                    System.out.println("Data sent to Bob.");
                }

            } else {
                // Bob's Server
                serverSocket = new ServerSocket(12345);
                System.out.println("Waiting for Alice...");
                socket = serverSocket.accept();
                System.out.println("Alice connected.");

                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(rsaKeyPair.getPublic());
                out.flush();
                System.out.println("Sent Bob's public key to Alice.");

                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                PublicKey partnerPublicKey = (PublicKey) in.readObject();
                System.out.println("Received Alice's public key.");

                while (true) {
                    try {
                        // Receive Data
                        byte[] encryptedDESKey = (byte[]) in.readObject();
                        byte[] encryptedData = (byte[]) in.readObject();
                        System.out.println("Data received from Alice.");

                        // Step 6: Decrypt DES Key
                        Cipher rsaCipher = Cipher.getInstance("RSA");
                        rsaCipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());
                        byte[] desKeyBytes = rsaCipher.doFinal(encryptedDESKey);
                        SecretKey desKey = new SecretKeySpec(desKeyBytes, "DES");
                        System.out.println("Step 6: Decrypted DES Key: " + bytesToHex(desKeyBytes));

                        // Step 7: Decrypt Data with DES
                        Cipher desCipher = Cipher.getInstance("DES");
                        desCipher.init(Cipher.DECRYPT_MODE, desKey);
                        byte[] decryptedData = desCipher.doFinal(encryptedData);
                        String decompressedData = new String(decryptedData);
                        System.out.println("Step 7: Decrypted and Decompressed Data: " + decompressedData);

                        // Step 8: Verify Signature
                        String[] parts = decompressedData.split("::");
                        String originalMessage = parts[0];
                        byte[] signedHash = hexToBytes(parts[1]);

                        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
                        byte[] originalHash = sha512.digest(originalMessage.getBytes());

                        Signature signature = Signature.getInstance("SHA512withRSA");
                        signature.initVerify(partnerPublicKey);
                        signature.update(originalHash);
                        boolean isVerified = signature.verify(signedHash);

                        System.out.println("Step 8: Signature Verified: " + isVerified);
                        System.out.println("Original Message: " + originalMessage);
                    } catch (EOFException e) {
                        System.out.println("Connection closed by Alice.");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Close Scanner
            if (scanner != null) {
                scanner.close();
            }
            // Close ServerSocket
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Close Socket
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
