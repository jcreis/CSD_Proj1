package api;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.*;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

import bftsmart.reconfiguration.util.RSAKeyLoader;
import bftsmart.tom.ServiceProxy;
import com.fasterxml.jackson.annotation.JsonProperty;
import rest.server.CaptureMessages;
import rest.server.ReplicaServer;


@Path("/users")
public class WalletResources {

	int replicaNumber;

	Long nonce;

	ServiceProxy serviceProxy;

	ReplicaServer replicaServer;

	RSAKeyLoader keyLoader;

	CaptureMessages captureMessages = new CaptureMessages();

	public WalletResources(int replicaNumber) {
		this.replicaNumber = replicaNumber;
		System.out.println("replica number " + replicaNumber);
		replicaServer = new ReplicaServer(replicaNumber);
		keyLoader = new RSAKeyLoader(replicaNumber, "config", false, "sha512WithRSAEncryption");
		serviceProxy  = new ServiceProxy(replicaNumber, null,null, captureMessages, keyLoader);

	}

	public enum opType{
		TRANSFER,
		ADD_MONEY,
		GET_MONEY,
		GET_USERS,
		ADD_USER
	}

	private Map<String, User> db = new ConcurrentHashMap<String, User>();

	@GET
	@Produces(MediaType.APPLICATION_JSON)

	public Reply getUsers(@QueryParam("nonce") Long nonce) {
		User[] userReply ;
		Long replyNonce;

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			 ObjectOutput objOut = new ObjectOutputStream(byteOut);) {

			objOut.writeObject(opType.GET_USERS);
			objOut.writeObject(nonce);

			objOut.flush();
			byteOut.flush();

			// TODO Passar para Ordered ?
			byte[] reply = serviceProxy.invokeUnordered(byteOut.toByteArray());
			if (reply.length == 0) {
				//System.out.println("1");
				return null;
			}
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
				 ObjectInput objIn = new ObjectInputStream(byteIn)) {
				System.out.println("List of users: "+ db.values().toArray(new User[db.size()]));
				userReply = (User[])objIn.readObject();
				replyNonce = (Long)objIn.readObject();
				System.out.println(captureMessages.sendMessages());
				System.out.println("nonce :" + replyNonce);
				return new Reply(captureMessages.sendMessages(), userReply, replyNonce);
			}

		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception getting value from map: " + e.getMessage());
		}

		System.out.println( db.size());
		return new Reply(captureMessages.sendMessages(), db.values().toArray( new User[ db.size() ]), nonce);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Reply register(User user) {

		User userReply ;


		System.err.printf("register: %s <%s>\n", user.getId(), user);

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			 ObjectOutput objOut = new ObjectOutputStream(byteOut);) {

			objOut.writeObject(opType.ADD_USER);
			objOut.writeObject(user);

			objOut.flush();
			byteOut.flush();

			byte[] reply = serviceProxy.invokeOrdered(byteOut.toByteArray());
			if (reply.length == 0)
				return null;
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
				 ObjectInput objIn = new ObjectInputStream(byteIn)) {
				userReply = (User)objIn.readObject();
				return new Reply(captureMessages.sendMessages(), userReply, nonce);

			}

		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception putting value into map: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	@PUT
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Reply addMoney(@PathParam("id") String id, @QueryParam("value") Double value){
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			 ObjectOutput objOut = new ObjectOutputStream(byteOut);) {

			objOut.writeObject(opType.ADD_MONEY);
			objOut.writeObject(id);
			objOut.writeObject(value);

			objOut.flush();
			byteOut.flush();

			byte[] reply = serviceProxy.invokeOrdered(byteOut.toByteArray());
			if (reply.length == 0)
				return null;
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
				 ObjectInput objIn = new ObjectInputStream(byteIn)) {
				 double money = (Double)objIn.readObject();
				 return new Reply(captureMessages.sendMessages(), money, nonce) ;
			}

		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception putting value into map: " + e.getMessage());
		}
		return null;
	}


	@PUT
	@Path("/transfer/{fid}")
	@Produces(MediaType.APPLICATION_JSON)
	public Reply transferMoney(@PathParam("fid") String fid, @QueryParam("tid") String tid, @QueryParam("value") Double value){

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			 ObjectOutput objOut = new ObjectOutputStream(byteOut);) {

			objOut.writeObject(opType.TRANSFER);
			objOut.writeObject(fid);
			objOut.writeObject(tid);
			objOut.writeObject(value);

			objOut.flush();
			byteOut.flush();

			byte[] reply = serviceProxy.invokeOrdered(byteOut.toByteArray());
			if (reply.length == 0)
				return null;
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
				 ObjectInput objIn = new ObjectInputStream(byteIn)) {
				double money = (Double)objIn.readObject();
				return new Reply(captureMessages.sendMessages(), money, nonce) ;
			}

		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception putting value into map: " + e.getMessage());
		}
		return null;

	}

	@GET
	@Path("/{id}/money")
	@Produces(MediaType.APPLICATION_JSON)
	public Reply getMoney(@PathParam("id") String id){

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			 ObjectOutput objOut = new ObjectOutputStream(byteOut);) {

			objOut.writeObject(opType.GET_MONEY);
			objOut.writeObject(id);

			objOut.flush();
			byteOut.flush();

			byte[] reply = serviceProxy.invokeUnordered(byteOut.toByteArray());
			if (reply.length == 0)
				return null;
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
				 ObjectInput objIn = new ObjectInputStream(byteIn)) {
				double money = (Double)objIn.readObject();
				return new Reply(captureMessages.sendMessages(), money, nonce) ;
			}

		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception getting value from map: " + e.getMessage());
		}
		return null;

	}

}
