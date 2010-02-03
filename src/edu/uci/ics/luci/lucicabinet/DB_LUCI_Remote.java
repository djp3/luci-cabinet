package edu.uci.ics.luci.lucicabinet;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import edu.uci.ics.luci.lucicabinet.Butler.ServerResponse;

/**
 * This class enables synchronized and asynchronous access to a tokyo-cabinet database over a socket connection.
 * The expected use is that the remote end would be running the Butler class and that this class would connect to it.
 */
public class DB_LUCI_Remote extends DB_LUCI{

	private Socket clientSocket;
	private ObjectOutputStream oos = null;
	private ObjectInputStream ois = null;
	public ExecutorService threadExecutor = null;
	private ReentrantLock fairLock = null;
	private String host = null;
	private Integer port = null;
	
	private static transient volatile Logger log = null;
	public Logger getLog(){
		if(log == null){
			log = Logger.getLogger(DB_LUCI_Remote.class);
		}
		return log;
	}
	
	/**
	 * @param host The remote host to connect to, e.g. "localhost", "192.128.1.20"
	 * @param port The port that the remote host is listening on.
	 */
	public DB_LUCI_Remote(String host,Integer port) {
		super();
		
		this.host = host;
		
		this.port = port;
		
		threadExecutor = Executors.newCachedThreadPool();
		
		fairLock = new ReentrantLock(true);
	}
	
	/**
	 * This method opens the connection to the remote Butler service.  If the host connection is
	 * non-null it overrides the host that was passed to the constructor.  If it is null, then the
	 * constructor provided host is used.
	 */
	public void open(String newHost){
		fairLock.lock();
		try{
			if(host != null){
				this.host = newHost;
			}
			
			clientSocket = new Socket(host,port);
		
			oos = new ObjectOutputStream(clientSocket.getOutputStream());
		
			ois = new ObjectInputStream(clientSocket.getInputStream());
		
			ServerResponse okay;
			try {
				okay = (ServerResponse) ois.readObject();
				if(!okay.equals(ServerResponse.CONNECTION_OKAY)){
					throw new RuntimeException("Remote host did not send a connection okay signal");
				}
			} catch (ClassNotFoundException e) {
				throw new IOException("Remote host did not send a connection okay signal"+e);
			} 
		} catch (UnknownHostException e) {
			getLog().log(Level.ERROR, "Unable to open "+host+":"+port+" for a connection",e);
		} catch (IOException e) {
			getLog().log(Level.ERROR, "Unable to open connection",e);
		}
		finally{
			fairLock.unlock();
		}
		
	}
	
	@Override
	/**
	 * A wrapper for shutdown
	 */
	public void close() {
		shutdown();
	}
	

	/**
	 * Gives all asynchronous operations up to 2 minutes to complete then tells the remote
	 * server that this connection is shutting down, then cleans up all the resources created by this class. 
	 */
	protected void shutdown(){
		
		/*Let the current commands finish */
		if(threadExecutor != null){
			threadExecutor.shutdown();
			try {
				threadExecutor.awaitTermination(120, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
			}
			
			threadExecutor.shutdownNow();
			
			threadExecutor = null;
		}
		
		/*Close the remote database */
		fairLock.lock();
		try{
			if(oos != null){
				try {
					oos.writeObject(Butler.ServerCommands.CLOSE);
				} catch (IOException e) {
					getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.CLOSE+" command",e);
				}
				checkForError(ois);
			}
		}
		finally{
			fairLock.unlock();
		}
		
		/* close the connections */
		try {
			if(ois != null){
				ois.close();
				ois = null;
			}
		} catch (IOException e) {
		}
		
		try {
			if(oos != null){
				oos.close();
				oos = null;
			}
		} catch (IOException e) {
		}
		
		try {
			if(clientSocket != null){
				clientSocket.close();
				clientSocket = null;
			}
		} catch (IOException e) {
		}
		
	}
	

	/** Wrapper for shutdown to make sure all resources are clean up */
	protected void finalize() throws Throwable{
		try{
			shutdown();
		} catch (Throwable e) {
			getLog().error(e.toString());
		}
		finally{
			super.finalize();
		}
	}
	
	/** A synchronous remove command.  Does not return until command is complete.
	 * 
	 * @param key the entry to remove
	 */
	public void removeSync(Serializable key){
		fairLock.lock();
		try{
			try {
				oos.writeObject(Butler.ServerCommands.REMOVE);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.REMOVE+" command",e);
			}
	
			try {
				oos.writeObject(key);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.REMOVE+" command parameter",e);
			}
			
			checkForError(ois);
		}
		finally{
			fairLock.unlock();
		}
	}
	
	
	private class RemoveAsync implements Runnable{
		private Serializable key;

		public RemoveAsync(Serializable key){
			this.key = key;
		}
		public void run() {
			removeSync(key);
		};
	}
	
	/**
	 * An asynchronous remove command. Runs on a separate thread.
	 * @param key the entry to remove
	 */
	public void removeAsync(Serializable key) {
		threadExecutor.execute(new RemoveAsync(key));
	}
	
	/**
	 * Remove an entry from the database asynchronously.
	 * @param key The entry to remove.
	 */
	public void remove(Serializable key){
		removeAsync(key);
	}
	

	/**
	 * A synchronous put command.  Does not return until command is complete.
	 * 
	 * @param key
	 * @param value
	 */
	public void putSync(Serializable key, Serializable value){
		fairLock.lock();
		try{
			try {
				oos.writeObject(Butler.ServerCommands.PUT);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.PUT+" command",e);
			}
			
			try {
				oos.writeObject(key);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.PUT+" command parameter,key",e);
			}
			
			try {
				oos.writeObject(value);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.PUT+" command parameter,value",e);
			}
			
			checkForError(ois);
		}
		finally{
			fairLock.unlock();
		}
	}
	
	
	private class PutAsync implements Runnable{
		private Serializable key;
		private Serializable value;

		public PutAsync(Serializable key,Serializable value){
			this.key = key;
			this.value = value;
		}
		public void run() {
			putSync(key,value);
		};
	}
	
	/**
	 * An asynchronous put command.
	 * 
	 * @param key
	 * @param value
	 */
	public void putAsync(Serializable key,Serializable value) {
		threadExecutor.execute(new PutAsync(key,value));
	}
	
	/**
	 * Put an entry into the database, asynchronously
	 * @param key
	 * @param value
	 */
	public void put(Serializable key, Serializable value){
		putAsync(key,value);
	}
	

	/** Get an entry from the database
	 * 
	 * @param key
	 * @return the value in the database. null if there is no entry
	 */
	public Serializable get(Serializable key){
		Serializable ret = null;
		
		fairLock.lock();
		try{
			try{
				oos.writeObject(Butler.ServerCommands.GET);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.GET+" command",e);
			}
			
			try {
				oos.writeObject(key);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.GET+" command parameter, key",e);
			}
			
			try {
				ret = (Serializable) ois.readObject();
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
			} catch (ClassNotFoundException e) {
				getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
			}
			
			checkForError(ois);
			
		}
		finally{
			fairLock.unlock();
		}
		return ret;
	}
	
	
	private void checkForError(ObjectInputStream ois){
		ServerResponse okay = null;
		try {
			okay = (ServerResponse) ois.readObject();
		} catch (IOException e) {
			getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
		} catch (ClassNotFoundException e) {
			getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
		}
		
		if(okay.equals(ServerResponse.COMMAND_SUCCESSFUL)){
			return;
		}
		
		try {
			okay = (ServerResponse) ois.readObject();
		} catch (IOException e) {
			getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
		} catch (ClassNotFoundException e) {
			getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
		}
		
		throw new RuntimeException("Bad Response from server:"+okay);
	}


	

	/**
	 * Synchronously run a job that iterates through all the entries in the database. Since the IteratorWorker
	 * may have internal state that the caller wants to access after the iteration, the returned value is the IteratorWorker
	 * after iterations.   
	 * @param iw a class representing the work to be done.
	 * @return the IteratorWorker after the work is complete.  
	 */
	public IteratorWorker iterateSync(IteratorWorker iw){
		IteratorWorker ret = null;
		
		fairLock.lock();
		try{
			try {
				oos.writeObject(Butler.ServerCommands.ITERATE);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.ITERATE+" command",e);
			}
				
			try {
				oos.writeObject(iw);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.ITERATE+" parameter, iw",e);
			}

			try {
				ret = (IteratorWorker) ois.readObject();
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
			} catch (ClassNotFoundException e) {
				getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
			}
				
			checkForError(ois);
		}
		finally{
			fairLock.unlock();
		}
		return(ret);
	}
	
	
	private class IterateAsync implements Runnable{
		IteratorWorker iw = null;

		public IterateAsync(IteratorWorker iw){
			this.iw = iw;
		}
		
		public void run() {
			iterateSync(iw);
		};
	}
	
	/**
	 * Asynchronously run a job that iterates through all the entries in the database. No state is returned and the
	 *  internal state of iw is never changed because it is sent remotely to do it's work.
	 * @param iw a class representing the work to be done.
	 */
	public void iterateAsync(IteratorWorker iw){
		threadExecutor.execute(new IterateAsync(iw));
	}
	
	/** Synchronously iterate over the entries in the database and call the appropriate methods in <param>iw</param>
	 * to do work.  See IteratorWorker for details on how the iteration works.  If there no information needs to be returned or
	 * collected, consider using <pre>iterateAsync</pre>.
	 * @param iw
	 */
	public IteratorWorker iterate(IteratorWorker iw){
		return(iterateSync(iw));
	}
	
	/**
	 * The number of entries in the database.
	 */
	public Long size(){
		Long ret = null;
		
		fairLock.lock();
		try{
			try{
				oos.writeObject(Butler.ServerCommands.SIZE);
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to write "+Butler.ServerCommands.SIZE+" command",e);
			}
			
			try {
				ret = (Long) ois.readObject();
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
			} catch (ClassNotFoundException e) {
				getLog().log(Level.ERROR, "Unable to read a result from object input stream",e);
			}
			
			checkForError(ois);
			
		}
		finally{
			fairLock.unlock();
		}
		return ret;
	}

	


}
