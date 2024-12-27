import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Scanner;
import java.util.concurrent.*;

public class Node {
    private final KeyPair rsaKeyPair;
    private PublicKey partnerPublicKey;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    public Node(KeyPair rsaKeyPair) {
        this.rsaKeyPair = rsaKeyPair;
    }

    public void setConnection(Socket socket) throws IOException, ClassNotFoundException {
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());

        // Exchange public keys
        out.writeObject(rsaKeyPair.getPublic());
        out.flush();
        partnerPublicKey = (PublicKey) in.readObject();
    }

    public void sendMessage(String message) throws Exception {
        System.out.println("\n=== Encryption Process ===");

        // Step 1: Compute SHA-512 hash
        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        byte[] hash = sha512.digest(message.getBytes());
        System.out.println("Step 1: SHA-512 Hash: " + CryptoUtils.bytesToHex(hash));

        // Step 2: Sign the hash using RSA private key
        Cipher rsaCipher = Cipher.getInstance("RSA");
        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaKeyPair.getPrivate());
        byte[] signedHash = rsaCipher.doFinal(hash);
        System.out.println("Step 2: Digitally Signed Hash: " + CryptoUtils.bytesToHex(signedHash));

        // Step 3: Append message and signed hash
        String appended = message + "||" + CryptoUtils.bytesToHex(signedHash);
        System.out.println("Step 3: Appended Message and Signed Hash: " + appended);

        // Step 4: Compress the appended data
        byte[] compressed = CryptoUtils.compress(appended);
        System.out.println("Step 4: Compressed Data: " + CryptoUtils.bytesToHex(compressed));

        // Step 5: Encrypt compressed data with DES
        KeyGenerator keyGen = KeyGenerator.getInstance("DES");
        SecretKey desKey = keyGen.generateKey();
        Cipher desCipher = Cipher.getInstance("DES");
        desCipher.init(Cipher.ENCRYPT_MODE, desKey);
        byte[] encryptedData = desCipher.doFinal(compressed);
        System.out.println("Step 5: Encrypted Data with DES: " + CryptoUtils.bytesToHex(encryptedData));

        // Step 6: Encrypt DES key with recipient's RSA public key
        rsaCipher.init(Cipher.ENCRYPT_MODE, partnerPublicKey);
        byte[] encryptedDESKey = rsaCipher.doFinal(desKey.getEncoded());
        System.out.println("Step 6: Encrypted DES Key with RSA: " + CryptoUtils.bytesToHex(encryptedDESKey));

        // Step 7: Send encrypted data and encrypted DES key
        out.writeObject(new Object[]{encryptedData, encryptedDESKey});
        out.flush();
        System.out.println("Step 7: Sent Encrypted Data and Key\n");
    }

    public void receiveMessage() throws Exception {
        System.out.println("\n=== Decryption Process ===");

        // Receive data
        Object[] received = (Object[]) in.readObject();
        byte[] encryptedData = (byte[]) received[0];
        byte[] encryptedDESKey = (byte[]) received[1];

        System.out.println("Received Encrypted Data: " + CryptoUtils.bytesToHex(encryptedData));
        System.out.println("Received Encrypted DES Key: " + CryptoUtils.bytesToHex(encryptedDESKey));

        // Step 1: Decrypt DES key using RSA private key
        Cipher rsaCipher = Cipher.getInstance("RSA");
        rsaCipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());
        byte[] desKeyBytes = rsaCipher.doFinal(encryptedDESKey);
        SecretKey desKey = new SecretKeySpec(desKeyBytes, "DES");
        System.out.println("Step 1: Decrypted DES Key: " + CryptoUtils.bytesToHex(desKeyBytes));

        // Step 2: Decrypt data using DES key
        Cipher desCipher = Cipher.getInstance("DES");
        desCipher.init(Cipher.DECRYPT_MODE, desKey);
        byte[] decompressedData = desCipher.doFinal(encryptedData);
        System.out.println("Step 2: Decrypted Data: " + CryptoUtils.bytesToHex(decompressedData));

        // Step 3: Decompress data
        String decompressed = CryptoUtils.decompress(decompressedData);
        System.out.println("Step 3: Decompressed Data: " + decompressed);

        // Step 4: Separate message and signed hash
        String[] parts = decompressed.split("\\|\\|");
        String message = parts[0];
        byte[] signedHash = CryptoUtils.hexToBytes(parts[1]);
        System.out.println("Step 4: Extracted Message: " + message);
        System.out.println("Step 4: Extracted Signed Hash: " + CryptoUtils.bytesToHex(signedHash));

        // Step 5: Verify signed hash
        rsaCipher.init(Cipher.DECRYPT_MODE, partnerPublicKey);
        byte[] hash = rsaCipher.doFinal(signedHash);
        System.out.println("Step 5: Decrypted Hash from Signature: " + CryptoUtils.bytesToHex(hash));

        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        byte[] computedHash = sha512.digest(message.getBytes());
        System.out.println("Step 5: Computed Hash of Message: " + CryptoUtils.bytesToHex(computedHash));

        boolean isValid = MessageDigest.isEqual(hash, computedHash);
        System.out.println("Step 5: Verification Result: " + (isValid ? "Success" : "Failure"));

        System.out.println("\nFinal Message: " + message + "\n");
    }

    public static void main(String[] args) throws Exception {
        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);

        // Generate RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair rsaKeyPair = keyGen.generateKeyPair();

        Node node = new Node(rsaKeyPair);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Socket socket = null;
        try {
            @SuppressWarnings("resource")
            ServerSocket serverSocket = new ServerSocket(12345);
            System.out.println("Waiting for peer to connect...");
            socket = serverSocket.accept();
            System.out.println("Peer connected.");
        } catch (IOException e) {
            System.out.println("Could not start server, attempting to connect as client...");
            socket = new Socket("localhost", 12345);
            System.out.println("Connected to peer.");
        }

        node.setConnection(socket);

        executor.submit(() -> {
            try {
                while (true) {
                    node.receiveMessage();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        while (true) {
            System.out.print("Enter message to send (or type 'exit' to quit): ");
            String message = scanner.nextLine();
            if (message.equalsIgnoreCase("exit")) {
                socket.close();
                executor.shutdownNow();
                break;
            }
            node.sendMessage(message);
        }
    }
}
