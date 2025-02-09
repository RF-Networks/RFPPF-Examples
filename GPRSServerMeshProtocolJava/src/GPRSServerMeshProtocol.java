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
import org.bitbucket.rfnetwork.rfppf.messages.gprs.gateway.GatewayMessage;
import org.bitbucket.rfnetwork.rfppf.messages.gprs.gateway.GatewayMessage.MessageTypes;
import org.bitbucket.rfnetwork.rfppf.messages.oldprotocol.*;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.RFMMessage;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.data.DataRawInMessage;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.diagnostic.DiagnosticGetShortAddressMessage;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.event.CheckInEventV2Message;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.event.CheckOutEventMessage;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.event.SchedulerExecutedEventV2Message;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.scheduler.GetSchedulerRequestV2Message;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.scheduler.GetSchedulerRequestV2MessageBuilder;
import org.bitbucket.rfnetwork.rfppf.messages.rfm.scheduler.SetSchedulerRequestV2MessageBuilder;
import org.bitbucket.rfnetwork.rfppf.parsers.GPRSParser;
import org.bitbucket.rfnetwork.rfppf.parsers.RFMeshParser;

public class GPRSServerMeshProtocol extends IoHandlerAdapter implements IParser {

	public static final int LISTENING_PORT = 4009;
	
	final NioSocketAcceptor acceptor = new NioSocketAcceptor();
	final static Logger logger = (Logger)LogManager.getLogger(GPRSServerMeshProtocol.class);
	private final Set<IoSession> sessions = Collections
            .synchronizedSet(new HashSet<IoSession>());
	
	public GPRSServerMeshProtocol(int _port) throws IOException {
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
    
        // Close previous sessions
        synchronized (sessions) {
    		for (IoSession session : sessions) {
    			if (client != session && 
    					session.isConnected() && 
    					session.getAttribute("IMEI") != null 
    					&& (long)session.getAttribute("IMEI") == imei) {
    				session.closeNow();
    			}
    		}
    	}

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
		new GPRSServerMeshProtocol(LISTENING_PORT);
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
			
			if ((receivedGPRSMessage != null) && ((receivedGPRSMessage.getGatewayMessage() == null) ||
					(receivedGPRSMessage.getGatewayMessage() != null &&
					receivedGPRSMessage.getGatewayMessage().getMessageType() != MessageTypes.Acknowledge &&
					receivedGPRSMessage.getGatewayMessage().getMessageType() != MessageTypes.WhoIAm)))
			{
				// Because GPRS protocol requires acknowledgments. Send acknowledge message.
	            GPRSMessage ack = GPRSMessage.GenerateGPRSAcknowledgeMessage(receivedGPRSMessage.getMessageNumber());
	            SendToClient((IoSession)parser.GetInfo(), ack.GetBytes());
			}
            // Update connection IMEI for sending commands out of parser
 			updateClientIMEI((IoSession)parser.GetInfo(), receivedGPRSMessage.getMainReceiverID());
         			
            // Handle received message
            ReceiverMessage rcvMessage = null;
            RFMMessage meshMessage = null;
			GatewayMessage gatewayMessage = null;
            try
            {
            	gatewayMessage = receivedGPRSMessage.getGatewayMessage();
            	if (gatewayMessage == null) 
            	{
            		meshMessage = (RFMMessage)MessageFactory.ParseMeshMessage(receivedGPRSMessage.getBody());
            	}
            } catch(Exception ex) {
            	try
            	{
            		meshMessage = null;
            		rcvMessage = (ReceiverMessage)MessageFactory.ParseReceiverMessage(receivedGPRSMessage.getBody());
            	} catch (Exception e) {
            		logger.warn(String.format("Can't handle received message. %s", e.getMessage()));
            	}
			}
            
            
            if (gatewayMessage != null) {
            	// Gateway message
            	switch (gatewayMessage.getMessageType())
            	{
            	case Acknowledge:
            		logger.info(String.format("Gateway (IMEI: %d) Acknowledge message: %s", receivedGPRSMessage.getMainReceiverID(), gatewayMessage.toString()));
            		break;
            	case WhoIAm:
            		logger.info(String.format("Gateway (IMEI: %d) WhoIAm message: %s", receivedGPRSMessage.getMainReceiverID(), gatewayMessage.toString()));
            		break;
            	default:
            		logger.info(String.format("GPRS Message # %d, IMEI: %ld, Address: %ld, Message type: %s", receivedGPRSMessage.getMessageNumber(), receivedGPRSMessage.getMainReceiverID(), receivedGPRSMessage.getReceiverID(), gatewayMessage.getMessageType().toString()));
            		break;
            	}
            } else if (meshMessage != null) {
            	// Mesh protocol
            	//logger.info(String.format("GPRS Message # %d, IMEI: %d, Address: %d", receivedGPRSMessage.getMessageNumber(), receivedGPRSMessage.getMainReceiverID(), receivedGPRSMessage.getReceiverID()));
            	
            	// !!! Here you can implement your custom logics according to message type.
            	//logger.info(meshMessage.toString());
            	
            	if (meshMessage instanceof DataRawInMessage) {
            		DataRawInMessage dataRawMessage = (DataRawInMessage)meshMessage;
            	}
            	else
            	{
            		logger.info(meshMessage.toString());
            	}
            	
            	// Or handle message according its type as follows
            	if (meshMessage instanceof DiagnosticGetShortAddressMessage) {
            		DiagnosticGetShortAddressMessage diagnosticGetShorAddressMessage = (DiagnosticGetShortAddressMessage)meshMessage;
            		//logger.info(String.format("Tag %d DiagnosticGetShortAddressMessage", diagnosticGetShorAddressMessage.getDeviceID()));
            	}
            	
            	if (meshMessage instanceof CheckInEventV2Message)
            	{
            		CheckInEventV2Message checkInMessage = (CheckInEventV2Message)meshMessage;
            		
            		//checkInMessage.getDeviceSDLA() // Device ID
            		//checkInMessage.getParentDeviceSDLA() // Parent Device ID
            		//checkInMessage.getParentRSSI() // How devices sees it's parent
            		//checkInMessage.getRSSI() // How parent sees this device
            		//checkInMessage.getVoltage() // Battery voltage
            		//checkInMessage.getIsLowPower() // Low power mode
            		//checkInMessage.getPosition() // Position in network cycle
            		//checkInMessage.getSWType() // Software type
            		//checkInMessage.getVersionMaj() // Major version
            		//checkInMessage.getVersionMin() // Minor version
            		//checkInMessage.getVersionBuild() // Build version
            		//checkInMessage.getTS() // Timestamp
            	}
            	
            	if (meshMessage instanceof CheckOutEventMessage)
            	{
            		CheckOutEventMessage checkOutMessage = (CheckOutEventMessage)meshMessage;
            		
            		//checkOutMessage.getDeviceSDLA() // Device 
            		//checkOutMessage.getTS() // Timestamp
            	}
            	
            	if (meshMessage instanceof SchedulerExecutedEventV2Message)
            	{
            		SchedulerExecutedEventV2Message schedulerExecutedEventMessage = (SchedulerExecutedEventV2Message)meshMessage;
            	}
            	
//            	SetSchedulerRequestV2MessageBuilder schedulerRequestMessageBuilder = new SetSchedulerRequestV2MessageBuilder();
//            	schedulerRequestMessageBuilder.Version = 2;
//            	//schedulerRequestMessageBuilder.MessageNumber = <Message Number>;
//            	//schedulerRequestMessageBuilder.DeviceID = <Device ID>;
//            	//schedulerRequestMessageBuilder.SchedulerNumber = <Scheduler Number>;
//            	//schedulerRequestMessageBuilder.SchedulerNumberOfExecutions = <Number of executions>;
//            	//schedulerRequestMessageBuilder.SchedulerPeriod = <Period for periodic executions>;
//            	//schedulerRequestMessageBuilder.SchedulerOperation1Time = <First operation start time (Open)>; // 0x41 - first 6 bit mask, last 6 bit gpio, in your case only GPIO 1
//            	//schedulerRequestMessageBuilder.SchedulerOperation2Time = <Second operations start time (Close)>; // 0x40 - as previous command, but GPIO 1 is set to 0 (0x40 is mask, 0x00 is value)
//            	// +-----------------------------------+-----------------------------------+
//            	// |                MASK               |			     VALUE
//            	// +-----------------------------------+-----------------------------------+
//            	// |  6  |  5  |  4  |  3  |  2  |  1  |  6  |  5  |  4  |  3  |  2  |  1  |
//            	// +-----------------------------------+-----------------------------------+
//            	// |  0  |  0  |  0  |  0  |  0  |  1  |  0  |  0  |  0  |  0  |  0  |  1  |  = 0x41
//            	// +-----------------------------------+-----------------------------------+
//            	// |  0  |  0  |  0  |  0  |  0  |  1  |  0  |  0  |  0  |  0  |  0  |  0  |  = 0x40
//            	// +-----------------------------------+-----------------------------------+
//            	//schedulerRequestMessageBuilder.Command1 = <First operation command (Open)>;
//            	//schedulerRequestMessageBuilder.Command2 = <Second operation command (Close)>;
//            	SetSchedulerRequestV2Message shedulerRequest = (SetSchedulerRequestV2Message)MessageFactory.CreateMessage(SetSchedulerRequestV2Message.class, schedulerRequestMessageBuilder);
            	
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
	            logger.info(String.format("GPRS Message # %d, IMEI: %d, Address: %d, Inner message type: %s", receivedGPRSMessage.getMessageNumber(), receivedGPRSMessage.getMainReceiverID(), receivedGPRSMessage.getReceiverID(), rcvMessage.getClass().toString()));
	            if (rcvMessage.getMessageType() == ReceiverMessage.MessageTypes.PowerAlert) {
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
	            } else {
	            	logger.info(rcvMessage.toString());
	            }
            }
		} else if (parser instanceof RFMeshParser) {
			// RF-Networks Mesh protocol (New Protocol) message parsed
			logger.info(msg.getClass().toString());
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
