package edu.uky.cs.nil.tt;

import java.util.List;
import java.util.Random;

import edu.uky.cs.nil.tt.world.Status;
import edu.uky.cs.nil.tt.world.Turn;

class Test {
	
	public static void main(String[] args) throws Exception {
		System.out.println("Starting test client.");
		try(Client client = new TestClient()) {
			client.call();
		}
	}
	
	private static class TestClient extends Client {
		
		private final Random random = new Random(0);
		
		public TestClient() {
			super("api", "tutorial", Role.PLAYER, "test", "localhost", 9005);
		}
		
		@Override
		protected int onChoice(Status status) throws Exception {
			external.complete(new String[]{"You are a helpful assistant.","Tell me about George Washington.","He is my dog.","No he is not."});
			List<Turn> choices = status.getChoices();
			int choice = random.nextInt(choices.size());
			System.out.println(choices.get(choice).getDescription());
			return choice;
		}
	}
}