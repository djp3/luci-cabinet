package edu.uci.ics.luci.lucicabinet;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * LUCI_Butler is a class that provides socket access to a LUCI_HDB database.
 * See the use cases for example code
 *
 */
public class LUCI_Butler<K extends Serializable,V extends Serializable> implements Runnable{
	
	enum ServerCommands {PUT,GET,REMOVE,ITERATE, CLOSE, SIZE, CLEAR, SET_OPTIMIZE};
	enum ServerResponse {CONNECTION_OKAY_OPTIMIZE, CONNECTION_OKAY_UNOPTIMIZE,COMMAND_SUCCESSFUL,COMMAND_FAILED};
	
	private boolean shuttingDown = false;
	protected LUCICabinetMap<K,V> db;
	protected AccessControl checker;
	private ServerSocket serverSocket = null;
	
	private static transient volatile Logger log = null;
	public static Logger getLog(){
		if(log == null){
			log = Logger.getLogger(LUCI_Butler.class);
		}
		return log;
	}
	
	/**
	 * Stop this butler service.
	 */
	public synchronized void shutdown(){
		shuttingDown = true;
		if(serverSocket != null){
			synchronized(serverSocket){
				serverSocket.notifyAll();
			}
			try {
				serverSocket.close();
			} catch (IOException e) {
				getLog().log(Level.ERROR,"Could not close Server Socket");
			}
		}
	}
	
	/**
	 * 
	 * @param db The database to expose
	 * @param port The port to accept commands on
	 * @param checker An object which tells us which connections are allowed. Examples are in the library package
	 */
	public LUCI_Butler(LUCICabinetMap<K,V> db,int port,AccessControl checker){
		this.db = db;
		this.checker = checker;
		try {
		    serverSocket = new ServerSocket(port);
		} catch (IOException e) {
		    getLog().log(Level.ERROR,"Could not listen on port:"+port);
		}
	}
	
	/**
	 * This method is called to begin accepting socket connections. 
	 */
	public void initialize(){
		if(serverSocket == null){
			throw new RuntimeException("Unable to start LUCI_Butler object");
		}
		
		Thread t = new Thread(this);
		t.setName("LUCI_Butler Socket Accept Thread");
		t.setDaemon(false); /*Force an explicit shutdown call */
		t.start();
	}

	/**
	 * Class to handle requests to LUCI_Butler
	 *
	 */
	private class Handler implements Runnable{
		
		private Socket clientSocket = null;

		public Handler(Socket clientSocket){
			this.clientSocket = clientSocket;
		}

		@SuppressWarnings("unchecked")
		public void run() {
			boolean done = false;
			ObjectInputStream ois = null;
			ObjectOutputStream oos = null;
			
			try{
				String source = clientSocket.getInetAddress().toString();
				if(checker.allowSource(source)){
					/* Get the object input stream */
					try {
						ois = new ObjectInputStream(clientSocket.getInputStream());
					} catch (IOException e) {
						getLog().log(Level.ERROR, "Unable to create object input stream",e);
						return;
					}
				
					/* Get the object output stream */
					try {
						oos = new ObjectOutputStream(clientSocket.getOutputStream());
					} catch (IOException e) {
						getLog().log(Level.ERROR, "Unable to create object output stream",e);
						return;
					}
					
					try {
						if(db.getOptimize()){
							oos.writeObject(ServerResponse.CONNECTION_OKAY_OPTIMIZE);
						}
						else{
							oos.writeObject(ServerResponse.CONNECTION_OKAY_UNOPTIMIZE);
						}
						oos.flush();
					} catch (IOException e) {
						getLog().log(Level.ERROR, "Unable to write to object output stream",e);
					}
				
					while(!done){
						String response = "";
						
						/* Get the command */
						ServerCommands command = null;
						try {
							command = (LUCI_Butler.ServerCommands) ois.readObject();
						} catch (IOException e) {
							getLog().log(Level.ERROR, "Unable to read a command from object input stream",e);
							response += e;
						} catch (ClassNotFoundException e) {
							getLog().log(Level.ERROR, "Unable to read a command from object input stream",e);
							response += e;
						}
					
						/*Process the command */
						if(command != null){
							if(command.equals(LUCI_Butler.ServerCommands.REMOVE)){
								Serializable key= null;
								try {
									key  = (K) ois.readObject();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to read the key to remove from object input stream",e);
									response += e.toString();
								} catch (ClassNotFoundException e) {
									getLog().log(Level.ERROR, "Unable to read the key to remove from object input stream",e);
									response += e.toString();
								}
					
								if(key != null){
									/*Execute remove */
									V thing = null;
									try{
										thing = db.remove(key);
									}
									catch(RuntimeException e){
										getLog().log(Level.ERROR, "Unable to read the remove object from database",e);
										response += e.toString();
									}

									if(!db.getOptimize()){
										try {
											oos.writeObject(thing);
										} catch (IOException e) {
											getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
											response += e.toString();
										}
									}
								}
							}
							else if(command.equals(LUCI_Butler.ServerCommands.PUT)){
								K key= null;
								try {
									key  =  (K) ois.readObject();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to read the key to put from object input stream",e);
									response += e.toString();
								} catch (ClassNotFoundException e) {
									getLog().log(Level.ERROR, "Unable to read the key to put from object input stream",e);
									response += e.toString();
								}
					
								V value = null;
								try {
									value = (V) ois.readObject();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to read the value to put from object input stream",e);
									response += e.toString();
								} catch (ClassNotFoundException e) {
									getLog().log(Level.ERROR, "Unable to read the value to put from object input stream",e);
									response += e.toString();
								}
				
								/*Execute put */
								V thing = null;

								try{
									thing = db.put(key,value);
								}
								catch(RuntimeException e){
									getLog().log(Level.ERROR, "Unable to put key-value pair into database",e);
									response += e.toString();
								}
								
								if(!db.getOptimize()){
									try {
										oos.writeObject(thing);
									} catch (IOException e) {
										getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
										response += e.toString();
									}
								}
								
							}
							else if(command.equals(LUCI_Butler.ServerCommands.GET)){
								Serializable key= null;
								try {
									key = (Serializable) ois.readObject();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to read a key to get object input stream",e);
									response += e.toString();
								} catch (ClassNotFoundException e) {
									getLog().log(Level.ERROR, "Unable to read a key to get object input stream",e);
									response += e.toString();
								} catch(RuntimeException e){
									getLog().log(Level.ERROR, "Unable to read a key to get object input stream",e);
									response += e.toString();
								}
					
								/*Execute get */
								Serializable value = db.get(key);
								try {
									oos.writeObject(value);
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
									response += e.toString();
								}
							}
							else if(command.equals(LUCI_Butler.ServerCommands.ITERATE)){
								Class<? extends IteratorWorker> iwClass= null;
								try {
									iwClass  = (Class<? extends IteratorWorker>) ois.readObject();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to read an Iterator Worker Class from object input stream",e);
									response += e.toString();
								} catch (ClassNotFoundException e) {
									getLog().log(Level.ERROR, "Unable to read an Iterator Worker Class from object input stream",e);
									response += e.toString();
								} catch(RuntimeException e){
									getLog().log(Level.ERROR, "Unable to read an Iterator Worker Class from object input stream",e);
									response += e.toString();
								}
								
								IteratorWorkerConfig iwConfig = null;
								try {
									iwConfig  = (IteratorWorkerConfig) ois.readObject();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to read an IteratorWorkerConfig from object input stream",e);
									response += e.toString();
								} catch (ClassNotFoundException e) {
									getLog().log(Level.ERROR, "Unable to read an IteratorWorkerConfig from object input stream",e);
									response += e.toString();
								} catch(RuntimeException e){
									getLog().log(Level.ERROR, "Unable to read an IteratorWorkerConfig from object input stream",e);
									response += e.toString();
								}
								
								IteratorWorker iw = null;
								try{
									iw = db.iterate((Class<? extends IteratorWorker<K, V>>) iwClass,iwConfig);
								} catch (InstantiationException e) {
									getLog().log(Level.ERROR, "Unable to iterate on a database",e);
									response += e.toString();
								} catch (IllegalAccessException e) {
									getLog().log(Level.ERROR, "Unable to iterate on a database",e);
									response += e.toString();
								}
								catch(RuntimeException e){
									getLog().log(Level.ERROR, "Unable to iterate on a database",e);
									response += e.toString();
								}

								try {
									oos.writeObject(iw);
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
									response += e.toString();
								}
							}
							else if(command.equals(LUCI_Butler.ServerCommands.CLOSE)){
								done = true;
							}
							else if(command.equals(LUCI_Butler.ServerCommands.SIZE)){
								Long ret = db.sizeLong();

								try {
									oos.writeObject(ret);
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
									response += e.toString();
								}
							}
							else if(command.equals(LUCI_Butler.ServerCommands.CLEAR)){
								db.clear();
							}
							else if(command.equals(LUCI_Butler.ServerCommands.SET_OPTIMIZE)){
								try {
									Boolean optimize = (Boolean) ois.readObject();
									db.setOptimize(optimize);
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to read a key to get object input stream",e);
									response += e.toString();
								} catch (ClassNotFoundException e) {
									getLog().log(Level.ERROR, "Unable to read a key to get object input stream",e);
									response += e.toString();
								} catch(RuntimeException e){
									getLog().log(Level.ERROR, "Unable to read a key to get object input stream",e);
									response += e.toString();
								}
							}
							else{
								done = true;
								getLog().log(Level.ERROR, "Unknown command sent to LUCI_Butler:"+command);
							}
							
							/* Return result */
							if(response.equals("")){
								try{
									oos.writeObject(ServerResponse.COMMAND_SUCCESSFUL);
									oos.flush();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
									return;
								}
							}
							else{
								try{
									oos.writeObject(ServerResponse.COMMAND_FAILED);
									oos.flush();
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
									return;
								}
						
								try {
									oos.writeObject(response);
								} catch (IOException e) {
									getLog().log(Level.ERROR, "Unable to write a result to object output stream",e);
									return;
								}
								
								done = true;
							}
						}
					}
				}
			}
			finally{
				if(oos != null){
					try {
						oos.close();
					} catch (IOException e) {
						getLog().log(Level.ERROR, "Unable to close oos",e);
					}
				}
				if(ois != null){
					try {
						ois.close();
					} catch (IOException e) {
						getLog().log(Level.ERROR, "Unable to close ois",e);
					}
				}
				if(clientSocket != null){
					try {
						clientSocket.close();
					} catch (IOException e) {
						getLog().log(Level.ERROR, "Unable to close clientSocket",e);
					}
				}
			}
		}
	}
	

	public void run() {
		Socket clientSocket = null;
		while(!shuttingDown){
			try {
				/* Grab a connection */
				try{
					clientSocket = serverSocket.accept();
				} catch (SocketException e) {
					/* Socket closed is okay because that's what happens when LUCI_Butler shuts down */
					if(e.getMessage().equals("Socket closed")){
						shuttingDown = true;
					}
					else{
						throw e;
					}
				}
				if(!shuttingDown){
					Thread t = new Thread(new Handler(clientSocket));
					t.setDaemon(false);
					t.start();
				}
			} catch (IOException e) {
				getLog().log(Level.ERROR, "Unable to accept a clientSocket",e);
			}
		}
	}
	

}
