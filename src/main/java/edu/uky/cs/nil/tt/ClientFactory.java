package edu.uky.cs.nil.tt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A client factory creates continuously creates {@link Client clients} to
 * connect to a {@link Server server} and is useful for deploying automated
 * agents.
 * <p>
 * A factory starts by creating one client to join the server's queue of waiting
 * players. When that client finds a partner and starts its session, the factory
 * creates a new client to take its place in the queue.
 * <p>
 * You can limit the {@link #maxClients maximum number of clients} this factory
 * will run simultaneously.
 * <p>
 * If any client created by this factory throws an uncaught exception, this
 * factory will shut down gracefully and throw that exception after shutting
 * down.
 * <p>
 * This class provides several methods that are all called from the same thread
 * at important moments in the factory's lifecycle. These methods can be
 * overridden to, for example, log important information. See the {@link
 * #call()} method for a full description of these methods and when they are
 * called.
 * 
 * @author Stephen G. Ware
 */
public abstract class ClientFactory implements Callable<Void>, AutoCloseable {
	
	/**
	 * An operation is a {@link Runnable runnable} that catches and records any
	 * exception thrown while running so that the client factory from which it
	 * was run can close and later throw that exception.
	 * 
	 * @author Stephen G. Ware
	 */
	private abstract class Operation implements Callable<Void> {
		
		@Override
		public Void call() {
			try {
				run();
			}
			catch(Exception exception) {
				if(uncaught == null)
					uncaught = exception;
				close();
			}
			return null;
		}
		
		/**
		 * Completes this operation's task. 
		 * 
		 * @throws Exception if a problem occurred while the task was running
		 */
		protected abstract void run() throws Exception;
	}
	
	/**
	 * The maximum number of {@link Client clients} that this factory will
	 * create to run simultaneously
	 */
	public final int maxClients;
	
	/** A queue of operations to run on the {@link #call() calling thread} */
	private final LinkedBlockingQueue<Operation> queue = new LinkedBlockingQueue<>();
	
	/** Clients that are waiting for their sessions to start */
	private final List<Client> waiting = new ArrayList<>();
	
	/** Clients whose sessions are currently happening */
	private final List<Client> running = new ArrayList<>();
	
	/** An executor service to run each new client on a new virtual thread */
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	
	/**
	 * An exception that was not caught by a factory {@link Operation operation}
	 * that will be thrown at the end of the {@link #call()} method
	 */
	private Exception uncaught = null;
	
	/** Whether {@link #close()} has been called */
	private boolean closed = false;
	
	/** Used to await the full shutdown of the server */
	private final CountDownLatch latch = new CountDownLatch(1);
	
	/**
	 * Creates a new client factory with an upper limit on the number of clients
	 * that it will create simultaneously.
	 * 
	 * @param maxClients the maximum number of clients that may be running
	 * simultaneously, or 0 if there is no limit
	 */
	public ClientFactory(int maxClients) {
		this.maxClients = maxClients;
		// If the JVM shuts down, close the server and wait for it to shut down.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			close();
			try {
				latch.await();
			}
			catch(InterruptedException exception) {
				// do nothing
			}
		}));
	}
	
	/**
	 * Creates a new client factory with no upper limit on the number of clients
	 * that may run simultaneously.
	 */
	public ClientFactory() {
		this(0);
	}
	
	@Override
	public String toString() {
		String string = "[Client Factory: ";
		if(maxClients > 0)
			string += (waiting.size() + running.size()) + "/" + maxClients + " clients; ";
		return string + waiting.size() + " waiting; " + running.size() + " running]";
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * This method starts the client factory, creates the first client,
	 * periodically creates new clients as needed, and shuts down gracefully
	 * if the factory is closed or if a client throw an uncaught exception.
	 * <p>
	 * As this method runs, it calls other methods to notify the factory of
	 * important events. Some of these methods are guaranteed to be called even
	 * if an exception is thrown. The list of those methods and when they happen
	 * is as follows:
	 * <ul>
	 * <li>The {@link #onStart()} method is called first. If this method throws
	 * an exception it is thrown immediately, and no other methods are called.
	 * </li>
	 * <li>The first client is created and {@link Client#call() runs} on a new
	 * virtual thread.</li>
	 * <li>Each time a client starts its session, a new client will be created
	 * to take its place in the server's queue of waiting clients. If creating a
	 * new client would exceed this factory's {@link #maxClients limit} on the
	 * number of clients which can run simultaneously, this factory will wait
	 * until a running client has completed its session to start the next
	 * client.</li>
	 * <li>If one of the clients created by this factory throws an exception, or
	 * if the {@link #close()} method is called, the {@link #onClose()} method
	 * will run on the thread which called this method, regardless of which
	 * thread called {@link #close()}. If the thread which called this method
	 * is interrupted, {@link #onClose()} will not be called.</li>
	 * <li>Any clients whose sessions have not yet begun will be {@link
	 * Client#close() closed}. Clients whose sessions have begun will not be
	 * closed. If this factory stopped because of an exception or because the
	 * {@link #close()} method was called, it will wait for all clients whose
	 * sessions have started to finish. If this factory stopped because the
	 * thread which called this method was interrupted, it will not wait for
	 * running clients to finish their sessions.</li>
	 * <li>After all running clients have finished their sessions, {@link
	 * #onStop()} is called. If this factory stopped because the thread which
	 * called this method was interrupted, that method will not be called.</li>
	 * <li>If a client threw an exception, that exception will be thrown from
	 * this method. If no exception was thrown, this method returns null.</li>
	 * </ul>
	 */
	@Override
	public Void call() throws Exception {
		// Notify factory that it has started.
		onStart();
		// Interrupting skips lifecycle methods.
		boolean interrupted = false;
		try {
			// Create first client.
			createNewClient();
			// Run operations until closed.
			while(!closed)
				queue.take().call();
			// Notify factory that is has closed.
			try {
				onClose();
			}
			catch(Exception exception) {
				if(uncaught == null)
					uncaught = exception;
			}
		}
		catch(InterruptedException exception) {
			interrupted = true;
		}
		// Close waiting clients.
		for(Client client : waiting)
			client.close();
		// Wait for clients to finish.
		executor.shutdown();
		if(!interrupted) {
			try {
				while(!executor.awaitTermination(1, TimeUnit.SECONDS));
			}
			catch(InterruptedException exception) {
				interrupted = true;
			}
		}
		// Notify factory it has stopped.
		if(!interrupted) {
			try {
				onStop();
			}
			catch(Exception exception) {
				if(uncaught == null)
					uncaught = exception;
			}
		}
		// Empty the lists and queue.
		waiting.clear();
		running.clear();
		queue.clear();
		// Signal that the server is done shutting down.
		latch.countDown();
		// Return or throw uncaught exception.
		if(uncaught == null)
			return null;
		else
			throw uncaught;
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Causes the factory to shut down gracefully, closing any waiting clients
	 * and waiting for any running clients to finish. This method can be called
	 * safely from any thread. It will cause the thread which called {@link
	 * #call()} to stop creating new clients, to call the {@link #onClose()}
	 * method, to close all clients whose sessions have not started, to wait for
	 * all clients whose sessions have started to end, and finally to stop.
	 */
	@Override
	public void close() {
		queue.offer(new Operation() {
			@Override
			protected void run() throws Exception {
				closed = true;
			}
		});
	}
	
	/**
	 * This method is called once when this factory begins running.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the factory
	 * wants to do something when the factory first begins running.
	 * 
	 * @throws Exception if a problem occurred during this method
	 */
	protected void onStart() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method creates and returns a new client of the type produced by this
	 * factory. The factory will always call this method from the thread which
	 * called {@link #call()}.
	 * 
	 * @return a new instance of the client this factory produces
	 * @throws Exception if a problem occurred while creating the client
	 */
	protected abstract Client create() throws Exception;
	
	/**
	 * This method is called once after this factory stops creating new clients
	 * and has begun shutting down. This method is called either because this
	 * factory was {@link #close() closed} or because a client threw an
	 * exception. This method will always be called from the thread which called
	 * {@link #call()}. This method will not be called if the client shuts down
	 * because the {@link #call()} method was interrupted.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the factory
	 * wants to do something when the factory first begins shutting down.
	 * 
	 * @throws Exception if a problem occurred during this method
	 */
	protected void onClose() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called once at the end of this factory's shutdown process
	 * after all clients have stopped running. This method will always be called
	 * from the thread which called {@link #call()}. This method will not be
	 * called if the client shuts down because the {@link #call()} method was
	 * interrupted.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the factory
	 * wants to do something just before the factory stops running.
	 * 
	 * @throws Exception if a problem occurred during this method
	 */
	protected void onStop() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * {@link #create() Creates} a new client and starts a new thread for it to
	 * run on. This method should always be called from the thread which called
	 * {@link #call()}.
	 * 
	 * @throws Exception if a problem occurs while creating the client
	 */
	private final void createNewClient() throws Exception {
		Client client = create();
		waiting.add(client);
		executor.submit(new Operation() {
			@Override
			protected void run() throws Exception {
				try {
					client.call(ClientFactory.this);
				}
				finally {
					onStop(client);
				}
			}
		});
	}
	
	/**
	 * This method is called from {@link Client#call() the client's call} method
	 * when the client begins its session. If the {@link #maxClients client
	 * limit} allows, this method will cause the factory to create a new client
	 * to take the starting client's place in the server's queue of waiting
	 * clients.
	 * 
	 * @param client a client created by this factory whose session has started
	 */
	final void onStart(Client client) {
		queue.offer(new Operation() {
			@Override
			protected void run() throws Exception {
				waiting.remove(client);
				running.add(client);
				if(maxClients <= 0 || waiting.size() + running.size() < maxClients)
					createNewClient();
			}
		});
	}
	
	/**
	 * This method is called to alert the factory that a client it created has
	 * completed its session and/or disconnected.
	 * 
	 * @param client a client created by this factory which is finished running
	 */
	private final void onStop(Client client) {
		queue.offer(new Operation() {
			@Override
			protected void run() throws Exception {
				if(running.remove(client) && waiting.size() == 0)
					createNewClient();
				else
					waiting.remove(client);
			}
		});
	}
}