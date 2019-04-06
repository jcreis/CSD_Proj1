package rest.server;


import api.WalletResources;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReplicaServer extends DefaultSingleRecoverable {

    private Map<String, Double> db = new ConcurrentHashMap<String, Double>();



    public ReplicaServer(int id){
        new ServiceReplica(id, this, this);
        db.put("09325224767032", 10.0);
    }


    @Override
    public void installSnapshot(byte[] bytes) {

    }

    @Override
    public byte[] getSnapshot() {
        return new byte[0];
    }

    @Override
    public byte[] appExecuteOrdered(byte[] bytes, MessageContext messageContext){
        byte[] reply = null;
        String publicKey;
        String publicKey2;
        Double value;
        boolean hasReply = false;
        Long nonce;

        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(bytes);
             ObjectInput objIn = new ObjectInputStream(byteIn);
             ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ObjectOutput objOut = new ObjectOutputStream(byteOut);) {

            WalletResources.opType reqType = (WalletResources.opType)objIn.readObject();

            switch (reqType) {
                case ADD_MONEY:

                    publicKey = (String)objIn.readObject();
                    value = (Double)objIn.readObject();
                    nonce = (Long)objIn.readObject();


                    if(db.containsKey(publicKey)) {
                        if (value >= 0){
                            db.put(publicKey, db.get(publicKey) + value);
                            // returns updated money
                            objOut.writeObject(db.get(publicKey));
                            objOut.writeObject(nonce);

                            hasReply = true;
                        }
                        else {
                            System.out.println("Invalid amount.");
                        }
                    }
                    else{
                        db.put(publicKey, value);
                        objOut.writeObject(db.get(publicKey));
                        objOut.writeObject(nonce);

                        hasReply = true;
                    }
                    break;



                case ADD_USER:

                    publicKey = (String)objIn.readObject();
                    value = (Double)objIn.readObject();
                    nonce = (Long)objIn.readObject();
                    if(!db.containsKey(publicKey)) {
                        db.put(publicKey, value);
                        System.out.println("User " + db.get(publicKey) + " added to Database.");

                       /* objOut.writeObject(user);*/
                        objOut.writeObject(nonce);

                        hasReply = true;

                    }
                    else{
                        System.out.println("User already exists in the database.");
                    }

                    break;


                case TRANSFER:

                    publicKey = (String)objIn.readObject();
                    publicKey2 = (String)objIn.readObject();
                    value = (Double)objIn.readObject();
                    nonce = (Long)objIn.readObject();



                    if(!db.containsKey(publicKey) || !db.containsKey(publicKey2)){
                        System.out.println("User not found.");
                    }
                    else if(value < 0){
                        System.out.println("Invalid amount.");
                    }
                    else{

                        if(db.get(publicKey) >= value){
                            db.put(publicKey, db.get(publicKey) - value);
                            db.put(publicKey2, db.get(publicKey2) + value);
                            objOut.writeObject(db.get(publicKey2));
                            System.out.println("User "+ publicKey2 + " now has "+ db.get(publicKey2)+"€");
                            objOut.writeObject(nonce);

                            hasReply = true;
                        }
                        else{
                            System.out.println("User making the transfer does not have enough money.");
                        }
                    }
                    break;

            }
            if (hasReply) {
                objOut.flush();
                byteOut.flush();
                reply = byteOut.toByteArray();
            } else {
                reply = new byte[0];
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("dadsasdadadad");
        }
        System.out.println(db);
        return reply;
    }

    @SuppressWarnings("unchecked")
    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        byte[] reply = null;
        boolean hasReply = false;
        String publicKey;
        Long nonce;

        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(command);
             ObjectInput objIn = new ObjectInputStream(byteIn);
             ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
            WalletResources.opType reqType = (WalletResources.opType)objIn.readObject();
            switch (reqType) {

               /* case GET_USERS:

                    nonce = (Long)objIn.readObject();


                    System.out.println("List of users: "+ db.values().toArray(new User[db.size()]).toString());

                    // Prepares output
                    objOut.writeObject(db.values().toArray(new User[db.size()]));

                    objOut.writeObject(nonce);
                    // Allows output to be set to ByteArray in the final phase of the method
                    hasReply = true;
                    break;*/

                case GET_MONEY:
                    publicKey = (String)objIn.readObject();
                    nonce = (Long)objIn.readObject();

                    if(db.containsKey(publicKey)) {
                        System.out.println("Amount: " + db.get(publicKey));

                        objOut.writeObject(db.get(publicKey));
                        objOut.writeObject(nonce);

                        hasReply = true;
                    }
                    else {
                        System.out.println("User not found in the database.");
                    }
                    break;

                default:
                    System.out.println("error");


            }
            if (hasReply) {
                objOut.flush();
                byteOut.flush();
                reply = byteOut.toByteArray();
            } else {
                reply = new byte[0];
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e);
        }

        return reply;
    }

}

