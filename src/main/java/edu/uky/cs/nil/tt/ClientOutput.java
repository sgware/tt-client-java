package edu.uky.cs.nil.tt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.uky.cs.nil.tt.io.Message;

final class ClientOutput {
	
	private final BufferedWriter output;
	private final Gson gson;
	
	public ClientOutput(Socket socket) throws IOException {
		this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		GsonBuilder builder = new GsonBuilder();
		Message.configure(builder);
		this.gson = builder.create();
	}
	
	public void send(Message message) {
		String string = gson.toJson(message);
		try {
			output.append(string);
			output.append("\n");
			output.flush();
		}
		catch(IOException exception) {
			// Ignore this exception.
		}
	}
}