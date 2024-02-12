import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.bitbucket.rfnetwork.rfppf.common.*;
import org.bitbucket.rfnetwork.rfppf.messages.MessageFactory;
import org.bitbucket.rfnetwork.rfppf.messages.gprs.GPRSMessage;
import org.bitbucket.rfnetwork.rfppf.messages.oldprotocol.*;
import org.bitbucket.rfnetwork.rfppf.messages.oldprotocol.ReceiverMessage.MessageTypes;
import org.bitbucket.rfnetwork.rfppf.parsers.GPRSParser;
import org.bitbucket.rfnetwork.rfppf.parsers.RFMeshParser;

public class GPRSServerOldProtocol extends IoHandlerAdapter implements IParser {

	public static final int LISTENING_PORT = 4009;
	
	final NioSocketAcceptor acceptor = new NioSocketAcceptor();
	final static Logger logger = (Logger)LogManager.getLogger(GPRSServerOldProtocol.class);
	private final Set<IoSession> sessions = Collections
            .synchronizedSet(new HashSet<IoSession>());
	
	public GPRSServerOldProtocol(int _port) throws IOException {
		acceptor.setHandler(this);
		acceptor.bind(new InetSocketAddress(_port));
		logger.info("TCP service startup, port:" + _port);
	}
	
	@Override
	public void sessionCreated(IoSession iosession) throws Exception {
		super.sessionCreated(iosession);
	}
	
	/**
	 * Client connected event handler
	 */
	@Override
	public void sessionOpened(IoSession iosession) throws Exception {
		super.sessionOpened(iosession);
		logger.info("Session created");
		GPRSParser parser = new GPRSParser();
		parser.addListener(this);
		parser.SetInfo(iosession);
		iosession.setAttribute("Parser", parser);
	}
	
	/**
	 * Handle incoming message from client event handler
	 */
	@Override
	public void messageReceived(IoSession session, Object message)
			throws Exception {
		IoBuffer bbuf = (IoBuffer) message;
		byte[] byten = new byte[bbuf.limit()];
		bbuf.get(byten, bbuf.position(), bbuf.limit());
		ProtocolParser parser = (ProtocolParser)session.getAttribute("Parser");
		logger.debug("-> " + RFPPFHelper.ByteArrayToHexString(byten));
		try
		{
			parser.AppendBytes(byten);
		}
		catch (Exception ex)
		{
			// Catch parsing exception
			logger.warn(String.format("Parsing exception: %s", ex.toString()));
		}
	}
	
	/**
	 * Client disconnected event handler
	 */
	@Override
	public void sessionClosed(IoSession session) throws Exception {
		logger.info("session closed");
		Object parser = session.getAttribute("Parser");
		parser = null;
		session.setAttribute("Parser", parser);
		sessions.remove(session);
	}
	
	/**
	 * Client exception event handler
	 */
	@Override
	public void exceptionCaught(IoSession session, Throwable cause)
			throws Exception {
		logger.info(String.format("session exception: %s", cause.toString()));
		super.exceptionCaught(session, cause);
	}
	
	public void SendToClient(IoSession client, byte[] msg) {
		logger.debug("<- " + RFPPFHelper.ByteArrayToHexString(msg));
		client.write(IoBuffer.wrap(msg));
	}
	
	public void updateClientIMEI(IoSession client, long imei) {
        if (client == null || imei < 1 || client.getAttribute("IMEI") != null && (long)client.getAttribute("IMEI") == imei)
            return;
        logger.info(String.format("Connection to %s IMEI updated %d", client.getRemoteAddress().toString(), imei));
        client.setAttribute("IMEI", imei);
    }
	
	/**
     * Send message to specific gateway
     *
     * @param imei Gateway IMEI
     * @param msg  Message bytes
     */
    public void SendMessageToClient(long imei, GPRSMessage msg) {
    	synchronized (sessions) {
    		for (IoSession session : sessions) {
    			if (session.isConnected() && session.getAttribute("IMEI") != null && (long)session.getAttribute("IMEI") == imei) {
    				SendToClient(session, msg.GetBytes());
    			}
    		}
    	}
    }
	
	public static void main(String[] argv) throws IOException {
		new GPRSServerOldProtocol(LISTENING_PORT);
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
			ReceiverMessage rcvMessage = null;
			
			// Handle received message
			try {
				rcvMessage = (ReceiverMessage)MessageFactory.ParseReceiverMessage(receivedGPRSMessage.getBody());
			} catch (Exception ex)  {
				rcvMessage = null;
				logger.warn(String.format("Can't handle received message. %s", ex.getMessage()));
			}
			
			if (rcvMessage != null && !(rcvMessage instanceof ReceiverAcknowledgeMessage)) {
				// Because GPRS protocol requires acknowledgments. Send acknowledge message if not acknowledge.
	            GPRSMessage ack = GPRSMessage.GenerateGPRSAcknowledgeMessage(receivedGPRSMessage.getMessageNumber());
	            SendToClient((IoSession)parser.GetInfo(), ack.GetBytes());
			}
			// Update connection IMEI for sending commands out of parser
			updateClientIMEI((IoSession)parser.GetInfo(), receivedGPRSMessage.getMainReceiverID());
            
            // Handle received message			
			// Print receiver details such as IMEI and Address
			logger.info(String.format("GPRS Message # %d, IMEI: %d, Address: %d, Inner message type: %s", receivedGPRSMessage.getMessageNumber(), receivedGPRSMessage.getMainReceiverID(), receivedGPRSMessage.getReceiverID(), rcvMessage.getClass().toString()));
            if (rcvMessage.getMessageType() == MessageTypes.TagDataMessages) {
            	// Tag data message
            	for (TagMessage tm : ((TagDataMessage)rcvMessage).getTagMessages()) {
            		// Handle tag messages according its type
//            		if (tm instanceof TagTemperatureMessage) {
//            			// Temperature message
//            			TagTemperatureMessage tempMsg = (TagTemperatureMessage)tm;
//            			logger.info(String.format("Tag ID %d, Temperature: %.2f C", tempMsg.getTagID(), tempMsg.getTemperature()));
//            		}
//            		else if (tm instanceof TagPeriodicMessage)
//            		{
//            			// Periodic message
//            			TagPeriodicMessage periodicMsg = (TagPeriodicMessage)tm;
//            			
//            			logger.info(String.format("Tag ID %d, Voltage: %.2f V", periodicMsg.getTagID(), periodicMsg.getVoltage()));
//            		}
//            		else if (tm instanceof TagCounterMessage)
//            		{
//            			// Counter
//            			TagCounterMessage counterMsg = (TagCounterMessage)tm;
//            			logger.info(String.format("Tag ID %d, Counter: %d, Voltage: %.2f V", counterMsg.getTagID(), counterMsg.getCounter(), counterMsg.getVoltage()));
//            		}
//            		else if (tm instanceof TagActivateMessage)
//            		{
//            			// Activate message
//            			TagActivateMessage activateMsg = (TagActivateMessage)tm;
//            			logger.info(String.format("Tag ID %d, Activator: %d", activateMsg.getTagID(), activateMsg.getActivator()));
//	            	}
//            		else if (tm instanceof TagPressureMessage)
//            		{
//            			// Pressure message
//            			TagPressureMessage pressureMsg = (TagPressureMessage)tm;
//            			logger.info(String.format("Tag ID %d, Pressure: %.2f, Humidity: %.2f, Temperature: %.2f", pressureMsg.getTagID(), pressureMsg.getPressure(), pressureMsg.getHumidity(), pressureMsg.getTemperature()));
//		            }
//            		else if (tm instanceof TagTemperatureRHMessage)
//            		{
//            			// Temperature RH message
//            			TagTemperatureRHMessage temperatureRHMsg = (TagTemperatureRHMessage)tm;
//            			logger.info(String.format("Tag ID %d, Temperature: %.2f, Humidity: %.2f, Voltage: %.2f", temperatureRHMsg.getTagID(), temperatureRHMsg.getTemperature(), temperatureRHMsg.getHumidity(), temperatureRHMsg.getVoltage()));
//   			    }
            		
            		//// Additional messages. Use as shown before
                    //// TagPushButtonGPIO9Message
                    //// TagAngleCalibrationMessage
                    //// TagAgroMessage
                    //// TagMovementMessage
                    //// TagAngleCalibrationRecalibrationMessage
                    //// TagMovementAverageMessage
                    //// TagPushButtonGPIO8Message
                    //// TagDistanceMessage
                    //// TagAnalogMessage
                    //// TagNoMovementMessage
                    //// TagAnalogCounterMessage
                    //// TagAngleMessage
                    //// TagIButtonMessage
                    //// TagMagnetDetectionGPIO8Message
                    //// TagProductionMessage
					//// TagAveragePulsesPerSecondMessage
            		// Print message details by using ToString() method
            		logger.info(tm.toString());
            	}
            } else if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.Transient) {
            	// Transient
            	byte[] transientData = ((ReceiverTransientMessage)rcvMessage).getTransientData();

            } else if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.PowerAlert) {
            	// Receiver power alert. Is sent from the gateway when external power is connected or disconnected
                // Note: Available only on gateways with the inner battery
                ReceiverPowerAlertMessage pam = (ReceiverPowerAlertMessage)rcvMessage;
                logger.info(String.format("Gateway (IMEI: %d) External power %s", receivedGPRSMessage.getMainReceiverID(), pam.getExternalPowerConnected() ? "connected" : "disconnected"));		
            } else if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.ModemVoltage) {
            	// Modem voltage. Actually sends voltage measured inside GPRS modem module
                ReceiverModemVoltageMessage mv = (ReceiverModemVoltageMessage)rcvMessage;
                logger.info(String.format("Gateway (IMEI: %d) Modem voltage message: %s", receivedGPRSMessage.getMainReceiverID(), mv.toString()));		
            } else if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.ReceiverMessage) {
            	// Receiver configuration message.
            	
                ReceiverConfigurationMessage rcm = (ReceiverConfigurationMessage)rcvMessage;
                if (rcvMessage instanceof ReceiverConfigurationVoltageMessage)
                {
                	ReceiverConfigurationVoltageMessage cvm = (ReceiverConfigurationVoltageMessage)rcvMessage;
                	logger.info(String.format("Gateway (IMEI: %d) Radio module voltage message: %s", receivedGPRSMessage.getMainReceiverID(), cvm.toString()));		
                }
                else if (rcvMessage instanceof ReceiverConfigurationExternalVoltageMessage)
                {
                	ReceiverConfigurationExternalVoltageMessage cevm = (ReceiverConfigurationExternalVoltageMessage)rcvMessage;
                	logger.info(String.format("Gateway (IMEI: %d) External voltage message: %s", receivedGPRSMessage.getMainReceiverID(), cevm.toString()));		
                }
                else if (rcvMessage instanceof ReceiverConfigurationTemperatureMessage)
                {
                	ReceiverConfigurationTemperatureMessage ctm = (ReceiverConfigurationTemperatureMessage)rcvMessage;
                	logger.info(String.format("Gateway (IMEI: %d) Radio module temperature message: %s", receivedGPRSMessage.getMainReceiverID(), ctm.toString()));		
                }
                else 
                {
                	logger.info(String.format("Gateway (IMEI: %d) Radio module configuration message: %s", receivedGPRSMessage.getMainReceiverID(), rcm.toString()));		
                }
            } else if (rcvMessage.getMessageType() == MessageTypes.Weight) {
            	// Weight message
            	ReceiverWeightMessage rwm = (ReceiverWeightMessage)rcvMessage;
            	
            	// To get weight use rwm.getWeight()
            	
            	logger.info(rwm.toString());
            } else {
            	logger.info(rcvMessage.toString());
            }
		} else if (parser instanceof RFMeshParser) {
			// RF-Networks Mesh protocol (New Protocol) message parsed
			logger.info(msg.toString());
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
		logger.info(String.format("Parsing error: %s", msg.toString()));
	}
}
