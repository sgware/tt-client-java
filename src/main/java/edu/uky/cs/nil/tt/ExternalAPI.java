package edu.uky.cs.nil.tt;

/**
 * The external API is how a {@link Client client} contacts remote services to
 * perform computations the client cannot perform on its own.
 * <p>
 * An external API typically requires an API key, which is usually a long string
 * that acts as both a user name and password. An API key allows this client to
 * authenticate itself with the external service and allows that service to
 * limit what functions and resources this client can access.
 * <p>
 * This object represents a client's connection to the external API for that
 * client's lifetime, which is one session. Limits on the resources this API can
 * use exist only for that session; there is no common budget that clients
 * share.
 * 
 * @author Stephen G. Ware
 */
public class ExternalAPI {
	
	/**
	 * Represents the different levels of effort that can be used by a reasoning
	 * model during {@link ExternalAPI#complete(String...) text completion}.
	 * Higher levels of effort use more output tokens but may generate better
	 * quality responses.
	 * 
	 * @author Stephen G. Ware
	 */
	public enum ReasoningEffort {
		
		/** Highest level of reasoning effort */
		XHIGH,
		
		/** High reasoning effort */
		HIGH,
		
		/** Middle reasoning effort */
		MEDIUM,
		
		/** Low level of reasoning effort */
		LOW,
		
		/** Lowest level of reasoning effort */
		MINIMAL,
		
		/** Do not perform reasoning */
		NONE
	}
	
	/**
	 * The maximum number of tokens that can be used by the text embedding API
	 * during this client's session
	 */
	public static final int EMBED_TOKENS_LIMIT = 1000000;
	
	/**
	 * The maximum number of input tokens that can be used by the text
	 * completion API during this client's session
	 */
	public static final int COMPLETE_IN_TOKENS_LIMIT = 1000000;
	
	/**
	 * The maximum number of output tokens that can be used by the text
	 * completion API during this client's session
	 */
	public static final int COMPLETE_OUT_TOKENS_LIMIT = 1000000;
	
	/** The URL of the external text embedding API */
	private static final String EMBED_URL = "https://openrouter.ai/api/v1/embeddings";
	
	/** The name of the external text embedding model */
	private static final String EMBED_MODEL = "qwen/qwen3-embedding-8b";
	
	/** The URL of the external text completion API */
	private static final String COMPLETE_URL = "https://openrouter.ai/api/v1/chat/completions";
	
	/** The name of the external text completion model */
	private static final String COMPLETE_MODEL = "deepseek/deepseek-v4-flash-0731";
	
	/** The random seed passed to the text completion model */
	private static final int COMPLETE_SEED = 0;
	
	/** The key used by the client to authenticate with the external service. */
	private final String key;
	
	/** Number of tokens used this session for text embedding */
	private int embedTokensUsed = 0;
	
	/** Size of embedding vectors, or null if setting is not used */
	private Integer embedDimensions = null;
	
	/** Number of input tokens used this session for text completion */
	private int completeInTokensUsed = 0;
	
	/** Number of output tokens used this session for text completion */
	private int completeOutTokensUsed = 0;
	
	/**
	 * Limit on the number of output tokens generated during text completion, or
	 * null if this setting is not used
	 */
	private Integer completeMaxTokens = null;
	
	/**
	 * Level of reasoning effort for text completion, or null if this setting
	 * is not used
	 */
	private ReasoningEffort completeReasoningEffort = null;
	
	/** Temperature for text completion, or null if this setting is not used */
	private Float completeTemperature = null;
	
	/**
	 * Top k setting for text completion, or null if this setting is not used
	 */
	private Integer completeTopK = null;
	
	/**
	 * Top p setting for text completion, or null if this setting is not used
	 */
	private Float completeTopP = null;
	
	/**
	 * Min p setting for text completion, or null if this setting is not used
	 */
	private Float completeMinP = null;
	
	/**
	 * Constructs an interface to the external API using an API key.
	 * 
	 * @param key a string used by the client to authenticate with the external
	 * API, or null if the API does not require a key
	 */
	public ExternalAPI(String key) {
		this.key = key;
	}
	
	/**
	 * Returns true if this interface to the external API has a key that can be
	 * used to authenticate with the external service.
	 * 
	 * @return true if this interface has an API key, false otherwise
	 */
	public boolean hasKey() {
		return key != null;
	}
	
	/**
	 * Returns the number of tokens used for {@link #embed(String) text
	 * embedding} so far during this session.
	 * 
	 * @return the number of embedding tokens used
	 * @see #getEmbedTokensRemaining()
	 * @see #embed(String)
	 */
	public int getEmbedTokensUsed() {
		return embedTokensUsed;
	}
	
	/**
	 * Returns the number of tokens that can be used for {@link #embed(String)
	 * text embedding} before {@link EMBED_TOKENS_LIMIT the limit} is exceeded.
	 * 
	 * @return the number of tokens that can be used for text embedding
	 * @see #getEmbedTokensUsed()
	 * @see #embed(String)
	 */
	public int getEmbedTokensRemaining() {
		return Math.max(0, EMBED_TOKENS_LIMIT - embedTokensUsed);
	}
	
	/**
	 * Returns the number of dimensions that will be used when {@link
	 * #embed(String) embedding a string} (i.e. the length of the vector
	 * returned by {@link #embed(String)}). A null value means this setting is
	 * not being used and the external API will decide the length of the vector.
	 * 
	 * @return the number of dimensions used for string embedding, or null if
	 * this setting is not being used
	 * @see #setEmbedDimensions(Integer)
	 * @see #embed(String)
	 */
	public Integer getEmbedDimensions() {
		return embedDimensions;
	}
	
	/**
	 * Sets the number of dimensions that will be used when {@link
	 * #embed(String) embedding a string} (i.e. the length of the vector
	 * returned by {@link #embed(String)}). A null value means this setting will
	 * not be used and the external API will decide the length of the vector.
	 * 
	 * @param value the new number of dimensions to use for string embedding
	 * @throws IllegalArgumentException if the value is less than 1
	 * @see #getEmbedDimensions()
	 * @see #embed(String)
	 */
	public void setEmbedDimensions(Integer value) {
		if(value != null)
			checkInteger("Embedding dimensions", value, 1, Integer.MAX_VALUE);
		embedDimensions = value;
	}
	
	/**
	 * Queries the external text embedding API to convert a string into a
	 * high-dimensional numeric vector. The number of dimensions that the
	 * resulting vector will have can be set via {@link
	 * #setEmbedDimensions(Integer)}.
	 * <p>
	 * Each time a string is embedded it is broken down into tokens. This API
	 * limits the total number of tokens that can be used during a client's
	 * session to {@link #EMBED_TOKENS_LIMIT}. You can check the number of
	 * embedding tokens used this session with {@link #getEmbedTokensUsed()} and
	 * the number of embedding tokens remaining for this session with {@link
	 * #getEmbedTokensRemaining()}.
	 * 
	 * @param string the string to convert to a vector
	 * @return an array of floating point values corresponding to that string's
	 * position in the embedding's latent space
	 * @throws RuntimeException if this client has exceeded the number of tokens
	 * that can be used for text embedding
	 * @see #getEmbedTokensUsed()
	 * @see #getEmbedTokensRemaining()
	 * @see #getEmbedDimensions()
	 */
	public float[] embed(String string) {
		checkKey();
		// TODO Will this throw other exceptions?
		throw new UnsupportedOperationException("External API not yet implemented.");
	}
	
	/**
	 * Returns the number of input tokens used for {@link #complete(String...)
	 * text completion} so far during this session.
	 * 
	 * @return the number of input text completion tokens used
	 * @see #getCompleteInTokensRemaining()
	 * @see #complete(String...)
	 */
	public int getCompleteInTokensUsed() {
		return completeInTokensUsed;
	}
	
	/**
	 * Returns the number of input tokens that can be used for {@link
	 * #complete(String...) text completion} before {@link
	 * #COMPLETE_IN_TOKENS_LIMIT the limit} is exceeded.
	 * 
	 * @return the number of input tokens that can be used for text completion
	 * @see #getCompleteInTokensUsed()
	 * @see #complete(String...)
	 */
	public int getCompleteInTokensRemaining() {
		return Math.max(0, COMPLETE_IN_TOKENS_LIMIT - completeInTokensUsed);
	}
	
	/**
	 * Returns the number of output tokens used for {@link #complete(String...)
	 * text completion} so far during this session.
	 * 
	 * @return the number of output text completion tokens used
	 * @see #getCompleteOutTokensRemaining()
	 * @see #complete(String...)
	 */
	public int getCompleteOutTokensUsed() {
		return completeOutTokensUsed;
	}
	
	/**
	 * Returns the number of output tokens that can be used for {@link
	 * #complete(String...) text completion} before {@link
	 * #COMPLETE_OUT_TOKENS_LIMIT the limit} is exceeded.
	 * 
	 * @return the number of output tokens that can be used for text completion
	 * @see #getCompleteOutTokensUsed()
	 * @see #complete(String...)
	 */
	public int getCompleteOutTokensRemaining() {
		return Math.max(0, COMPLETE_OUT_TOKENS_LIMIT - completeOutTokensUsed);
	}
	
	/**
	 * Returns the maximum number of output tokens that can be used when
	 * reasoning and generating a single response during {@link
	 * #complete(String...) text completion}. A null value means this setting
	 * is not being used and the external API will decide how many tokens to
	 * use.
	 * <p>
	 * Reasoning models generate output tokens during the reasoning phase and
	 * before generating the text they will return. This limit covers both
	 * the tokens generating during reasoning and the actual output tokens.
	 * {@link #complete(String...)} may return an empty output if it uses all
	 * tokens during the reasoning phase.
	 * 
	 * @return the max tokens that can be used to generate a single response
	 * during text completion, or null if this setting is not being used
	 * @see #setCompleteMaxOutTokens(Integer)
	 * @see #complete(String...)
	 */
	public Integer getCompleteMaxOutTokens() {
		return completeMaxTokens;
	}
	
	/**
	 * Sets the maximum number of output tokens that can be used when reasoning
	 * and generating a single response during {@link #complete(String...) text
	 * completion}. A null value means this setting will not be used and the
	 * external API will decide how many tokens to use.
	 * 
	 * @param value the new max tokens that can be used to generate a single
	 * response during text completion, or null if this setting should not be
	 * used
	 * @throws IllegalArgumentException if the value is less than 1
	 * @see #getCompleteMaxOutTokens()
	 * @see #complete(String...)
	 */
	public void setCompleteMaxOutTokens(Integer value) {
		if(value != null)
			checkInteger("Max output tokens for text completion", value, 1, Integer.MAX_VALUE);
		completeMaxTokens = value;
	}
	
	/**
	 * Returns the {@link ReasoningEffort level of reasoning effort} the {@link
	 * #complete(String...) text completion} model will use when generating its
	 * response. A null value means this setting will not be used and the
	 * external API will decide what level of reasoning to use.
	 * 
	 * @return the level of reasoning done during text completion, or null if
	 * this setting is not being used
	 * @see #setCompleteReasoningEffort(ReasoningEffort)
	 * @see #complete(String...)
	 */
	public ReasoningEffort getCompleteReasoningEffort() {
		return completeReasoningEffort;
	}
	
	/**
	 * Sets the {@link ReasoningEffort level of reasoning effort} the {@link
	 * #complete(String...) text completion} model will use when generating its
	 * response. A null value means this setting should not be used and the
	 * external API will decide what level of reasoning to use.
	 * <p>
	 * Reasoning models generate more tokens to check and refine their
	 * responses. Lower reasoning uses fewer tokens, while higher reasoning may
	 * generate better quality responses.
	 * 
	 * @param value the new level of reasoning to do during text completion, or
	 * null if this setting should not be used
	 * @see #getCompleteReasoningEffort()
	 * @see #complete(String...)
	 */
	public void setCompleteReasoningEffort(ReasoningEffort value) {
		completeReasoningEffort = value;
	}
	
	/**
	 * Returns the temperature used during {@link #complete(String...) text
	 * completion}. A null value means this setting is not being used and the
	 * external API will decide what temperature to use.
	 * 
	 * @return the temperature used during text completion, or null if this
	 * setting is not being used
	 * @see #setCompleteTemperature(Float)
	 * @see #complete(String...)
	 */
	public Float getCompleteTemperature() {
		return completeTemperature;
	}
	
	/**
	 * Sets the temperature to be used during {@link #complete(String...) text
	 * completion}. A null value means this setting should not be used and the
	 * external API will decide what temperature to use.
	 * <p>
	 * Temperature affects the variety of the words used in the output. A low
	 * temperature will produce predictable and repeatable responses, whereas a
	 * high temperature will produce less predictable responses.
	 * 
	 * @param value the new temperature to use during text completion, or null
	 * if this setting should not be used
	 * @throws IllegalArgumentException if the value is less than 0.0 or more
	 * than 2.0
	 * @see #getCompleteTemperature()
	 * @see #complete(String...)
	 */
	public void setCompleteTemperature(Float value) {
		if(value != null)
			checkFloat("Temperature for text completion", value, 0, 2);
		completeTemperature = value;
	}
	
	/**
	 * Returns the top k setting used during {@link #complete(String...) text
	 * completion}. A null values means this setting is not used and the
	 * external API should decide how to choose its next tokens.
	 * 
	 * @return the top k setting used during text completion, or null if this
	 * setting is not being used
	 * @see #setCompleteTopK(Integer)
	 * @see #complete(String...)
	 */
	public Integer getCompleteTopK() {
		return completeTopK;
	}
	
	/**
	 * Sets the top k setting used during {@link #complete(String...) text
	 * completion}. A null values means this setting should not used and the
	 * external API should decide how to choose its next tokens.
	 * <p>
	 * When choosing its next output token during text generation, this setting
	 * forces the model to choose only among the k most likely tokens. A value
	 * of 1 means the model will always choose the most likely token. Higher
	 * values allow the model to choose from a wider variety of tokens. Not
	 * using this setting means the model can choose from all tokens.
	 * 
	 * @param value the new top k value to use during text completion, or null
	 * if this setting should not be used
	 * @throws IllegalArgumentException if the value is less than 1
	 * @see #getCompleteTopK()
	 * @see #complete(String...)
	 */
	public void setCompleteTopK(Integer value) {
		if(value != null)
			checkInteger("Top k", value, 1, Integer.MAX_VALUE);
		completeTopK = value;
	}
	
	/**
	 * Returns the top p setting used during {@link #complete(String...) text
	 * completion}. A null values means this setting is not used and the
	 * external API should decide how to choose its next tokens.
	 * 
	 * @return the top p setting used during text completion, or null if this
	 * setting is not being used
	 * @see #setCompleteTopP(Float)
	 * @see #complete(String...)
	 */
	public Float getCompleteTopP() {
		return completeTopP;
	}
	
	/**
	 * Sets the top p setting used during {@link #complete(String...) text
	 * completion}. A null values means this setting should not used and the
	 * external API should decide how to choose its next tokens.
	 * <p>
	 * When choosing its next output token during text generation, this setting
	 * forces the model to choose only among the most likely tokens whose
	 * likelihoods sum to p. A low value means the model will generate more
	 * predictable text, whereas a higher value means the model will generate
	 * less predictable text. Not using this setting means the model can choose
	 * from all tokens.
	 * 
	 * @param value the new top p value to use during text completion, or null
	 * if this setting should not be used
	 * @throws IllegalArgumentException if the value is less 0 or more than 1
	 * @see #getCompleteTopP()
	 * @see #complete(String...)
	 */
	public void setCompleteTopP(Float value) {
		if(value != null)
			checkFloat("Top p", value, 0, 1);
		completeTopP = value;
	}
	
	/**
	 * Returns the min p setting used during {@link #complete(String...) text
	 * completion}. A null values means this setting is not used and the
	 * external API should decide how to choose its next tokens.
	 * 
	 * @return the min p setting used during text completion, or null if this
	 * setting is not being used
	 * @see #setCompleteMinP(Float)
	 * @see #complete(String...)
	 */
	public Float getCompleteMinP() {
		return completeMinP;
	}
	
	/**
	 * Sets the min p setting used during {@link #complete(String...) text
	 * completion}. A null values means this setting should not used and the
	 * external API should decide how to choose its next tokens.
	 * <p>
	 * When choosing its next output token during text generation, this setting
	 * forces the model to ignore tokens whose likelihood is below a threshold.
	 * The threshold is the product of the likelihood of the best token and p.
	 * For example, if p is set to 0.1, then the model may only consider tokens
	 * whose likelihood is one tenth a likely as the best token. A low value
	 * means the model will have many tokens to choose from and will generate
	 * less predictable text, whereas a higher value means the model will have
	 * fewer tokens to choose from and will generate more predictable text. Not
	 * using this setting means the model can choose from all tokens.
	 * 
	 * @param value the new min p value to use during text completion, or null
	 * if this setting should not be used
	 * @throws IllegalArgumentException if the value is less 0 or more than 1
	 * @see #getCompleteMinP()
	 * @see #complete(String...)
	 */
	public void setCompleteMinP(Float value) {
		if(value != null)
			checkFloat("Min p", value, 0, 1);
		completeMinP = value;
	}
	
	/**
	 * Queries the external text completion API to generate text which is a
	 * fitting continuation of to a series of messages. If multiple messages are
	 * given as input, then message 0 is treated as the system prompt (the
	 * instructions to the text completion agent), odd numbered messages (1, 3,
	 * etc.) are treated as messages from the user, and event numbered messages
	 * (2, 4, etc.) are treated as responses from the agent. If only a single
	 * string is given as input it is treated as a message from the user and the
	 * text completion model will use its default system prompt.
	 * <p>
	 * Text completion works by breaking down the input text into tokens, called
	 * input tokens. The response text is generated one token at a time, called
	 * output tokens. When choosing the next token to add to the output, the
	 * model assigns a probability to every possible token and chooses the next
	 * token probabilistically based on the likelihood of each token. Several
	 * parameters can affect how the model chooses its next tokens:
	 * <ul>
	 * <li>Set the {@link #setCompleteReasoningEffort(ReasoningEffort) reasoning
	 * effort} to control how much additional text the model generates to verify
	 * and refined its output. Reasoning text is not returned as part of the
	 * response, but higher levels of reasoning should produce better responses.
	 * </li>
	 * <li>Set the {@link #setCompleteTemperature(Float) temperature} to control
	 * the level of variation in how output tokens are chosen. A low temperature
	 * means the model is more likely to choose the most likely tokens,
	 * resulting in more predictable output. A higher temperature results in
	 * less predictable responses.</li>
	 * <li>Set {@link #setCompleteTopK(Integer) top k} to force the model to
	 * choose only from the k more likely output tokens. A low value results in
	 * more predictable text. A high value results in less predictable text.
	 * </li>
	 * <li>Set {@link #setCompleteTopP(Float) top p} to force the mode to choose
	 * only from the most likely output tokens whose probabilities add up to p.
	 * A low value results in more predictable text. A higher value results in
	 * less predictable text.</li>
	 * <li>Set {@link #setCompleteMinP(Float) min p} to force the model to
	 * ignore tokens whose likelihood is lower than the likelihood of the best
	 * token times p. A low value results in less predictable text. A higher
	 * value results in more predictable text.</li>
	 * </ul>
	 * This API limits the total number of input and output tokens tokens that
	 * can be used during a client's session to {@link
	 * #COMPLETE_IN_TOKENS_LIMIT} and {@link #COMPLETE_OUT_TOKENS_LIMIT}
	 * respectively. You can check the number of input and output tokens used
	 * this session with {@link #getCompleteInTokensUsed()} and
	 * {@link #getCompleteOutTokensUsed()}. You can also limit how many output
	 * tokens a model uses by setting {@link #setCompleteMaxOutTokens(Integer)}.
	 * 
	 * @param conversation a series of messages
	 * @return text that is a fitting continuation of the input messages
	 * @throws RuntimeException if this client has exceeded the number of input
	 * or output tokens that can be used for text completion
	 * @see #getCompleteInTokensUsed()
	 * @see #getCompleteInTokensRemaining()
	 * @see #getCompleteOutTokensUsed()
	 * @see #getCompleteOutTokensRemaining()
	 * @see #setCompleteMaxOutTokens(Integer)
	 * @see #setCompleteTemperature(Float)
	 * @see #setCompleteReasoningEffort(ReasoningEffort)
	 * @see #setCompleteTopK(Integer)
	 * @see #setCompleteTopP(Float)
	 * @see #setCompleteMinP(Float)
	 */
	public String complete(String...conversation) {
		checkKey();
		// TODO Are other exceptions thrown?
		throw new UnsupportedOperationException("External API not yet implemented.");
	}
	
	private final void checkKey() {
		if(key == null)
			throw new IllegalStateException("This agent does not have an API key. It cannot use the external API.");
	}
	
	private final void checkInteger(String type, int value, int min, int max) {
		if(value < min)
			throw new IllegalArgumentException(type + " must be >= " + min + ".");
		else if(value > max)
			throw new IllegalArgumentException(type + " must be <= " + max + ".");
	}
	
	private final void checkFloat(String type, float value, float min, float max) {
		if(value < min)
			throw new IllegalArgumentException(type + " must be >= " + min + ".");
		else if(value > max)
			throw new IllegalArgumentException(type + " must be <= " + max + ".");
	}
}