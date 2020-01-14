import java.io.IOException;

import server.ConnectionToClient;
import server.ObservableServer;

import org.bitbucket.rfnetwork.rfppf.common.*;
import org.bitbucket.rfnetwork.rfppf.messages.MessageFactory;
import org.bitbucket.rfnetwork.rfppf.messages.gprs.GPRSMessage;
import org.bitbucket.rfnetwork.rfppf.messages.oldprotocol.*;
import org.bitbucket.rfnetwork.rfppf.messages.oldprotocol.ReceiverMessage.MessageTypes;
import org.bitbucket.rfnetwork.rfppf.parsers.GPRSParser;
import org.bitbucket.rfnetwork.rfppf.parsers.RFMeshParser;

public class GPRSServerOldProtocol extends ObservableServer implements IParser {

	public static final int LISTENING_PORT = 4109;
	
	public GPRSServerOldProtocol(int _port) {
		super(_port);
	}
	
	/**
	 * Client connected event handler
	 */
	protected void clientConnected(ConnectionToClient connToClient) {
		System.out.println(String.format("Client connected (%s)", connToClient.getInetAddress().toString()));
		GPRSParser parser = new GPRSParser();
		parser.SetInfo(connToClient);
		parser.addListener(this);
		connToClient.setInfo("Parser", parser);
		
		RFMeshParser parser1 = new RFMeshParser();
		parser1.SetInfo(connToClient);
		parser1.addListener(this);
		connToClient.setInfo("Parser1", parser1);
	}
	
	/**
	 * Client disconnected event handler
	 */
	protected synchronized void clientDisconnected(ConnectionToClient client) {
		System.out.println(String.format("Client disconnected (%s)", client.getName()));
		Object parser = client.getInfo("Parser");
		parser = null;
		client.setInfo("Parser", parser);
		
		parser = client.getInfo("Parser1");
		parser = null;
		client.setInfo("Parser1", parser);
	}
	
	/**
	 * Listening exception event handler
	 */
	protected void listeningException(Throwable exception) {
		System.out.println(String.format("Listening exception: %s", exception.getMessage()));
	}

	/**
	 * Client exception event handler
	 */
	protected synchronized void clientException(ConnectionToClient client, Throwable exception) {
		System.out.println(String.format("[%s] Client exception", client.getName()));
	}
	
	/**
	 * Server stopped event handler
	 */
	protected void serverStopped() {
		System.out.println("Server stopped");
	}
	
	/**
	 * Server closed event handler
	 */
	protected void serverClosed() {
		System.out.println("[%s] Server closed");
	}
	
	/**
	 * Server started event handler
	 */
	protected void serverStarted() {
		System.out.println(String.format("Server started (Port %d).", this.getPort()));
	}
	
	/**
	 * Handle incoming message from client event handler
	 */
	protected synchronized void handleMessageFromClient(byte[] message,
			ConnectionToClient client) {
		ProtocolParser parser = (ProtocolParser) client.getInfo("Parser");
		//System.out.println(String.format("Data received: %s", RFPPFHelper.ByteArrayToHexString(message, true)));
		parser.AppendBytes(message);
	}
	
	public static void main(String[] argv) {
		GPRSServerOldProtocol server = new GPRSServerOldProtocol(LISTENING_PORT);
		try {
			server.listen();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void MessageReceived(ProtocolParser parser, ProtocolMessage msg) {
		// Message was successfully parsed
		if (parser instanceof GPRSParser) {
			// GPRS parser
			GPRSMessage receivedGPRSMessage = (GPRSMessage)msg;
			
			// Because GPRS protocol requires acknowledgments. Send acknowledge message.
            GPRSMessage ack = GPRSMessage.GenerateGPRSAcknowledgeMessage(receivedGPRSMessage.getMessageNumber());
            try {
				((ConnectionToClient)parser.GetInfo()).sendToClient(ack.GetBytes());
			} catch (IOException e) {
				System.out.println(String.format("Can't send acknowledge. %s", e.getMessage()));
			}
            
            
            // Handle received message
			try {
				ReceiverMessage rcvMessage = (ReceiverMessage)MessageFactory.ParseReceiverMessage(receivedGPRSMessage.getBody());
				
				// Print receiver details such as IMEI and Address
	            System.out.println(String.format("GPRS Message # %d, IMEI: %d, Address: %d, Inner message type: %s", receivedGPRSMessage.getMessageNumber(), receivedGPRSMessage.getMainReceiverID(), receivedGPRSMessage.getReceiverID(), rcvMessage.getClass().toString()));
	            if (rcvMessage.getMessageType() == MessageTypes.TagDataMessages) {
	            	// Tag data message
	            	for (TagMessage tm : ((TagDataMessage)rcvMessage).getTagMessages()) {
	            		// Handle tag messages according its type
//	            		if (tm instanceof TagTemperatureMessage) {
//	            			// Temperature message
//	            			TagTemperatureMessage tempMsg = (TagTemperatureMessage)tm;
//	            			System.out.println(String.format("Tag ID %d, Temperature: %.2f C", tempMsg.getTagID(), tempMsg.getTemperature()));
//	            		}
	            		// Print message details by using ToString() method
	            		System.out.println(tm.toString());
	            	}
	            } else if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.Transient) {
	            	// Transient
	            	byte[] transientData = ((ReceiverTransientMessage)rcvMessage).getTransientData();
	            	if ((transientData != null) && (transientData.length > 0) && (transientData[0] == 0x96)) {
	            		// New protocol message, pass it to another parser
	            		ProtocolParser p = (ProtocolParser)((ConnectionToClient)parser.GetInfo()).getInfo("Parser1");
	            		if (p != null) {
	            			// We need to encode transient data using Bit-Stuffing algorithm
	            			p.AppendBytes(RFPPFHelper.BitStuffEncode(transientData));
	            		}
	            	} else {
	            		System.out.println(RFPPFHelper.ByteArrayToHexString(((ReceiverTransientMessage)rcvMessage).getTransientData()));
	            	}
	            } else {
	            	System.out.println(rcvMessage.toString());
	            }
			} catch (Exception e) {
				System.out.println(String.format("Can't handle received message. %s", e.getMessage()));
			}
		} else if (parser instanceof RFMeshParser) {
			// RF-Networks Mesh protocol (New Protocol) message parsed
			System.out.println(msg.toString());
		}
		
		// To send SMS via GPRS gateway use following code
        // Change <Phone Number> to phone number string
        // Change <Message> to message string
		// client is an instance of ConnectionToClient (in this case - client = ((ConnectionToClient)parser.GetInfo() )
		//GPRSMessage smsMsg = GPRSMessage.GenerateSendSMSMessage(<Phone Number>, <Message>);
		//client.sendToClient(smsMsg.GetBytes());
		
		// To set register on mesh tag use following code
        // Change <DeviceID> to required tag ID
        // Change <RegisterNumber> to required register number
        // Change <RegisterValue> to required register value
		// client is an instance of ConnectionToClient (in this case - client = ((ConnectionToClient)parser.GetInfo() )
		//SetRegisterRequestMessageBuilder setRegMsgBuilder = new SetRegisterRequestMessageBuilder();
		//setRegMsgBuilder.Version = 1;
		//setRegMsgBuilder.DeviceID = <DeviceID>;
		//setRegMsgBuilder.MessageNumber = 0;
		//setRegMsgBuilder.RegisterNumber = <RegisterNumber>;
		//setRegMsgBuilder.RegisterValue = <RegisterValue>;
		//SetRegisterRequestMessage setRegMsg = (SetRegisterRequestMessage)MessageFactory.CreateMessage(SetRegisterRequestMessage.class, setRegMsgBuilder);
		//GPRSMessage transientMsg = GPRSMessage.GenerateTransientMessage(setRegMsg.GetBytes());
		//client.sendToClient(transientMsg.GetBytes());
	}

	@Override
	public void MessageError(ProtocolParser parser, ProtocolMessage msg) {
		// Parsing message failure
		System.out.println(String.format("Parsing error: %s", msg.toString()));
	}
}
