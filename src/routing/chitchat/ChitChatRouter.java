package routing.chitchat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import routing.ActiveRouter;
import routing.MessageRouter;
import routing.chitchat.decay.AbstractDecay;
//import util.ETACalculator;
import routing.util.IncompleteCSV;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;

public class ChitChatRouter extends ActiveRouter {

	private static final String VARIABLE_TTL_KWD = "VARIABLE_TTL";

	/** ChitChat router's setting namespace ({@value}) */
	public static final String CHITCHAT_NS = "ChitChatRouter";

	/**
	 * Social interests are the unique keywords and phrases that describe the the
	 * social profile of the person carrying a mobile device. This is a required
	 * setting in the configuration file.
	 * 
	 * The Social Interests file must be of the format
	 * 
	 * NODE_ID;SOCIAL_INTEREST_KEY
	 */
	public static final String SOCIAL_INTERESTS_S = "interests";

	/**
	 * If a potential receiver is rejected from receiving a message due to
	 * insufficient social characteristics, then this value is returned.
	 */
	public static final int DENIED_SOCIAL_CHARACTERISTICS = -98;

	/**
	 * Each mobile node has social patterns. When a mobile node u meets another
	 * mobile node v, the social interests of v induce social patterns in u.
	 * Likewise, if a mobile node v has social patterns, then these social patterns
	 * also induce social patterns in u.
	 * 
	 * The strength of a particular social pattern decays when it is no longer
	 * induced by other nodes. The model of their decay is specified in the
	 * configuration file.
	 */
	private static final String SOCIALPATTERNDECAY_NS = CHITCHAT_NS + ".SocialPatternDecay";
	private static final String SOCIAL_PATTERN_DECAY_CLASS_S = "class";
	private static final String CHITCHATDECAY_PACKAGE = "routing.chitchat.decay.";

	/**
	 * Messages forwarded through the ChitChat router require metadata descriptors.
	 * These are keywords that describe the topic of the message. For instance, a
	 * photograph of a cat would have "cat" as a metadata descriptor.
	 * 
	 * If a collection of messages was constructed for input into the simulation,
	 * then a metadata descriptor file may also be constructed to describe the
	 * content of the messages. This file must be in the format
	 * 
	 * MESSAGE_ID;METADATA_KEYWORD
	 * 
	 * This is an optional setting in the configuration file. If no file is
	 * specified in the configuration file, then the metadata descriptors of a
	 * particular message are defined as the Social Interests of the destination
	 * node.
	 */
	private static final String MESSAGE_METADATA_DESCRIPTORS_PROPERTY_KEY = "chitchat_mdd";
	private static final String MESSAGE_METADATA_FILE_S = "messageMetadataFile";

	private static String messageMetadataFileURL = "";

	protected SocialCharacteristics characteristics;

	private static String socialInterestsFileURL;

	private static final String SOCIAL_INTEREST_SPACE_DIMENSIONS_S = "socialInterestSpaceDimensions";
	private static final String SOCIAL_INTERESTS_PER_ROUTER_S = "socialInterestsPerRouter";
	private static int socialInterestSpaceDimensions;
	private static int socialInterestsPerRouter;
	private static int mesRedToSelfishNodes = 0;
	public static double IN_MAX = 2.0;

	// private static ETACalculator eta;

	@Override
	public void init(DTNHost host, List<MessageListener> mListeners) {
		// if (eta == null) {
		// eta = new ETACalculator();
		// }

		super.init(host, mListeners);
		Settings chitchatSettings = new Settings(ChitChatRouter.CHITCHAT_NS);

		// If no routers have been initialized (i.e. the program just started, and this
		// router
		// is the first to be initialized), then begin loading up some settings from the
		// configuration file.
		if (noRoutersInitialized()) {
			// If the configuration file does not specify a metadata descriptor
			// file, then the URL is set to NULL to indicate that each message
			// should use the destination's social interests as the metadata
			// descriptor for the corresponding message.
			messageMetadataFileURL = chitchatSettings.getSetting(MESSAGE_METADATA_FILE_S, null);
			socialInterestsFileURL = chitchatSettings.getSetting(SOCIAL_INTERESTS_S, null);
			socialInterestSpaceDimensions = chitchatSettings.getInt(SOCIAL_INTEREST_SPACE_DIMENSIONS_S, 0);
			socialInterestsPerRouter = chitchatSettings.getInt(SOCIAL_INTERESTS_PER_ROUTER_S, 0);

			// If the user specified a social interest file, which associates social
			// interests to an individual router,
			// then the social interests do not need to be synthesized. However, if no
			// social interest file was
			// specified, then we need to synthesize the "space" from which social interests
			// are picked.
			if (socialInterestsFileURL == null) {
				SocialInterests.generateSpace(socialInterestSpaceDimensions);
			}
		}

		// Retrieve the social interests for this particular mobile device.
		SocialInterests i = null;
		i = new SocialInterests(host.interests);
		if (socialInterestsFileURL == null) {
			i = SocialInterests.buildFromInterestSpace(socialInterestsPerRouter);
		} else {
			i = SocialInterests.buildFromSettingsFile(socialInterestsFileURL, host.getAddress());
		}

		// Initialize the Social characteristics, originally setting the social
		// interests for this particular node.
		characteristics = new SocialCharacteristics(i);

		// Based on the settings file, set the decay model that will be used
		// by social patterns.
		Settings socialPatternSettings = new Settings(ChitChatRouter.SOCIALPATTERNDECAY_NS);
		String socialPatternDecayClassName = socialPatternSettings.getSetting(SOCIAL_PATTERN_DECAY_CLASS_S,
				"LogisticDecay");
		characteristics.getSocialPatterns().setDecayModel((AbstractDecay) socialPatternSettings
				.createIntializedObject(CHITCHATDECAY_PACKAGE + socialPatternDecayClassName));
	}

	private static boolean noRoutersInitialized() {
		return messageMetadataFileURL != null && messageMetadataFileURL.isEmpty();
	}

	/**
	 * Checks out all sending connections to finalize the ready ones and abort those
	 * whose connection went down. Also drops messages whose TTL <= 0 (checking
	 * every one simulated minute).
	 * 
	 * @see #addToSendingConnections(Connection)
	 */
	@Override
	public void update() {
		// eta.iteration_marker(SimClock.getIntTime());
		// eta.ping("update");

		super.update();

		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();

		// eta.pong("update");
	}

	/**
	 * Called when a connection's state changes. If the connection is up, then a
	 * growth event should occur for both nodes.
	 * 
	 * @param @con
	 *            The connection whose state changed
	 */
	@Override
	public void changedConnection(Connection con) {
		// eta.iteration_marker(SimClock.getIntTime());

		super.changedConnection(con);
		DTNHost other = con.getOtherNode(getHost());
		if (con.isUp()) {
			// While two nodes are still connected with one another, growth should
			// occur between the two nodes' shared interests and patterns.
			// TODO Note that this may be a performance bottleneck, as patterns will change
			// at every second, and thus that information will need to be wirelessly
			// communicated
			// between the two devices at every second. This is not favorable.
			// Look into alternatives for this chitchatting whenever data analysis is
			// performed.
			// For instance, when a connection is first established, save the social pattern
			// details from the other guy and continuously reference this local data to
			// update
			// the patterns of this node.
			// Or look into pushing updates.
			// While two nodes u and v are connected, can the social patterns of v change to
			// include
			// new social patterns that must also be transmitted to u?
			// eta.ping("changedConnectionUp");

			SocialCharacteristics c = ((ChitChatRouter) other.getRouter()).getCharacteristics();
			// System.out.println("=========================================");
			// System.out.printf("Node #%d:%d patterns\n", getHost().getAddress(),
			// characteristics.getStringInfo());

			// Since a new connection has been established between two nodes,
			// induction of social interests and patterns needs to take place.
			characteristics.connect(c, other.getAddress());
			////////////////////////////////// System.out.printf("Connection between node
			////////////////////////////////// #%d and #%d is up\n", getHost().getAddress(),
			////////////////////////////////// con.getOtherNode(getHost()).getAddress());
			// System.out.printf("Node #%d:%d patterns\n", getHost().getAddress(),
			////////////////////////////////// characteristics.getStringInfo());
			// eta.pong("changedConnectionUp");

		} else {
			// eta.ping("changedConnectionDown");
			// System.out.println("=========================================");
			// System.out.printf("Node #%d:%d patterns\n", getHost().getAddress(),
			// characteristics.getStringInfo());
			characteristics.disconnect(other.getAddress());
			///////////////////////////////////// System.out.printf("Connection between node
			///////////////////////////////////// #%d and #%d is down\n",
			///////////////////////////////////// getHost().getAddress(),
			///////////////////////////////////// con.getOtherNode(getHost()).getAddress());
			// System.out.printf("Node #%d:%d patterns\n", getHost().getAddress(),
			///////////////////////////////////// characteristics.getStringInfo());
			// eta.pong("changedConnectionDown");
		}
	}

	/**
	 * Tries to start a transfer of message using a connection. If the other node is
	 * a good candidate based on the content strength of both nodes, then the
	 * transfer will be allowed forward.
	 * 
	 * @param m
	 *            The message to transfer
	 * @param con
	 *            The connection to use
	 * @return the value returned by
	 *         {@link Connection#startTransfer(DTNHost, Message)}
	 */
	@Override
	protected int startTransfer(Message m, Connection con) {
		// eta.iteration_marker(SimClock.getIntTime());
		// eta.ping("startTransfer");
		///////////////////////////////////////////////////////////////// System.out.printf("Considering
		///////////////////////////////////////////////////////////////// msg %s
		///////////////////////////////////////////////////////////////// (#%d->#%d)
		///////////////////////////////////////////////////////////////// from node #%d
		///////////////////////////////////////////////////////////////// to node
		///////////////////////////////////////////////////////////////// #%d\n",
		///////////////////////////////////////////////////////////////// m.getId(),
		///////////////////////////////////////////////////////////////// m.getFrom().getAddress(),
		///////////////////////////////////////////////////////////////// m.getTo().getAddress(),
		///////////////////////////////////////////////////////////////// getHost().getAddress(),
		///////////////////////////////////////////////////////////////// con.getOtherNode(getHost()).getAddress());
		/*
		 * if(m.getFrom().getSelfishFlag()==1) { return DENIED_SOCIAL_CHARACTERISTICS; }
		 */
		if (otherNodeIsAGoodCandidate(m, con)) {
			// eta.pong("startTransfer");
			//System.out.println("Transfer to be started!");
			return super.startTransfer(m, con);
		}

		// eta.pong("startTransfer");
		return DENIED_SOCIAL_CHARACTERISTICS;
	}

	private boolean otherNodeIsAGoodCandidate(Message m, Connection con) {

		DTNHost other = con.getOtherNode(getHost());

		int randomNum = 0;
		if (con.getFromNode().getSelfishFlag() == 1) {
			randomNum = ThreadLocalRandom.current().nextInt(0, 2);
		}
		if (randomNum == 1)
			return false;
		// If the other node is the destination host, return true by default.
		if (other.getAddress() == m.getTo().getAddress()) {
			return true;
		} else {
			// Collection<String> metadata_descriptors = (Collection<String>)
			// m.getProperty(MESSAGE_METADATA_DESCRIPTORS_PROPERTY_KEY);
			ArrayList<String> metadata_descriptors = m.tags;
			// System.out.printf("Node #%d content strength:\n", getHost().getAddress());
			double u_content_strength = getCharacteristics().getContentStrength(metadata_descriptors);

			SocialCharacteristics otherSC = ((ChitChatRouter) other.getRouter()).getCharacteristics();
			// System.out.printf("Node #%d content strength:\n", other.getAddress());
			double v_content_strength = otherSC.getContentStrength(metadata_descriptors);

			// System.out.println(u_content_strength <= v_content_strength ? "Message will
			// be forwarded" : "No message forwarding");
			// System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++");
			return u_content_strength < v_content_strength;
		}
	}

	//Logic here should be if TO node in connection object is the destination, 
	//check if the TO node has enough incentives to pay for the message
	
	private boolean otherNodeIsAGoodCandidate(Message m, Connection con, int flag) {
		DTNHost other = con.getOtherNode(getHost());
		
		//This randomNumber is to determine if a selfish node
		//will have bluetooth on or off while transferring the message
		int randomNum = 0;
		if (con.getFromNode().getSelfishFlag() == 1) {
			randomNum = ThreadLocalRandom.current().nextInt(0, 2);
		}
		//If random Number == 0 ==> the from node is either non-selfish OR
		//the from node is selfish but keeps the bluetooth on
		
		if (randomNum == 0) {
			////Following codition-- TO node in connection object is a destination node
			
			if (other.getAddress() == m.getTo().getAddress()) {
				
				double incent = 0.0;
				//If the incentive promise is not there means the transfer is from source to destination directly
				if (m.getIncentivePromise() != 0.0) {
					incent = m.getIncentivePromise();
				} else {
					//Incentive promise is not there, therefore calculate it and assign it
					incent = (0.5 * m.sizePercent + 0.5 * m.qualityPercent) * IN_MAX;
				}
				
				//This condition checks if the destination node has enough incentive to pay for the message
				if (other.getIncentive() >= incent) {
					
					//If rating for the to node is possessed, modify incentive reward value
					if (other.hostRatings.get(m.getFrom()) != null) {
						double percentVal = 0.0;

						double ratingForSource = other.hostRatings.get(m.getFrom());
						
						if (ratingForSource < 4.0 && ratingForSource >= 3.0) {
							
							incent = 0.75 * incent;
						}
						if (ratingForSource < 3.0 && ratingForSource >= 2.0) {
							
							incent = 0.50 * incent;
						}
						if (ratingForSource < 2.0) {
							
							incent = 0;
						}
					}
					
					//Following lines are to transfer the incentive reward from dest to transferring node
					
					other.setIncentive(other.getIncentive() - incent);
					m.getFrom().setIncentive(m.getFrom().getIncentive() + incent);

					return true;
				} else {
					//The node does not have enough incentive to pay for the message, so increase the value of 
					//reduced messages!
					
					incrementReducedMessagesToSelfish();
					return false;
				}

			}
			
			////Condition that node is relay node
			else {
				double incent = (0.5 * m.sizePercent + 0.5 * m.qualityPercent) * IN_MAX;
				if (other.hostRatings.get(m.getFrom()) != null) {
					double ratingForSource = other.hostRatings.get(m.getFrom());
					if (ratingForSource < 4.0 && ratingForSource >= 3.0)
						incent = 0.75 * incent;
					if (ratingForSource < 3.0 && ratingForSource >= 2.0)
						incent = 0.50 * incent;
					if (ratingForSource < 2.0)
						incent = 0;
				}
				m.setIncentivePromise(incent);
				
				if (con.getFromNode().rank > other.rank) {
					return true;
				}
				ArrayList<String> metadata_descriptors = m.tags;
				
				double u_content_strength = getCharacteristics().getContentStrength(metadata_descriptors);
				
				SocialCharacteristics otherSC = ((ChitChatRouter) other.getRouter()).getCharacteristics();
				
				double v_content_strength = otherSC.getContentStrength(metadata_descriptors);
				
				//Only major condition to be satisfied is from CHitChat
				return u_content_strength < v_content_strength;
			}
		} else {
			return false;
		}
	}

	/**
	 * When a new message is created, we must populate with message with the
	 * metadata descriptors that describe the contents of the message.
	 */
	@Override
	public boolean createNewMessage(Message m) {
		// eta.iteration_marker(SimClock.getIntTime());
		// eta.ping("createNewMessage");
		//////////////////////////////////// System.out.printf("Message #%s has been
		//////////////////////////////////// newly created!\n", m.getId());

		// This is where we will load the message's metadata descriptors from
		// an external file.
		if (null == m.getProperty(MESSAGE_METADATA_DESCRIPTORS_PROPERTY_KEY)) {
			// The message is newly created, and its metadata descriptors needs
			// to be populated from the specified external file.
			//////////////////////// System.out.printf("Message #%s has been newly
			// created!\n", m.getId());

			Collection<String> descriptors = loadMessageMetadataDescriptors(m);
			// System.out.println(descriptors);
			m.addProperty(MESSAGE_METADATA_DESCRIPTORS_PROPERTY_KEY, descriptors);

			///////////////////////////////////////////////////////////////////
			// We also need to add a field for variable TTL
			///////////////////////////////////////////////////////////////////
			m.addProperty(VARIABLE_TTL_KWD, m.getTtl());
		}

		// eta.pong("createNewMessage");
		return super.createNewMessage(m);
	}

	/**
	 * This shit is super experimental!
	 */

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		// eta.iteration_marker(SimClock.getIntTime());
		// eta.ping("messageTransferred");

		Message m = super.messageTransferred(id, from);
		////////////////////////// System.out.printf("Msg #%s successfully transferred
		////////////////////////// from node #%d to node #%d\n", id,
		////////////////////////// getHost().getAddress(), from.getAddress());
		/*
		 * For some stupid reason, I cannot get the initTtl value from a message.
		 * Instead of doing the naive thing and creating a getter for that value, I'll
		 * do some math to get it.
		 * 
		 * 0+~--|-------------------|-----------|-----------> created now() initTTL
		 * 
		 * |-----------| getTtl() = initTtl - (now() - getCreationTime())
		 * |-------------------| now() - getCreationTime()
		 * 
		 * |-------------------------------| initTtl = getTtl() + (now() -
		 * getCreationTime())
		 * 
		 * If I want to shrink the initTtl value, then I can multiply initTtl by some
		 * factor 0 <= x <= 1. Then floor it.
		 */
		/*
		 * double initTtl = m.getTtl() + (SimClock.getTime() - m.getCreationTime());
		 * 
		 * @SuppressWarnings("unchecked") double contentStrength =
		 * getCharacteristics().getContentStrength( (Collection<String>) m.getProperty(
		 * MESSAGE_METADATA_DESCRIPTORS_PROPERTY_KEY )); int reducedTtl = (int)
		 * Math.floor(initTtl * contentStrength); m.setTtl(reducedTtl);
		 */

		// eta.pong("messageTransferred");
		return m;
	}

	/*
	 * Load the message metadata descriptors for the given file.
	 * 
	 * If there is no file describing the metadata descriptors, then set the
	 * metadata descriptors of the message to the social interests of the receiver.
	 */
	private Collection<String> loadMessageMetadataDescriptors(Message m) {
		if (messageMetadataFileURL == null) {
			return ((ChitChatRouter) m.getTo().getRouter()).getCharacteristics().getSocialInterests().keys();
		}
		return IncompleteCSV.load_from_file(m.getId(), messageMetadataFileURL);
	}

	public ChitChatRouter(Settings s) {
		super(s);
	}

	public ChitChatRouter(ActiveRouter r) {
		super(r);
	}

	@Override
	public MessageRouter replicate() {
		return new ChitChatRouter(this);
	}

	public SocialCharacteristics getCharacteristics() {
		return characteristics;
	}
}
