package client;

import api.OpType;
import api.Reply;
import bftsmart.reconfiguration.util.RSAKeyLoader;
import bftsmart.tom.util.KeyLoader;
import security.Digest;
import security.PrivateKey;
import security.PublicKey;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

public class AppClient {

    //private static  URI baseURI;
    private static SecureRandom random = new SecureRandom();

    private static List<KeyPair> keys = new ArrayList<KeyPair>();

    public static void main(String[] args) throws Exception {

        addMoney();
        /*addMoneyWNoPermission();

        addMoney();
        transferMoney();
        getMoney();*/


    }


    public AppClient() {

    }


    @SuppressWarnings("Duplicates")
    public static void transferMoney() throws Exception {
        Client client = ClientBuilder.newBuilder()
                .hostnameVerifier(new InsecureHostnameVerifier())
                .build();

        URI baseURI = UriBuilder.fromUri("https://localhost:8080/users/").build();
        WebTarget target = client.target(baseURI);
        System.out.println("URI: " + baseURI);


        // TODO generate random public/private key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);


        KeyPair kp = keys.get(random.nextInt(keys.size()));

        PublicKey pub = new PublicKey("RSA", kp.getPublic());
        PrivateKey priv = new PrivateKey("RSA", kp.getPrivate());
        String publicString = Base64.getEncoder().encodeToString(pub.exportKey());
        String pathPublicKey = URLEncoder.encode(publicString, "UTF-8");

        KeyPair kp2 = keys.get(random.nextInt(keys.size()));
        PublicKey pub2 = new PublicKey("RSA", kp.getPublic());
        PrivateKey priv2 = new PrivateKey("RSA", kp.getPrivate());
        String publicString2 = Base64.getEncoder().encodeToString(pub2.exportKey());
        String pathPublicKey2 = URLEncoder.encode(publicString2, "UTF-8");


        Double value = 1.0;
        Long nonce = random.nextLong();

        String msg = publicString + publicString2 + value + nonce;
        byte[] hash = Digest.getDigest(msg.getBytes());

        byte[] hashEncriptPriv = priv.encrypt(hash);
        String msgHashStr = Base64.getEncoder().encodeToString(hashEncriptPriv);


        Response response = target.path("transfer/" + pathPublicKey).queryParam("tpublicKey", pathPublicKey2)
                .queryParam("value", value)
                .queryParam("nonce", nonce)
                .queryParam("msg", msgHashStr)
                .request()
                .post(Entity.entity(Reply.class, MediaType.APPLICATION_JSON));

        Reply r = response.readEntity(Reply.class);

        for (int i = 0; i < r.getMessages().size(); i++) {

            KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
            java.security.PublicKey pk = keyLoader.loadPublicKey(r.getMessages().get(i).getSender());
            Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
            sig.initVerify(pk);
            sig.update(r.getMessages().get(i).getSerializedMessage());
            if (sig.verify(r.getMessages().get(i).getSignature())) {
                System.out.println("Replica message is authentic");
            } else {
                System.out.println("Signature of message is invalid");
            }

        }

        // Check if response nonce(which is nonce+1) is equals to original nonce + 1
        if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.TRANSFER) {
            System.out.println("Nonces dont match, reject message from server");
        } else {


            System.out.println("#################################");
            System.out.println("####### T R A N S F E R #########");
            System.out.println("#################################");
            System.out.println();
            System.out.println("Status: " + response.getStatusInfo());
            System.out.println("From pubKey: " + publicString);
            System.out.println("To pubKey: " + r.getPublicKey());
            System.out.println("New amount: " + r.getAmount());
            System.out.println("Client nonce: " + nonce);
            System.out.println("Nonce from response: " + r.getNonce());
            System.out.println();

        }
    }

    @SuppressWarnings("Duplicates")
    public static void addMoneyWNoPermission() throws Exception {


        Client client = ClientBuilder.newBuilder()
                .hostnameVerifier(new InsecureHostnameVerifier())
                .build();

        URI baseURI = UriBuilder.fromUri("https://localhost:8080/users/").build();
        WebTarget target = client.target(baseURI);
        System.out.println("URI: " + baseURI);

        // TODO generate random public/private key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.generateKeyPair();

        //keys.add(kp);
        PublicKey pub = new PublicKey("RSA", kp.getPublic());
        PrivateKey priv = new PrivateKey("RSA", kp.getPrivate());


        Double value = 50.5;

        String publicString = Base64.getEncoder().encodeToString(pub.exportKey());
        Long nonce = random.nextLong();

        String msg = publicString + value + nonce;
        byte[] hash = Digest.getDigest(msg.getBytes());

        byte[] hashEncriptPriv = priv.encrypt(hash);
        String msgHashStr = Base64.getEncoder().encodeToString(hashEncriptPriv);
        String pathPublicKey = URLEncoder.encode(publicString, "UTF-8");

        Response response = target.path(pathPublicKey)
                .queryParam("value", value)
                .queryParam("nonce", nonce)
                .queryParam("msg", msgHashStr)
                .request()
                .post(Entity.entity(Reply.class, MediaType.APPLICATION_JSON));

        if (response.getStatus() == 401) {
            System.out.println("Sorry, you dont have permissions to add Money");
            System.out.println();
        } else {
            Reply r = response.readEntity(Reply.class);

            for (int i = 0; i < r.getMessages().size(); i++) {

                KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
                java.security.PublicKey pk = keyLoader.loadPublicKey(r.getMessages().get(i).getSender());
                Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
                sig.initVerify(pk);
                sig.update(r.getMessages().get(i).getSerializedMessage());
                if (sig.verify(r.getMessages().get(i).getSignature())) {
                    System.out.println("Replica message is authentic");
                } else {
                    System.out.println("Signature of message is invalid");
                }

            }

            // Check if response nonce(which is nonce+1) is equals to original nonce + 1
            if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.ADD_MONEY) {
                System.out.println("Nonces dont match, reject message from server");
            } else {

                System.out.println("#################################");
                System.out.println("####### A D D - M O N E Y #######");
                System.out.println("#################################");
                System.out.println();
                System.out.println("Status: " + response.getStatusInfo());
                System.out.println("From pubKey: " + publicString);
                System.out.println("To pubKey(????): " + r.getPublicKey());
                System.out.println("New amount: " + r.getAmount());
                System.out.println("Client nonce: " + nonce);
                System.out.println("Nonce from response: " + r.getNonce());
                System.out.println();


            }
        }
    }

    @SuppressWarnings("Duplicates")
    public static void addMoney() throws Exception {


        Client client = ClientBuilder.newBuilder()
                .hostnameVerifier(new InsecureHostnameVerifier())
                .build();

        URI baseURI = UriBuilder.fromUri("https://localhost:8080/users/").build();
        WebTarget target = client.target(baseURI);
        System.out.println("URI: " + baseURI);


        Double value = 50.5;


        File file = new File("./publicKey.txt");
        File file2 = new File("./privateKey.txt");
        String adminPublicString = null;
        String adminPrivateString = null;

        Scanner sc = new Scanner(file);
        Scanner sc2 = new Scanner(file2);

        while (sc.hasNextLine() && sc2.hasNextLine()) {
            adminPublicString = sc.next();
            adminPrivateString = sc2.next();
        }


        System.out.println("publicKey : " + adminPublicString);
        System.out.println("privateKey : " + adminPrivateString);

        byte[] pubByte = Base64.getDecoder().decode(adminPublicString);
        PublicKey adminPub = PublicKey.createKey(pubByte);
        byte[] privByte = Base64.getDecoder().decode(adminPrivateString);
        PrivateKey adminPriv = PrivateKey.createKey(privByte);


        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.generateKeyPair();

        keys.add(kp);
        PublicKey pub = new PublicKey("RSA", kp.getPublic());
        PrivateKey priv = new PrivateKey("RSA", kp.getPrivate());

        String publicString = Base64.getEncoder().encodeToString(pub.exportKey());


        Long nonce = random.nextLong();

        String msg = publicString + value + nonce;
        byte[] hash = Digest.getDigest(msg.getBytes());

        byte[] hashEncriptPriv = adminPriv.encrypt(hash);
        String msgHashStr = Base64.getEncoder().encodeToString(hashEncriptPriv);
        String pathPublicKey = URLEncoder.encode(publicString, "UTF-8");

        Response response = target.path(pathPublicKey)
                .queryParam("value", value)
                .queryParam("nonce", nonce)
                .queryParam("msg", msgHashStr)
                .request()
                .post(Entity.entity(Reply.class, MediaType.APPLICATION_JSON));

        System.out.println(response);
        Reply r = response.readEntity(Reply.class);

        // TODO
        for (int i = 0; i < r.getMessages().size(); i++) {

            KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
            java.security.PublicKey pk = keyLoader.loadPublicKey(r.getMessages().get(i).getSender());
            Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
            sig.initVerify(pk);
            sig.update(r.getMessages().get(i).getSerializedMessage());
            if (sig.verify(r.getMessages().get(i).getSignature())) {
                System.out.println("Replica message is authentic");
            } else {
                System.out.println("Signature of message is invalid");
            }

        }


        // Check if response nonce(which is nonce+1) is equals to original nonce + 1
        if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.ADD_MONEY) {
            System.out.println("Nonces dont match, reject message from server");
        } else {

            System.out.println("#################################");
            System.out.println("####### A D D - M O N E Y #######");
            System.out.println("#################################");
            System.out.println();
            System.out.println("Status: " + response.getStatusInfo());
            System.out.println("From pubKey: " + publicString);
            System.out.println("New amount: " + r.getAmount());
            System.out.println("Client nonce: " + nonce);
            System.out.println("Nonce from response: " + r.getNonce());
            System.out.println();


        }
    }

    @SuppressWarnings("Duplicates")
    public static void getMoney() throws Exception {


        Client client = ClientBuilder.newBuilder()
                .hostnameVerifier(new InsecureHostnameVerifier())
                .build();

        URI baseURI = UriBuilder.fromUri("https://localhost:8080/users/").build();
        WebTarget target = client.target(baseURI);
        System.out.println("URI: " + baseURI);

        KeyPair kp = keys.get(random.nextInt(keys.size()));
        PublicKey pub = new PublicKey("RSA", kp.getPublic());
        PrivateKey priv = new PrivateKey("RSA", kp.getPrivate());
        String publicString = Base64.getEncoder().encodeToString(pub.exportKey());
        String pathPublicKey = URLEncoder.encode(publicString, "UTF-8");


        Long nonce = random.nextLong();

        String msg = publicString + nonce;
        byte[] hash = Digest.getDigest(msg.getBytes());

        byte[] hashEncriptPriv = priv.encrypt(hash);
        String msgHashStr = Base64.getEncoder().encodeToString(hashEncriptPriv);

        Response response = target.path(pathPublicKey + "/money")
                .queryParam("nonce", nonce)
                .queryParam("msg", msgHashStr)
                .request()
                .get();
        Reply r = response.readEntity(Reply.class);

        for (int i = 0; i < r.getMessages().size(); i++) {

            KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
            java.security.PublicKey pk = keyLoader.loadPublicKey(r.getMessages().get(i).getSender());
            Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
            sig.initVerify(pk);
            sig.update(r.getMessages().get(i).getSerializedMessage());
            if (sig.verify(r.getMessages().get(i).getSignature())) {
                System.out.println("Replica message is authentic");
            } else {
                System.out.println("Signature of message is invalid");
            }

        }

        // Check if response nonce(which is nonce+1) is equals to original nonce + 1
        if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.GET_MONEY) {
            System.out.println("Nonces dont match, reject message from server");
        } else {

            System.out.println("#################################");
            System.out.println("####### G E T - M O N E Y #######");
            System.out.println("#################################");
            System.out.println();
            System.out.println("Status: " + response.getStatusInfo());
            System.out.println("From pubKey: " + publicString);
            System.out.println("To pubKey(????): " + r.getPublicKey());
            System.out.println("New amount: " + r.getAmount());
            System.out.println("Client nonce: " + nonce);
            System.out.println("Nonce from response: " + r.getNonce());
            System.out.println();

        }
    }


    static public class InsecureHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }


}




