/**
 * This library provides the abstract classes {@link edu.uky.cs.nil.tt.Client}
 * and {@link edu.uky.cs.nil.tt.ClientFactory} for implementing
 * <a href="https://sgware.github.io/tt">Tandem Tales</a> agents in Java.
 * <p>
 * {@link Client} handles connecting to the server and joining a session. A
 * minimal subclass of Client only needs to implement the {@link
 * Client#onChoice(edu.uky.cs.nil.tt.world.Status) onChoice(Status)} method to
 * choose what the agent will do on its turn.
 * <p>
 * {@link ClientFactory} continuously creates new clients to connect to the
 * server and wait for partners. A minimal subclass of ClientFactory only needs
 * to implement the {@link ClientFactory#create() create()} method to return a
 * new Client instance.
 * <p>
 * Here is an example client and factory:
 * {@snippet :
 * package edu.uky.cs.nil.tt;
 * 
 * import java.util.List;
 * import java.util.Random;
 * 
 * import edu.uky.cs.nil.tt.world.Status;
 * import edu.uky.cs.nil.tt.world.Turn;
 * 
 * public class RandomAgent extends Client {
 * 
 *     private final Random random = new Random();
 * 
 *     public RandomAgent(String url, int port) {
 *         super("random", null, null, null, url, port);
 *     }
 * 	
 *     @Override
 *     protected int onChoice(Status status) throws Exception {
 *         List<Turn> choices = status.getChoices();
 *         int choice = random.nextInt(choices.size());
 *         System.out.println(choices.get(choice).getDescription());
 *         return choice;
 *     }
 * }}
 * {@snippet :
 * package edu.uky.cs.nil.tt;
 * 
 * public class RandomAgentFactory extends ClientFactory {
 * 
 *     @Override
 *     protected Client create() throws Exception {
 *         return new RandomAgent("localhost", 9005);
 *     }
 * 
 *     public static void main(String[] args) throws Exception {
 *         try(RandomAgentFactory factory = new RandomAgentFactory()) {
 *             factory.call();
 *         }
 *     }
 * }}
 * <p>
 * Both classes have other lifecycle methods that are meant to be overridden.
 * <p>
 * {@link Client} has the following lifecycle methods:
 * <ul>
 * <li>{@link Client#onConnect(edu.uky.cs.nil.tt.io.Connect) onConnect(Connect)},
 * which runs after it connects to the server.</li>
 * <li>{@link Client#onStart(edu.uky.cs.nil.tt.world.World, Role, edu.uky.cs.nil.tt.world.State) onStart(World, Role, State)},
 * which runs after its session starts.</li>
 * <li>{@link Client#onUpdate(edu.uky.cs.nil.tt.world.Status) onUpdate(Status)},
 * which runs each time the server sends a state update (even if it is not the
 * client's turn).</li>
 * <li>{@link Client#onChoice(edu.uky.cs.nil.tt.world.Status) onChoice(Status)},
 * which runs each time the server sends a state update and it is the client's
 * turn.</li>
 * <li>{@link Client#onEnd(edu.uky.cs.nil.tt.world.Ending) onEnd(Ending)},
 * which runs if the story session reaches a pre-defined ending.</li>
 * <li>{@link Client#onClose() onClose()}, which runs after the client ends its
 * session without throwing an exception.</li>
 * <li>{@link Client#onStop(String) onStop(String)}, which runs after the
 * client's session has ended.</li>
 * <li>{@link Client#onDisconnect() onDisconnect()}, which runs after the client
 * has disconnected.</li>
 * </ul>
 * <p>
 * {@link ClientFactory} has the following lifecycle methods:
 * <ul>
 * <li>{@link ClientFactory#onStart() onStart()}, which runs after the factory
 * starts.</li>
 * <li>{@link ClientFactory#onClose() onClose()}, which runs after the factory
 * has been shut down.</li>
 * <li>{@link ClientFactory#onStop() onStop()}, which runs before the factory
 * stops.</li>
 * </ul>
 * <p>
 * {@link Client} also provides a way to call {@link Client#external an external
 * API} for expensive operations like text embedding and text completion.
 * <ul>
 * <li>{@link ExternalAPI#embed(String) embed(String)} lets you convert a text
 * string to an array of floats.</li>
 * <li>{@link ExternalAPI#complete(String...) complete(String...} lets you
 * generate text that is a fitting continuation of a sequence of messages.</li>
 * </ul>
 */
package edu.uky.cs.nil.tt;