package edu.uky.cs.nil.tt;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import edu.uky.cs.nil.tt.io.*;
import edu.uky.cs.nil.tt.io.Error;
import edu.uky.cs.nil.tt.world.Ending;
import edu.uky.cs.nil.tt.world.State;
import edu.uky.cs.nil.tt.world.Status;
import edu.uky.cs.nil.tt.world.Turn;
import edu.uky.cs.nil.tt.world.World;

/**
 * A client connects to a {@link Server server} to find a partner and play a
 * {@link Role role} in a storytelling {@link Session session}.
 * <p>
 * This abstract class implements the necessary communication protocol to
 * connect to the server, find a partner, and start a session. Each time a
 * {@link Status status update} arrives from the server that requires this
 * client to make a choice, the {@link #onChoice(Status)} method is called to
 * determine what choice the agent wants to make.
 * <p>
 * This class provides several other methods that are all called from the same
 * thread at important moments in the client's lifecycle. These methods can be
 * overridden to, for example, log important information or update the client's
 * world model. See the {@link #call()} method for a full description of these
 * methods and when they are called.
 * <p>
 * If the session begins and ends normally the client's {@link #call()} method
 * returns the session ID of the completed session. If this client disconnects
 * early, or if a problem occurs that does not cause an uncaught exception, that
 * method will return null.
 * 
 * @author Stephen G. Ware
 */
public abstract class Client implements Callable<String>, AutoCloseable {
	
	/**
	 * The name of the environment variable where the client expects to find the
	 * password this agent will use, unless the password is explicitly set by
	 * the agent's constructor. If this environment variable is not set and no
	 * password is provided in the constructor, this agent will not use a
	 * password.
	 */
	public static final String ENVIRONMENT_VARIABLE_PASSWORD = "password";
	
	/**
	 * The name of the environment variable where the client expects to find the
	 * API key used to authenticate with the service that provides functions
	 * which require special external resources or computation. If this
	 * environment variable is not set and no API key is provided in the
	 * constructor, this agent will not be able to use the external API.
	 */
	public static final String ENVIRONMENT_VARIABLE_API_KEY = "apikey";
	
	/**
	 * The default URL the client will attempt to connect to if one is not
	 * explicitly provided in the constructor
	 */
	public static final String DEFAULT_URL = "localhost";
	
	/**
	 * The default network port the client will attempt to connect to if one is
	 * not explicitly provided in the constructor
	 */
	public static final int DEFAULT_PORT = Settings.DEFAULT_PORT;
	
	/** The client's API key */
	private final String key;
	
	/** The URL where the client will attempt to connect */
	private final String url;
	
	/** The network port the client will attempt to connect on */
	private final int port;
	
	/**
	 * The client's join request, containing their name, password, and other
	 * session preferences
	 */
	private Join join;
	
	/** The secure socket used to connect to the server */
	private SSLSocket socket = null;
	
	/** Used to read messages received over the socket */
	private ClientInput input = null;
	
	/** Used to send messages over the socket */
	private ClientOutput output = null;
	
	/** Whether the client has sent its join request yet */
	private boolean joined = false;
	
	/** The world in which this client's story takes places */
	private World world = null;
	
	/** This client's role in the story */
	private Role role = null;
	
	/** The current status of the story world as received from the server */
	private Status status = null;
	
	/** The current choices available to the client */
	private List<Turn> choices = null;
	
	/** The stop message received from the server */
	private Stop stop = null;
	
	/** The session ID received from the server at the end of the session */
	private String session = null;
	
	/**
	 * Constructs a client with the given session preferences, password, API
	 * key, and network details.
	 * 
	 * @param name the name the client will use
	 * @param password the password the client will provide to the server, or
	 * null if the client will not use a password
	 * @param world the name of the world the client wants their session to take
	 * place in, or null if the client has no preference for a story world
	 * @param role the role the client wants to play in the session, or null if
	 * the client is willing to play either role
	 * @param partner the name of the client's desired partner, or null if the
	 * client is willing to play with any partner
	 * @param key the API key the client will use to access external resources
	 * and computation, or null if the client will not use the external API
	 * @param url the URL of the server to which this client will connect
	 * @param port the network port on which this client will connect
	 */
	public Client(String name, String password, String world, Role role, String partner, String key, String url, int port) {
		Utilities.requireNonNull(name, "name");
		this.join = new Join(name, password, world, role, partner);
		this.key = key;
		Utilities.requireNonNull(url, "server URL");
		this.url = url;
		this.port = port;
	}
	
	/**
	 * Constructs a client with the given session preferences and network
	 * details, reading the password and API key from the environment. The
	 * client's password will be read from {@link
	 * #ENVIRONMENT_VARIABLE_PASSWORD}. The client's API key will be read from
	 * {@link #ENVIRONMENT_VARIABLE_API_KEY}.
	 * 
	 * @param name the name the client will use
	 * @param world the name of the world the client wants their session to take
	 * place in, or null if the client has no preference for a story world
	 * @param role the role the client wants to play in the session, or null if
	 * the client is willing to play either role
	 * @param partner the name of the client's desired partner, or null if the
	 * client is willing to play with any partner
	 * @param url the URL of the server to which this client will connect
	 * @param port the network port on which this client will connect
	 */
	public Client(String name, String world, Role role, String partner, String url, int port) {
		this(name, System.getenv(ENVIRONMENT_VARIABLE_PASSWORD), world, role, partner, System.getenv(ENVIRONMENT_VARIABLE_API_KEY), url, port);
	}
	
	/**
	 * Constructs a client with the given session preferences, reading the
	 * password and API key from the environment, and using the default network
	 * settings.
	 * 
	 * @param name the name the client will use
	 * @param world the name of the world the client wants their session to take
	 * place in, or null if the client has no preference for a story world
	 * @param role the role the client wants to play in the session, or null if
	 * the client is willing to play either role
	 * @param partner the name of the client's desired partner, or null if the
	 * client is willing to play with any partner
	 */
	public Client(String name, String world, Role role, String partner) {
		this(name, world, role, partner, DEFAULT_URL, DEFAULT_PORT);
	}
	
	/**
	 * Constructs a client with a name, role, and world name, which has no
	 * preference for a partner, reading the password and API key from the
	 * environment, and using the default network settings.
	 * 
	 * @param name the name the client will use
	 * @param world the name of the world the client wants their session to take
	 * place in, or null if the client has no preference for a story world
	 * @param role the role the client wants to play in the session, or null if
	 * the client is willing to play either role
	 */
	public Client(String name, String world, Role role) {
		this(name, world, role, null);
	}
	
	/**
	 * Constructs a client with a given name, which has no preference for its
	 * role, world, or partner, reading the password and API key from the
	 * environment, and using the default network settings.
	 * 
	 * @param name the name the client will use
	 */
	public Client(String name) {
		this(name, null, null, null);
	}
	
	@Override
	public String toString() {
		String string = "[Client: name=\"" + join.name + "\"";
		if(join.password != null)
			string += "; password=\"***\"";
		if(join.world != null)
			string += "; world=\"" + join.world + "\"";
		if(join.role != null)
			string += "; role=" + join.role;
		if(join.partner != null)
			string += "; partner=\"" + join.partner + "\"";
		return string + "]";
	}
	
	/**
	 * Returns the client's name.
	 * 
	 * @return the client's name
	 */
	public String getName() {
		return join.name;
	}
	
	/**
	 * Sets the name this client will use.
	 * 
	 * @param name the new name this client will use
	 * @throws IllegalStateException if the client has already joined the server
	 */
	protected void setName(String name) { 
		failIfJoined("name");
		join = new Join(name, join.password, join.world, join.role, join.partner);
	}
	
	/**
	 * Sets the password this client will use.
	 * 
	 * @param password the new password this client will use
	 * @throws IllegalStateException if the client has already joined the server
	 */
	protected void setPassword(String password) {
		failIfJoined("password");
		join = new Join(join.name, password, join.world, join.role, join.partner);
	}
	
	/**
	 * Returns the name of the story world this client will play in or is
	 * currently playing in. A null value means this client's session has not
	 * yet started this client is willing to play in any world. Once the session
	 * starts, this method will return the name of the world the client is
	 * playing in.
	 * 
	 * @return the name of the client's story world
	 */
	public String getWorldName() {
		if(world == null)
			return join.world;
		else
			return world.name;
	}
	
	/**
	 * Sets the name of the story world this client wants to play in.
	 * 
	 * @param world the name of the world this client wants to play in
	 * @throws IllegalStateException if the client has already joined the server
	 */
	protected void setWorldName(String world) {
		failIfJoined("world");
		join = new Join(join.name, join.password, world, join.role, join.partner);
	}
	
	/**
	 * Returns the role this client wants to play or is currently playing in its
	 * session. A null value means this client's session has not yet started and
	 * this client is willing to play either role. Once the session starts, this
	 * method will return the role assigned to the client.
	 * 
	 * @return the client's role
	 */
	public Role getRole() {
		if(role == null)
			return join.role;
		else
			return role;
	}
	
	/**
	 * Sets the role this client wants to play in its session.
	 * 
	 * @param role the role this client wants to play in its session
	 * @throws IllegalStateException if the client has already joined the server
	 */
	protected void setRole(Role role) {
		failIfJoined("role");
		join = new Join(join.name, join.password, join.world, role, join.partner);
	}
	
	/**
	 * Returns the name of the partner this client wants to play with. A null
	 * value means this client is willing to play with any partner. This value
	 * does not change when the session starts. In other words, if the client
	 * did not request a specific partner, they client has no way to know the
	 * name of the partner they were assigned.
	 * 
	 * @return the name of the partner this client wants to play with
	 */
	public String getPartner() {
		return join.partner;
	}
	
	/**
	 * Sets the name of the partner this client wants to play in its session.
	 * 
	 * @param partner the name of the partner this client wants to play with
	 * @throws IllegalStateException if the client has already joined the server
	 */
	protected void setPartner(String partner) {
		failIfJoined("partner");
		join = new Join(join.name, join.password, join.world, join.role, partner);
	}
	
	/**
	 * If the client has sent its join request, this method throws an exception
	 * that explains why a property can no longer be modified.
	 * 
	 * @param property the name of the property which can no longer be modified
	 * after joining the server
	 * @throws IllegalStateException if the client has already sent its join
	 * request
	 */
	private final void failIfJoined(String property) {
		if(joined)
			throw new IllegalStateException("The client's " + property + " can no longer be changed because the client has already joined the server.");
	}
	
	/**
	 * Returns the {@link World story world} in which this client's session is
	 * taking place.
	 * 
	 * @return the story world for this client's session
	 * @throws IllegalStateException if the client's session has not yet started
	 */
	public World getWorld() {
		return failItNotStarted(world, "world");
	}
	
	/**
	 * Returns the list of all {@link Turn turns} that have been taken so far in
	 * this client's session that this client has observed. If the client is the
	 * {@link Role#GAME_MASTER game master}, they always observe all turns. If
	 * this client is the {@link Role#PLAYER player}, they may not observe all
	 * turns.
	 * 
	 * @return the list of all turns that have been taken so far in the session
	 * @throws IllegalStateException if the client's session has not yet started
	 */
	public List<Turn> getHistory() {
		return failItNotStarted(status, "history").getHistory();
	}
	
	/**
	 * Returns the current {@link State state} of the story world.
	 * 
	 * @return the current state of the story world
	 * @throws IllegalStateException if the client's session has not yet started
	 */
	public State getState() {
		return failItNotStarted(status, "state").getState();
	}
	
	/**
	 * If it is currently this client's turn to act, this method returns the
	 * list of {@link Turn turns} they can take next. If it is not thie client's
	 * turn, the list will be empty.
	 * 
	 * @return the list of turns the client can take next
	 * @throws IllegalStateException if the client's session has not yet started
	 */
	public List<Turn> getChoices() {
		return failItNotStarted(choices, "list of choices");
	}
	
	/**
	 * Returns the ID of this client's session. A session is only assigned after
	 * the session has ended, so the value returned by this method will always
	 * be null until the very end of this client's lifecycle.
	 * 
	 * @return the session ID of this client's session
	 */
	public String getSession() {
		return session;
	}
	
	/**
	 * If the object passed to this method is null, this method throws an
	 * exception explaining that it is not available because the session has
	 * not started. Otherwise, the given object is returned.
	 * 
	 * @param <T> the type of object given as input and returned as output
	 * @param object the object to be returned if it is not null
	 * @param description a description of the object
	 * @return the object
	 * @throws IllegalStateException if the object is null
	 */
	private static final <T> T failItNotStarted(T object, String description) {
		if(object == null)
			throw new IllegalStateException("The " + description + " is not available because the client's session has not started yet.");
		else
			return object;
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * This method connects to the server, joins, waits for a session to start,
	 * sends the choices made by this client, and eventually returns the session
	 * ID of the completed session.
	 * <p>
	 * As this method runs, it calls other methods to notify the client of
	 * important events. Some of these methods are guaranteed to be called even
	 * if an exception is thrown. The list of those methods and when they happen
	 * is as follows:
	 * <ul>
	 * <li>The {@link #connect(String, int)} method is called to establish a
	 * secure socket to the server. If an exception is thrown by this method,
	 * it will be thrown immediately, and the rest of the methods below will not
	 * be called.</li>
	 * <li>After the client receives the {@link Connect connect} message from
	 * the server, {@link #onConnect(Connect)} is called. This is the client's
	 * last chance to make changes to its identity or session details.</li>
	 * <li>After the client receives the {@link Start start} message and the
	 * first {@link Update update} message, the session begins and the {@link
	 * #onStart(World, Role, State)} method is called. Immediately before that
	 * method is called, method like {@link #getWorld()} will be able to return
	 * their values.</li>
	 * <li>Each time the client receives an {@link Update update} message, the
	 * {@link #onUpdate(Status)} method is called to notify the client about
	 * changes to the session history and world state. This method is called
	 * whether or not the client need to make a choice.</li>
	 * <li>Each time the client receives an {@link Update update} message and it
	 * is the client's turn to act, after calling {@link #onUpdate(Status)} the
	 * {@link #onChoice(Status)} method will be called to solicit which turn the
	 * client wants to take. After calling that method, the {@link
	 * #getChoices()} method will return an empty list.</li>
	 * <li>If the session's story reaches one of its pre-defined endings, the
	 * {@link #onEnd(Ending)} method will be called.</li>
	 * <li>If the session ended because this client was {@link #close() closed},
	 * because the client thread was interrupted, or because the socket was
	 * disconnected, the {@link #onClose()} method will be called. If an
	 * exception was thrown earlier in this method, {@link #onClose()} will not
	 * be called.</li>
	 * <li>If the session started, the {@link #onStop(String)} method will
	 * always be called before this method returns or throw an exception.</li>
	 * <li>If the client successfully connected to the server, the {@link
	 * #onDisconnect()} method will always be called before this method returns
	 * or throws an exception.</li>
	 * </ul>
	 */
	@Override
	public final String call() throws Exception {
		return call(null);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Disconnects this client from the server, causing it to stop. This method
	 * can be called safely from any thread. If this method is called before the
	 * client connects (e.g. before {@link #call()}), it does nothing.
	 */
	@Override
	public void close() {
		if(socket != null) {
			try {
				socket.close();
			}
			catch(IOException exception) {
				// Ignore this exception.
			}
		}
	}
	
	final String call(ClientFactory factory) throws Exception {
		// Warn if password or API key are missing.
		if(join.password == null && System.getenv(ENVIRONMENT_VARIABLE_PASSWORD) == null)
			onWarning("The environment variable \"" + ENVIRONMENT_VARIABLE_PASSWORD + "\" is not set. This agent will not use a password.");
		if(key == null && System.getenv(ENVIRONMENT_VARIABLE_API_KEY) == null)
			onWarning("The environment variable \"" + ENVIRONMENT_VARIABLE_API_KEY + "\" is not set. This agent will not be able to use the external API.");
		// Connect the socket input and output.
		socket = connect(url, port);
		input = new ClientInput(socket);
		output = new ClientOutput(socket);
		input.start();
		// If an exception is thrown, catch it to throw later.
		Exception uncaught = null;
		try {
			// Wait for the connect message.
			Connect connect = receive(Connect.class);
			if(connect != null) {
				// Warn if the server's version number does not match.
				if(!connect.version.equals(Settings.VERSION))
					onWarning("This client is using version " + Settings.VERSION + " of the communication protocol, but the server is using version " + connect.version + ". This may cause misconnunications.");
				// Notify the client is has connected.
				onConnect(connect);
				// Send the join message.
				joined = true;
				send(join);
			}
			// Wait for the start message.
			Start start = receive(Start.class);
			if(start != null) {
				world = start.world;
				role = start.role;
				// Notify the factory that created this client that its session has started.
				if(factory != null)
					factory.onStart(this);
			}
			// Receive and process messages.
			while(world != null) {
				Message message = receive();
				// Stop if disconnected.
				if(message == null)
					break;
				// When the world status updates...
				else if(message instanceof Update update) {
					// If this is the first update, notify the client the session has started.
					if(this.status == null) {
						this.status = update.status;
						this.choices = update.status.getChoices();
						onStart(world, role, status.getState());
					}
					// Otherwise, update the current status and notify the client.
					else {
						this.status = update.status;
						this.choices = update.status.getChoices();
						onUpdate(status);
					}
					// If it is the client's turn, make a choice.
					if(update.status.getChoices().size() > 0) {
						int index = onChoice(status);
						choices = List.of();
						if(!input.getStopped())
							send(new Choice(index));
					}
					// If the story has ended, notify the client.
					else if(update.status.getEnding() != null)
						onEnd(update.status.getEnding());
				}
				// Immediately acknowledge stop messages.
				else if(message instanceof Stop) {
					send(new Stop(getRole()));
					stop = (Stop) message;
					onStop(stop.message);
				}
				// Get session ID from end message.
				else if(message instanceof End end) {
					session = end.session;
					break;
				}
				// Handle errors reported by the server.
				else if(message instanceof Error error)
					onError(error.message);
				// Handle unrecognized message types.
				else
					onError("The message type \"" + message.getClass().getSimpleName() + "\" is not recognized.");
			}
		}
		catch(Exception exception) {
			uncaught = exception;
		}
		// If the client ended normally, notify.
		if(uncaught == null) {
			try {
				onClose();
			}
			catch(Exception exception) {
				uncaught = exception;
			}
		}
		// If the session has started but not yet stopped, notify.
		if(world != null && stop == null) {
			try {
				onStop(null);
			}
			catch(Exception exception) {
				if(uncaught == null)
					uncaught = exception;
			}
		}
		// Ensure the socket is closed.
		close();
		// Notify the client it has disconnected.
		try {
			onDisconnect();
		}
		catch(Exception exception) {
			if(uncaught == null)
				uncaught = exception;
		}
		// Throw an uncaught exception or return the session ID.
		if(uncaught == null)
			return session;
		else
			throw uncaught;
	}
	
	/**
	 * Establishes a secure socket to the server based on this client's network
	 * configuration.
	 * <p>
	 * By default, this method uses Java's {@link SSLSocketFactory Secure Socket
	 * Layer} to create and return a new socket.
	 * 
	 * @param url the URL of the server
	 * @param port the network port on which to open the socket
	 * @return a secure socket connection to the server
	 * @throws IOException if a problem occurred establishing the socket
	 * @throws SecurityException if a security manager exists and it does not
	 * allow the socket to be established
	 * @throws java.net.UnknownHostException if the URL of server is not known
	 * @throws IllegalArgumentException if the network port is outside the
	 * specified range of valid port values
	 */
	protected SSLSocket connect(String url, int port) throws Exception {
		SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		SSLSocket socket = (SSLSocket) factory.createSocket(url, port);
		socket.startHandshake();
		return socket;
	}
	
	/**
	 * This method blocks until the client receives a message of a given type
	 * from the server. If the next message received is not of the correct type,
	 * this method throws an exception. If the client's connection to the server
	 * is closed before it receives a message, this method returns null.
	 * <p>
	 * It is usually unsafe for implementations of this abstract class to call
	 * this method. The {@link #call()} method has specific expectations about
	 * when and what type of messages will be received. Calling this method at
	 * an unexpected time is likely to cause this client to get out of sync with
	 * the server.
	 * 
	 * @param <M> the type of message the client expects
	 * @param type the message class this client expects to receive
	 * @return the message received from the server, or null if the connection
	 * was closed
	 * @throws IllegalStateException if the client has not yet connected to the
	 * server
	 */
	protected <M extends Message> M receive(Class<M> type) {
		if(input == null)
			throw new IllegalStateException("A message cannot be received because the client has not connected to the server yet.");
		else
			return input.receive(type);
	}
	
	/**
	 * This method blocks until the client receives a message from the server
	 * and then returns that message. If the client's connection to the server
	 * is closed before it receives a message, this method returns null.
	 * 
	 * @return the message received from the server, or null if the connection
	 * was closed
	 * @throws IllegalStateException if the client has not yet connected to the
	 * server
	 * @see #receive(Class)
	 */
	protected Message receive() {
		return receive(Message.class);
	}
	
	/**
	 * This method sends a message to the server.
	 * <p>
	 * It is usually unsafe for implementations of this abstract class to call
	 * this method. The {@link #call()} method has specific expectations about
	 * when and what type of messages should be sent. Calling this method at an
	 * unexpected time is likely to cause this client to get out of sync with
	 * the server.
	 * 
	 * @param message the message to send to the server
	 * @throws IllegalStateException if the client has not yet connected to the
	 * server
	 */
	protected void send(Message message) {
		if(output == null)
			throw new IllegalStateException("A message cannot be sent because the client has not connected to the server yet.");
		else
			output.send(message);
	}
	
	/**
	 * This method is called after the client connects to the server and the
	 * server sends the list of available worlds and agents. This method is
	 * typically the last chance the client has to change its identity, such as
	 * its {@link #setName(String) name} or {@link #setRole(Role) requested
	 * role}, before the client's join request is sent.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the client
	 * wants to react to connecting to the server.
	 * 
	 * @param connect the connect message sent from the server
	 * @throws Exception if a problem occurs during this method
	 */
	protected void onConnect(Connect connect) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called once when the client's session starts.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the client
	 * wants to react to its session starting.
	 * 
	 * @param world the story world in which the session will take place
	 * @param role the role this client will play in the story
	 * @param initial the initial state of the story world before the story
	 * begins
	 * @throws Exception if a problem occurs during this method
	 */
	protected void onStart(World world, Role role, State initial) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called each time the story world changes as a result of a
	 * turn the client observes. If this client is the {@link Role#GAME_MASTER
	 * game master}, it will observe all turns. If this client is the {@link
	 * Role#PLAYER player}, it may not observe all turns.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the client
	 * wants to react to a change in the world state.
	 * 
	 * @param status the current status of the story world, including the
	 * history of all turns and the current world state
	 * @throws Exception if a problem occurs during this method
	 */
	protected void onUpdate(Status status) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called each time it is this client's turn to make a choice
	 * in the story. Calls to this method will always be preceded by a call to
	 * {@link #onStart(World, Role, State)} if this is the start of the session
	 * or {@link #onUpdate(Status)} if this is not the start of the session.
	 * 
	 * @param status the current status of the story world, including the {@link
	 * Status#getHistory() history of all turns so far}, the {@link
	 * Status#getState() current state} of the world, and the {@link
	 * Status#getChoices() list of choices} available for the client to choose
	 * from
	 * @return the index (starting at 0) of the turn this client wants to take
	 * from the {@link Status#getChoices() list of choices} given
	 * @throws Exception if a problem occurs during this method
	 */
	protected abstract int onChoice(Status status) throws Exception;
	
	/**
	 * This method is called once if the story reaches one of its {@link
	 * World#getEndings() pre-defined endings}. If the session never starts or
	 * of the story does not reach an ending, this method will not be called.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the client
	 * wants to react to the end of the story.
	 * 
	 * @param ending the ending of the story
	 * @throws Exception if a problem occurs during this method
	 */
	protected void onEnd(Ending ending) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called once when the client's session ends. If the session
	 * started, this method will always be called, even if an uncaught exception
	 * was thrown at an earlier stage in the client's lifecycle.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the client
	 * wants to react to the end of the session.
	 * 
	 * @param message the message explaining why the session ended, or null if
	 * no explanation was received
	 * @throws Exception if a problem occurs during this method
	 */
	protected void onStop(String message) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called once if the client was {@link #close() closed}, if
	 * it was interrupted, or if the socket was disconnected. This method will
	 * not be called if the client is stopping because an uncaught exception was
	 * thrown at an earlier stage of the client's lifecycle. This method is
	 * always called from the {@link #call()} method, meaning it will always run
	 * on the thread which called that method, even if a different thread called
	 * the {@link #close()} method.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the client
	 * wants to react to being closed.
	 * 
	 * @throws Exception if a problem occurs during this method
	 */
	protected void onClose() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * If the client ever established its connection to the server, this method
	 * is called once right before the {@link #call()} method returns or throws
	 * an exception.
	 * <p>
	 * By default, this method does nothing. It can be overridden if the client
	 * wants to react to the end of its lifecycle. This is a good method to
	 * clean up resources.
	 * 
	 * @throws Exception if a problem occurs during this method
	 */
	protected void onDisconnect() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called if this client encounters a problem which does not
	 * immediately require it to close but which may cause problems.
	 * <p>
	 * By default, this method prints the warning to {@link System#err standard
	 * error}.
	 * 
	 * @param message a description of the problem
	 * @throws Exception if this method throws an exception
	 */
	protected void onWarning(String message) throws Exception {
		System.err.println("Warning: " + message);
	}
	
	/**
	 * This method is called if the server reports an {@link Error error} to
	 * this client.
	 * <p>
	 * By default, if the session has not yet started, this method throws a
	 * {@link RuntimeException} whose message is the message passed to this
	 * method. If the session has started, the error will be printed to
	 * {@link System#err standard error} and no exception will be thrown.
	 * 
	 * @param message an explanation of what caused the error
	 * @throws Exception if this method throws an exception
	 */
	protected void onError(String message) throws Exception {
		if(world == null)
			throw new RuntimeException(message);
		else
			System.err.println("Error: " + message);
	}
	
	/**
	 * Makes an external call to a large language model API to complete a text
	 * prompt.
	 * <p>
	 * This method requires the agent to have an {@link
	 * #ENVIRONMENT_VARIABLE_API_KEY API key}; without one, this method will
	 * throw an exception.
	 * 
	 * @param system the system prompt which instructs the language model how
	 * to respond to the prompt
	 * @param prompt the prompt which the large language model will respond to
	 * @param temperature a parameter influencing the predictability of the
	 * language model's output, where 0 means completely predictable and
	 * higher values mean less predictable (more "creative") output
	 * @return the response from the large language model to the prompt
	 * @throws IllegalStateException if the client does not have an API key
	 */
	protected String complete(String system, String prompt, float temperature) {
		getKey();
		// TODO
		return null;
	}
	
	/**
	 * Makes an external call to a large language model API to embed a text
	 * string in the model's latent space.
	 * <p>
	 * This method requires the agent to have an {@link
	 * #ENVIRONMENT_VARIABLE_API_KEY API key}; without one, this method will
	 * throw an exception.
	 * 
	 * @param string the string to embed
	 * @return a vector representing the string's embedding
	 * @throws IllegalStateException if the client does not have an API key
	 */
	protected float[] embed(String string) {
		getKey();
		// TODO
		return null;
	}
	
	/**
	 * Returns this client's {@link #key API key} or throws an exception if it
	 * does not have one.
	 * 
	 * @return the client's API key
	 * @throws IllegalStateException if the client does not have an API key
	 */
	private final String getKey() {
		if(key == null)
			throw new IllegalStateException("The client does not have an API key, so it cannot use the external API.");
		else
			return key;
	}
}