import java.io.IOException;

import server.ConnectionToClient;
import server.ObservableServer;

import org.bitbucket.rfnetwork.rfppf.common.*;
import org.bitbucket.rfnetwork.rfppf.messages.MessageFactory;
import org.bitbucket.rfnetwork.rfppf.messages.gprs.GPRSMessage;
import org.bitbucket.rfnetwork.rfppf.messages.oldprotocol.*;
import org.bitbucket.rfnetwork.rfppf.messages.oldprotocol.ReceiverMessage.MessageTypes;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.RFMMessage;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.diagnostic.DiagnosticGetShortAddressMessage;
import org.bitbucket.rfnetwork.rfppf.parsers.GPRSParser;
import org.bitbucket.rfnetwork.rfppf.parsers.RFMeshParser;

public class GPRSServerMeshProtocol extends ObservableServer implements IParser {

	public static final int LISTENING_PORT = 4109;
	
	public GPRSServerMeshProtocol(int _port) {
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
		try {
			parser.AppendBytes(message);
		}
		catch (Exception ex)
		{
			// Catch parsing exception
			System.out.println(String.format("Parsing exception: %s", ex.toString()));
			System.out.println(String.format("Data received: %s", RFPPFHelper.ByteArrayToHexString(message, true)));
		}
	}
	
	public static void main(String[] argv) {
		GPRSServerMeshProtocol server = new GPRSServerMeshProtocol(LISTENING_PORT);
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
            ReceiverMessage rcvMessage = null;
            RFMMessage meshMessage = null;
            try
            {
            	meshMessage = (RFMMessage)MessageFactory.ParseMeshMessage(receivedGPRSMessage.getBody());
            } catch(Exception ex) {
            	try
            	{
            		meshMessage = null;
            		rcvMessage = (ReceiverMessage)MessageFactory.ParseReceiverMessage(receivedGPRSMessage.getBody());
            	} catch (Exception e) {
            		System.out.println(String.format("Can't handle received message. %s", e.getMessage()));
            	}
			}
            
            if (meshMessage != null) {
            	// Mesh protocol
            	System.out.println(String.format("GPRS Message # %d, IMEI: %d, Address: %d", receivedGPRSMessage.getMessageNumber(), receivedGPRSMessage.getMainReceiverID(), receivedGPRSMessage.getReceiverID()));
            	
            	// !!! Here you can implement your custom logics according to message type.
            	System.out.println(meshMessage.toString());
            	
            	// Or handle message according its type as follows
            	if (meshMessage instanceof DiagnosticGetShortAddressMessage) {
            		DiagnosticGetShortAddressMessage diagnosticGetShorAddressMessage = (DiagnosticGetShortAddressMessage)meshMessage;
            		//System.out.println(String.format("Tag %d DiagnosticGetShortAddressMessage", diagnosticGetShorAddressMessage.getDeviceID()));
            	}
            	
            	//// Additional messages. Use as shown before:
                // GetRegisterRequestMessage
                // GetRegisterResponseMessage
                // SetRegisterRequestMessage
                // SetRegisterResponseMessage
                // GetTimeRequestMessage
                // GetTimeResponseMessage    
                // TesterCommandMessage
                // AssociationRequestEventMessage
                // CheckInEventMessage
                // DataInMessage
                // DataRawInMessage
                // DataOutMessage
                // DataSensorMessage
                // ErrorEventMessage

                //// Sensor messages:
                // GPIOSensorMessage
                // AccelerometerSensorMessage
                // AnalogSensorMessage
                // BME280SensorMessage
                // DS18B20SensorMessage
                // SI7021SensorMessage
            	
            }  else if (rcvMessage != null) {
            	// Old protocol receiver message
            	
            	// Print receiver details such as IMEI and Address
	            System.out.println(String.format("GPRS Message # %d, IMEI: %d, Address: %d, Inner message type: %s", receivedGPRSMessage.getMessageNumber(), receivedGPRSMessage.getMainReceiverID(), receivedGPRSMessage.getReceiverID(), rcvMessage.getClass().toString()));
	            if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.PowerAlert) {
	            	// Receiver power alert. Is sent from the gateway when external power is connected or disconnected
	                // Note: Available only on gateways with the inner battery
	                ReceiverPowerAlertMessage pam = (ReceiverPowerAlertMessage)rcvMessage;
	                System.out.println(String.format("Gateway (IMEI: %d) External power %s", receivedGPRSMessage.getMainReceiverID(), pam.getExternalPowerConnected() ? "connected" : "disconnected"));		
	            } else if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.ModemVoltage) {
	            	// Modem voltage. Actually sends voltage measured inside GPRS modem module
	                ReceiverModemVoltageMessage mv = (ReceiverModemVoltageMessage)rcvMessage;
	                System.out.println(String.format("Gateway (IMEI: %d) Modem voltage message: %s", receivedGPRSMessage.getMainReceiverID(), mv.toString()));		
	            } else if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.ReceiverMessage) {
	            	// Receiver configuration message.
	            	
	                ReceiverConfigurationMessage rcm = (ReceiverConfigurationMessage)rcvMessage;
	                if (rcvMessage instanceof ReceiverConfigurationVoltageMessage)
	                {
	                	ReceiverConfigurationVoltageMessage cvm = (ReceiverConfigurationVoltageMessage)rcvMessage;
	                	System.out.println(String.format("Gateway (IMEI: %d) Radio module voltage message: %s", receivedGPRSMessage.getMainReceiverID(), cvm.toString()));		
	                }
	                else if (rcvMessage instanceof ReceiverConfigurationExternalVoltageMessage)
	                {
	                	ReceiverConfigurationExternalVoltageMessage cevm = (ReceiverConfigurationExternalVoltageMessage)rcvMessage;
	                	System.out.println(String.format("Gateway (IMEI: %d) External voltage message: %s", receivedGPRSMessage.getMainReceiverID(), cevm.toString()));		
	                }
	                else if (rcvMessage instanceof ReceiverConfigurationTemperatureMessage)
	                {
	                	ReceiverConfigurationTemperatureMessage ctm = (ReceiverConfigurationTemperatureMessage)rcvMessage;
	                	System.out.println(String.format("Gateway (IMEI: %d) Radio module temperature message: %s", receivedGPRSMessage.getMainReceiverID(), ctm.toString()));		
	                }
	                else 
	                {
	                	System.out.println(String.format("Gateway (IMEI: %d) Radio module configuration message: %s", receivedGPRSMessage.getMainReceiverID(), rcm.toString()));		
	                }
	            } else {
	            	System.out.println(rcvMessage.toString());
	            }
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
