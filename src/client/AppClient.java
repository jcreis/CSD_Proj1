package client;

import bftsmart.reconfiguration.util.RSAKeyLoader;
import bftsmart.tom.util.KeyLoader;
import hj.mlib.HomoAdd;
import hj.mlib.HomoOpeInt;
import hj.mlib.PaillierKey;
import model.OpType;
import model.ReplicaResponseMessage;
import model.Reply;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.security.*;
import java.util.*;

public class AppClient {

    //private static  URI baseURI;
    private static SecureRandom random = new SecureRandom();

    private static List<KeyPair> keys = new ArrayList<KeyPair>();

    private static List<Long> transferRequestTimes = new ArrayList<Long>();

    private static List<Long> addMoneyRequestTimes = new ArrayList<Long>();

    private static List<Long> getMoneyRequestTimes = new ArrayList<Long>();

    public static void main(String[] args) throws Exception {

        long initAddMoneyTime = System.currentTimeMillis();
        while(true){
            addMoney("WALLET");
            if(System.currentTimeMillis()-initAddMoneyTime >= 300*60){
                break;
            }
        }

        @SuppressWarnings("Duplicates")
        Thread transferThread1 = new Thread()
        {
            @Override
            public void run()
            {
                long initTransferTime = System.currentTimeMillis();
                while (true){
                    try {
                        transferMoney();
                        getMoney("WALLET");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // run during 3 min
                    if(System.currentTimeMillis()-initTransferTime >= 3000*60)
                        break;
                }


            }
        };

        @SuppressWarnings("Duplicates")
        Thread transferThread2 = new Thread()
        {
            @Override
            public void run()
            {
                long initTransferTime = System.currentTimeMillis();
                while (true){
                    try {
                        getMoney("WALLET");
                        transferMoney();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if(System.currentTimeMillis()-initTransferTime >= 3000*60)
                        break;
                }
            }
        };


        transferThread1.start();
        transferThread2.start();
        transferThread2.join();

        System.out.println("#####################################");
        System.out.println("###### AVERAGE REQUEST TIMES ########");
        System.out.println("#####################################");
        System.out.println("Average time of transfer requests: "+ getTransferAvgTime()+"ms");
        System.out.println("Average time of getMoney requests: "+ getGetMoneyAvgTime()+"ms");
        System.out.println("Average time of addMoney requests: "+ getAddMoneyAvgTime()+"ms");

    }


    public AppClient() {

    }


    @SuppressWarnings("Duplicates")
    public static void transferMoney() throws Exception {
        System.out.println("#################################");
        System.out.println("####### T R A N S F E R #########");
        System.out.println("#################################");
        System.out.println();

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
        if (kp2.getPublic().equals(kp.getPublic())) {
            kp2 = keys.get(random.nextInt(keys.size()));
        }
        //kp = keys.get(random.nextInt(keys.size()));
        PublicKey pub2 = new PublicKey("RSA", kp2.getPublic());
        PrivateKey priv2 = new PrivateKey("RSA", kp2.getPrivate());
        String publicString2 = Base64.getEncoder().encodeToString(pub2.exportKey());
        String pathPublicKey2 = URLEncoder.encode(publicString2, "UTF-8");


        Random randomm = new Random();
        Double value = randomm.nextInt(89) + 10.0;

        Long nonce = random.nextLong();

        String msg = publicString + publicString2 + value + nonce;
        byte[] hash = Digest.getDigest(msg.getBytes());

        byte[] hashEncriptPriv = priv.encrypt(hash);
        String msgHashStr = Base64.getEncoder().encodeToString(hashEncriptPriv);

        // Calculate time for request
        long initRequestTime = System.currentTimeMillis();

        Response response = target.path("transfer/" + pathPublicKey).queryParam("tpublicKey", pathPublicKey2)
                .queryParam("value", value)
                .queryParam("nonce", nonce)
                .queryParam("msg", msgHashStr)
                .request()
                .post(Entity.entity(Reply.class, MediaType.APPLICATION_JSON));

        long finalRequestTime = System.currentTimeMillis() - initRequestTime ;
        transferRequestTimes.add(finalRequestTime);

        Reply r = response.readEntity(Reply.class);

        ArrayList<Double> amounts = new ArrayList<Double>();
        ArrayList<Long> lNonces = new ArrayList<Long>();

        for (ReplicaResponseMessage currentReplicaMsg: r.getMessages()) {
            if (currentReplicaMsg != null){
                ByteArrayInputStream byteIn = new ByteArrayInputStream(currentReplicaMsg.getContent());
                ObjectInput objIn = new ObjectInputStream(byteIn);
                Double replicaMsgAmount = (Double) objIn.readObject();
                //System.out.println("replica amount: "+ replicaMsgAmount);
                Long replicaNonce = (Long) objIn.readObject();
                //System.out.println("replica nonce: " + replicaNonce);


                amounts.add(replicaMsgAmount);
                lNonces.add(replicaNonce);

                KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
                java.security.PublicKey pk = keyLoader.loadPublicKey(currentReplicaMsg.getSender());
                Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
                sig.initVerify(pk);
                sig.update(currentReplicaMsg.getSerializedMessage());


                if (sig.verify(currentReplicaMsg.getSignature())) {
                    System.out.println("Replica message coming from replica " + currentReplicaMsg.getSender() + " is authentic");
                } else {
                    System.out.println("Signature of message is invalid");
                }
            }
        }

        int majority = 0;
        int numbNonces = 0;
        for (Double amount : amounts) {
            if (amount == r.getAmount())
                majority++;
        }
        for (Long n : lNonces) {
            if (n + 1 == r.getNonce())
                numbNonces++;
        }

        // Verify majority of nonces of replicas
        if (numbNonces >= (lNonces.size() / 2) + 1) {
            System.out.println("majority of replicas returns the right nonce");
        } else {
            System.out.println("No majority reached for nonce");

        }

        // Verify majority from message replies of replicas
        if ((majority >= (amounts.size() / 2) + 1)) {
            System.out.println("majority of replicas returns the right value");
        } else {
            System.out.println("No majority reached");
        }

        // Check if response nonce(which is nonce+1) is equals to original nonce + 1
        if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.TRANSFER) {
            System.out.println("Nonces dont match, reject message from server");
        } else {

            System.out.println();
            System.out.println("Status: " + response.getStatusInfo());
            System.out.println("From pubKey: " + publicString.substring(0, 50));
            System.out.println("To pubKey: " + publicString2.substring(0, 50));
            System.out.println("Transferring amount : " + value);
            System.out.println(publicString2.substring(0, 50) + " now has " + r.getAmount());
            if (nonce + 1 == r.getNonce()) {
                System.out.println("Nonces match");
            }
            System.out.println();

        }
    }

    @SuppressWarnings("Duplicates")
    public static void addMoneyWNoPermission() throws Exception {

        System.out.println("#############################################");
        System.out.println("####### A D D - M O N E Y (NOT ADMIN) #######");
        System.out.println("#############################################");
        System.out.println();

        Client client = ClientBuilder.newBuilder()
                .hostnameVerifier(new InsecureHostnameVerifier())
                .build();

        URI baseURI = UriBuilder.fromUri("https://localhost:8080/users/").build();
        WebTarget target = client.target(baseURI);
        System.out.println("URI: " + baseURI);

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

            ArrayList<Double> amounts = new ArrayList<Double>();
            ArrayList<Long> lNonces = new ArrayList<Long>();

            for (int i = 0; i < r.getMessages().size(); i++) {
                ReplicaResponseMessage currentReplicaMsg = r.getMessages().get(i);

                ByteArrayInputStream byteIn = new ByteArrayInputStream(currentReplicaMsg.getContent());
                ObjectInput objIn = new ObjectInputStream(byteIn);
                Double replicaMsgAmount = (Double) objIn.readObject();
                //System.out.println("replica amount: "+ replicaMsgAmount);
                Long replicaNonce = (Long) objIn.readObject();
                //System.out.println("replica nonce: " + replicaNonce);


                amounts.add(replicaMsgAmount);
                lNonces.add(replicaNonce);


                KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
                java.security.PublicKey pk = keyLoader.loadPublicKey(currentReplicaMsg.getSender());
                Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
                sig.initVerify(pk);
                sig.update(currentReplicaMsg.getSerializedMessage());
                if (sig.verify(currentReplicaMsg.getSignature())) {
                    System.out.println("Replica message coming from replica " + currentReplicaMsg.getSender() + " is authentic");
                } else {
                    System.out.println("Signature of message is invalid");
                }

            }

            int majority = 0;
            int numbNonces = 0;
            for (Double amount : amounts) {
                if (amount == r.getAmount())
                    majority++;
            }
            for (Long n : lNonces) {
                if (n + 1 == r.getNonce())
                    numbNonces++;
            }

            // Verify majority of nonces of replicas
            if (numbNonces >= (lNonces.size() / 2) + 1) {
                System.out.println("majority of replicas returns the right nonce");
            } else {
                System.out.println("No majority reached for nonce");

            }

            // Verify majority from message replies of replicas
            if ((majority >= (amounts.size() / 2) + 1)) {
                System.out.println("majority of replicas returns the right value");
            } else {
                System.out.println("No majority reached");
            }

            // Check if response nonce(which is nonce+1) is equals to original nonce + 1
            if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.ADD_MONEY) {
                System.out.println("Nonces dont match, reject message from server");
            } else {


                System.out.println();
                System.out.println("Status: " + response.getStatusInfo());
                System.out.println("From pubKey: " + publicString.substring(0, 50));
                System.out.println("To pubKey: " + r.getPublicKey().substring(0, 50));
                System.out.println("New amount: " + r.getAmount());
                if (nonce + 1 == r.getNonce()) {
                    System.out.println("Nonces match");
                }
                System.out.println();


            }
        }
    }

    @SuppressWarnings("Duplicates")
    public static void addMoney(String type) throws Exception {
        System.out.println("#################################");
        System.out.println("####### A D D - M O N E Y #######");
        System.out.println("#################################");
        System.out.println();

        Client client = ClientBuilder.newBuilder()
                .hostnameVerifier(new InsecureHostnameVerifier())
                .build();

        URI baseURI = UriBuilder.fromUri("https://localhost:8080/users/").build();
        WebTarget target = client.target(baseURI);
        System.out.println("URI: " + baseURI);


        Random randomm = new Random();

        //Adicionar um valor random
        Double value = randomm.nextInt(899) + 100.0;


        File file = new File("./publicKey.txt");
        File file2 = new File("./privateKey.txt");
        File file3 = new File("./homoAddKey.txt");
        String adminPublicString = null;
        String adminPrivateString = null;

        Scanner sc = new Scanner(file);
        Scanner sc2 = new Scanner(file2);
        Scanner sc3 = new Scanner(file3);

        while (sc.hasNextLine() && sc2.hasNextLine()) {
            adminPublicString = sc.next();
            adminPrivateString = sc2.next();
        }


        /*System.out.println("AdminPublicKey : " + adminPublicString.substring(0,50));
        System.out.println("AdminPrivateKey : " + adminPrivateString.substring(0,50));*/

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

        Reply r;

        String msg = publicString + value + nonce;
        byte[] hash = Digest.getDigest(msg.getBytes());

        byte[] hashEncriptPriv = adminPriv.encrypt(hash);
        String msgHashStr = Base64.getEncoder().encodeToString(hashEncriptPriv);
        String pathPublicKey = URLEncoder.encode(publicString, "UTF-8");


        // Calculate time for request
        long initRequestTime = System.currentTimeMillis();

        Response response;


        switch (type) {

            case "WALLET":
                response = target.path(pathPublicKey)
                        .queryParam("value", value.toString())
                        .queryParam("nonce", nonce)
                        .queryParam("msg", msgHashStr)
                        .queryParam("type", type)
                        .request()
                        .post(Entity.entity(Reply.class, MediaType.APPLICATION_JSON));

                r = response.readEntity(Reply.class);
                break;

            case "HOMO_INT":
                PaillierKey pk = HomoAdd.generateKey();
                pk.printValues();

                BigInteger big1 =  BigInteger.valueOf(value.intValue());
                BigInteger encryptValue = HomoAdd.encrypt(big1, pk);
                System.out.println("value: " + value);
                System.out.println("encrypt Value: " + encryptValue);

                response = target.path(pathPublicKey)
                        .queryParam("value", encryptValue.toString())
                        .queryParam("nonce", nonce)
                        .queryParam("msg", msgHashStr)
                        .queryParam("type", type)
                        .request()
                        .post(Entity.entity(Reply.class, MediaType.APPLICATION_JSON));

                r = response.readEntity(Reply.class);
                break;

            case "HOMO_OPE_INT":

                long key = HomoOpeInt.generateKey();
                HomoOpeInt ope = new HomoOpeInt(key);
                Long openValue = ope.encrypt(value.intValue());
                response = target.path(pathPublicKey)
                        .queryParam("value", openValue.toString())
                        .queryParam("nonce", nonce)
                        .queryParam("msg", msgHashStr)
                        .queryParam("type", type)
                        .request()
                        .post(Entity.entity(Reply.class, MediaType.APPLICATION_JSON));

                r = response.readEntity(Reply.class);
                break;
            default:
                throw new IllegalStateException("Unexpected type: " + type);
        }

        long finalRequestTime = System.currentTimeMillis() - initRequestTime ;
        addMoneyRequestTimes.add(finalRequestTime);



        ArrayList<Double> amounts = new ArrayList<Double>();
        ArrayList<Long> lNonces = new ArrayList<Long>();

        for (int i = 0; i < r.getMessages().size(); i++) {
            ReplicaResponseMessage currentReplicaMsg = r.getMessages().get(i);

            ByteArrayInputStream byteIn = new ByteArrayInputStream(currentReplicaMsg.getContent());
            ObjectInput objIn = new ObjectInputStream(byteIn);
            Double replicaMsgAmount = (Double) objIn.readObject();
            //System.out.println("replica amount: "+ replicaMsgAmount);
            Long replicaNonce = (Long) objIn.readObject();
            //System.out.println("replica nonce: " + replicaNonce);


            amounts.add(replicaMsgAmount);
            lNonces.add(replicaNonce);

            KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
            java.security.PublicKey pk = keyLoader.loadPublicKey(currentReplicaMsg.getSender());
            Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
            sig.initVerify(pk);
            sig.update(currentReplicaMsg.getSerializedMessage());


            if (sig.verify(currentReplicaMsg.getSignature())) {
                System.out.println("Replica message coming from replica " + currentReplicaMsg.getSender() + " is authentic");
            } else {

                System.out.println("Signature of message is invalid");
            }

        }
        int majority = 0;
        int numbNonces = 0;
        for (Double amount : amounts) {
            if (amount == r.getAmount())
                majority++;
        }
        for (Long n : lNonces) {
            if (n + 1 == r.getNonce())
                numbNonces++;
        }

        // Verify majority of nonces of replicas
        if (numbNonces >= (lNonces.size() / 2) + 1) {
            System.out.println("majority of replicas returns the right nonce");
        } else {
            System.out.println("No majority reached for nonce");

        }

        // Verify majority from message replies of replicas
        if ((majority >= (amounts.size() / 2) + 1)) {
            System.out.println("majority of replicas returns the right value");
        } else {
            System.out.println("No majority reached");
        }


        // Check if response nonce(which is nonce+1) is equals to original nonce + 1
        if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.ADD_MONEY) {
            System.out.println("Nonces dont match, reject message from server");
        } else {

            System.out.println();
            System.out.println("Status: " + response.getStatusInfo());
            System.out.println("Add money to pubKey: " + publicString.substring(0, 50));
            System.out.println("Amount: " + r.getAmount());
            if (nonce + 1 == r.getNonce()) {
                System.out.println("Nonces match");
            }
            System.out.println();


        }
    }

    @SuppressWarnings("Duplicates")
    public static void getMoney(String type) throws Exception {
        System.out.println("#################################");
        System.out.println("####### G E T - M O N E Y #######");
        System.out.println("#################################");
        System.out.println();

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

        Reply r;

        String msg = publicString + nonce;
        byte[] hash = Digest.getDigest(msg.getBytes());

        byte[] hashEncriptPriv = priv.encrypt(hash);
        String msgHashStr = Base64.getEncoder().encodeToString(hashEncriptPriv);

        long initRequestTime = System.currentTimeMillis();

        Response response;
        switch (type) {

            case "WALLET":
                response = target.path(pathPublicKey + "/money")
                        .queryParam("nonce", nonce)
                        .queryParam("msg", msgHashStr)
                        .request()
                        .get();
                r = response.readEntity(Reply.class);
                break;

            case "HOMO_OPE_INT":
                int randValue1= random.nextInt();
                int randValue2=random.nextInt();
                while(randValue1 < randValue2) {
                    randValue1 = random.nextInt();
                }

                long key = HomoOpeInt.generateKey();
                HomoOpeInt ope = new HomoOpeInt(key);
                Long higher = ope.encrypt(randValue1);
                Long lower = ope.encrypt(randValue2);

                //TODO  pathPublicKey nao é preciso no url, deixamos?
                response = target.path(pathPublicKey+"/money")
                        .queryParam("higher", higher)
                        .queryParam("lower", lower)
                        .queryParam("nonce", nonce)
                        .queryParam("msg", msgHashStr)
                        .request()
                        .get();
                r = response.readEntity(Reply.class);
                break;
            default:
                throw new IllegalStateException("Unexpected type: " + type);
        }

        long finalRequestTime = System.currentTimeMillis() - initRequestTime ;
        getMoneyRequestTimes.add(finalRequestTime);



        ArrayList<Double> amounts = new ArrayList<Double>();
        ArrayList<Long> lNonces = new ArrayList<Long>();



        for (ReplicaResponseMessage currentReplicaMsg: r.getMessages()) {
            if (currentReplicaMsg != null) {

                ByteArrayInputStream byteIn = new ByteArrayInputStream(currentReplicaMsg.getContent());
                ObjectInput objIn = new ObjectInputStream(byteIn);
                Double replicaMsgAmount = (Double) objIn.readObject();
                //System.out.println("replica amount: "+ replicaMsgAmount);
                Long replicaNonce = (Long) objIn.readObject();
                //System.out.println("replica nonce: " + replicaNonce);


                amounts.add(replicaMsgAmount);
                lNonces.add(replicaNonce);

                KeyLoader keyLoader = new RSAKeyLoader(0, "config", false, "SHA256withRSA");
                java.security.PublicKey pk = keyLoader.loadPublicKey(currentReplicaMsg.getSender());
                Signature sig = Signature.getInstance("SHA512withRSA", "SunRsaSign");
                sig.initVerify(pk);
                sig.update(currentReplicaMsg.getSerializedMessage());

                if (sig.verify(currentReplicaMsg.getSignature())) {
                    System.out.println("Replica message coming from replica " + currentReplicaMsg.getSender() + " is authentic");
                } else {
                    System.out.println("Signature of message is invalid");
                }
            }
        }

        int majority = 0;
        int numbNonces = 0;
        for (Double amount : amounts) {
            if (amount == r.getAmount())
                majority++;
        }
        for (Long n : lNonces) {
            if (n + 1 == r.getNonce())
                numbNonces++;
        }

        // Verify majority of nonces of replicas
        if (numbNonces >= (lNonces.size() / 2) + 1) {
            System.out.println("majority of replicas returns the right nonce");
        } else {
            System.out.println("No majority reached for nonce");

        }

        // Verify majority from message replies of replicas
        if ((majority >= (amounts.size() / 2) + 1)) {
            System.out.println("majority of replicas returns the right value");
        } else {
            System.out.println("No majority reached");
        }

        // Check if response nonce(which is nonce+1) is equals to original nonce + 1
        if (r.getNonce() != nonce + 1 && r.getOperationType() == OpType.GET_MONEY) {
            System.out.println("Nonces dont match, reject message from server");
        } else {

            System.out.println();
            System.out.println("Status: " + response.getStatusInfo());
            System.out.println("From pubKey: " + publicString.substring(0, 50));
            System.out.println("New amount: " + r.getAmount());
            if (nonce + 1 == r.getNonce()) {
                System.out.println("Nonces match");
            }
            System.out.println();

        }
    }


    public static long getTransferAvgTime(){
        long totalTimeCounter=0;
        for(int i=0; i<transferRequestTimes.size(); i++){
            totalTimeCounter += transferRequestTimes.get(i);
        }
        return totalTimeCounter/transferRequestTimes.size();
    }
    public static long getGetMoneyAvgTime(){
        long totalTimeCounter=0;
        for(int i=0; i<getMoneyRequestTimes.size(); i++){
            totalTimeCounter += getMoneyRequestTimes.get(i);
        }
        return totalTimeCounter/getMoneyRequestTimes.size();
    }
    public static long getAddMoneyAvgTime(){
        long totalTimeCounter=0;
        for(int i=0; i<addMoneyRequestTimes.size(); i++){
            totalTimeCounter += addMoneyRequestTimes.get(i);
        }
        return totalTimeCounter/addMoneyRequestTimes.size();
    }


    static public class InsecureHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }


}
