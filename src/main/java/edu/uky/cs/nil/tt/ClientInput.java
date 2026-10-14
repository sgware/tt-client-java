package edu.uky.cs.nil.tt;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.uky.cs.nil.tt.io.Error;
import edu.uky.cs.nil.tt.io.Message;
import edu.uky.cs.nil.tt.io.Stop;

final class ClientInput extends Thread implements Closeable {
	
	private static final Message CLOSE = new Message() {
		@Override
		public void verify() {
			// Do nothing.
		}
	};
	
	private final BufferedReader input;
	private final Gson gson;
	private final LinkedBlockingQueue<Message> received = new LinkedBlockingQueue<>();
	private RuntimeException exception = null;
	private boolean stopped = false;
	private boolean closed = false;
	
	public ClientInput(Socket socket) throws IOException {
		this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		GsonBuilder builder = new GsonBuilder();
		Message.configure(builder);
		this.gson = builder.create();
	}
	
	@Override
	public void run() {
		while(true) {
			String line;
			try {
				line = input.readLine();
			}
			catch(Exception exception) {
				line = null;
			}
			try {
				if(line == null)
					break;
				else {
					Message message = gson.fromJson(line, Message.class);
					if(message instanceof Stop)
						stopped = true;
					received.offer(message);
				}
			}
			catch(RuntimeException exception) {
				this.exception = exception;
				break;
			}
		}
		close();
	}
	
	@Override
	public void close() {
		received.offer(CLOSE);
	}
	
	public boolean getStopped() {
		return stopped;
	}
	
	public <M extends Message> M receive(Class<M> type) {
		// If closed, immediately return null;
		if(closed)
			return null;
		// If an exception is waiting to be thrown, throw it.
		else if(exception != null)
			throw exception;
		// Wait for a message to be received.
		Message message;
		try {
			message = received.take();
		}
		// Interrupting is equivalent to closing.
		catch(InterruptedException exception) {
			message = CLOSE;
		}
		// If an exception happened while waiting, throw it.
		if(exception != null)
			throw exception;
		// If this is the close sentinel, close.
		if(message == CLOSE) {
			closed = true;
			received.clear();
			return null;
		}
		// If this is the expected type of message, return it.
		else if(type.isAssignableFrom(message.getClass()))
			return type.cast(message);
		// Otherwise, close and throw an exception.
		else {
			closed = true;
			received.clear();
			throw new IllegalArgumentException("Expected \"" + type.getSimpleName() + "\" message but received \"" + message.getClass().getSimpleName() + "\" message" + (message instanceof Error error ? ": \"" + error.message + "\"" : "") + ".");
		}
	}
}