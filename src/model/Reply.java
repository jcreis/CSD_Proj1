package model;

import java.util.ArrayList;
import java.util.List;

public class Reply {

    ArrayList<ReplicaResponseMessage> messages;
    String publicKey;
    double amount;
    List<String> listAmounts;
    long nonce;

    OpType operationType;




    public Reply(OpType operationType, ArrayList<ReplicaResponseMessage> messages, String publicKey, double amount, long nonce){
        this.messages = messages;
        this.publicKey = publicKey;
        this.amount = amount;
        this.nonce = nonce;
        this.operationType = operationType;

    }
    public Reply(OpType operationType, ArrayList<ReplicaResponseMessage> messages, String publicKey, List<String> listAmounts, long nonce){
        this.messages = messages;
        this.publicKey = publicKey;
        this.listAmounts = listAmounts;
        this.nonce = nonce;
        this.operationType = operationType;

    }

    public Reply() {
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public ArrayList<ReplicaResponseMessage> getMessages() {
        return messages;
    }

    public OpType getOperationType() {
        return operationType;
    }

    public void setOperationType(OpType operationType) {
        this.operationType = operationType;
    }

    public void setMessages(ArrayList<ReplicaResponseMessage> messages) {
        this.messages = messages;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }
}
