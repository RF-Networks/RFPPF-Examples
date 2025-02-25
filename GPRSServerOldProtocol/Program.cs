﻿using Hik.Communication.Scs.Communication.EndPoints.Tcp;
using Hik.Communication.Scs.Communication.Messages;
using Hik.Communication.Scs.Server;
using RFPPF.Common;
using RFPPF.Messages;
using RFPPF.Messages.GPRS;
using RFPPF.Messages.GPRS.Gateway;
using RFPPF.Messages.OldProtocol;
using RFPPF.Parsers;
using System;
using System.Collections.Generic;

namespace GPRSServerOldProtocol
{
    class Program
    {
        public const int LISTENING_PORT = 4009;

        public static Dictionary<long, ClientInfo> infos = new Dictionary<long, ClientInfo>();

        public class ClientInfo
        {
            public ProtocolParser parser;
            public ProtocolParser new_parser;
        }

        static void Main(string[] args)
        {
            IScsServer server = ScsServerFactory.CreateServer(new ScsTcpEndPoint(LISTENING_PORT));
            server.WireProtocolFactory = new MyWireProtocolFactory();
            server.ClientConnected += Server_ClientConnected;
            server.ClientDisconnected += Server_ClientDisconnected;

            server.Start(); //Start the server

            Console.WriteLine("Server is started successfully. Press enter to stop...");
            Console.ReadLine(); //Wait user to press enter

            // Disconnect all connected client
            foreach (IScsServerClient client in server.Clients.GetAllItems())
            {
                client.Disconnect();
            }
            server.Stop(); //Stop the server
        }

        private static void Server_ClientDisconnected(object sender, ServerClientEventArgs e)
        {
            e.Client.MessageReceived -= Client_MessageReceived;

            ClientInfo info = infos[e.Client.ClientId];

            if (info != null)
            {
                // Unregister event handlers
                info.parser.MessageReceived -= Parser_MessageReceived;
                info.parser.MessageError -= Parser_MessageError;
            }

            infos.Remove(e.Client.ClientId);

            Console.WriteLine("Client disconnected " + e.Client.ClientId.ToString());
        }

        private static void Server_ClientConnected(object sender, ServerClientEventArgs e)
        {
            e.Client.MessageReceived += Client_MessageReceived;

            ClientInfo info = new ClientInfo()
            {
                parser = new GPRSParser(),
                new_parser = new RFMeshParser()
            };

            // Register event handlers
            info.parser.Info = e.Client;
            info.parser.MessageReceived += Parser_MessageReceived;
            info.parser.MessageError += Parser_MessageError;

            info.new_parser.Info = e.Client;
            info.new_parser.MessageReceived += NewParser_MessageReceived;
            info.new_parser.MessageError += NewParser_MessageError;

            infos.Add(e.Client.ClientId, info);
            Console.WriteLine("Client connected " + e.Client.ClientId.ToString());
        }

        private static void Parser_MessageError(object sender, ProtocolEventArgs e)
        {
            // Parsing message failure
            Console.WriteLine(string.Format("Parsing error: {0}", e.Message.ToString()));
        }

        private static void Parser_MessageReceived(object sender, ProtocolEventArgs e)
        {
            // Message was successfully parsed
            GPRSMessage receivedGPRSMessage = (e.Message as GPRSMessage);
            GatewayMessage gatewayMessage = receivedGPRSMessage.GatewayMessage;
            if (receivedGPRSMessage != null && ((receivedGPRSMessage.GatewayMessage == null) ||
                (receivedGPRSMessage.GatewayMessage != null
                && receivedGPRSMessage.GatewayMessage.MessageType != GatewayMessage.MessageTypes.Acknowledge
                && receivedGPRSMessage.GatewayMessage.MessageType != GatewayMessage.MessageTypes.WhoIAm)))
            {
                // Because GPRS protocol requires acknowledgments. Send acknowledge message.
                GPRSMessage ack = GPRSMessage.GenerateGPRSAcknowledgeMessage(receivedGPRSMessage.MessageNumber);
                ((sender as GPRSParser).Info as IScsServerClient).SendMessage(new ScsRawDataMessage(ack.Bytes));
            }
            // Handle received message
            ReceiverMessage rcvMessage = null;
            try
            {
                rcvMessage = MessageFactory.ParseReceiverMessage(receivedGPRSMessage.Body) as ReceiverMessage;
            }
            catch
            {

            }
            
            if (gatewayMessage != null)
            {
                // Gateway messages
                switch (gatewayMessage.MessageType)
                {
                    case GatewayMessage.MessageTypes.Acknowledge:
                        Console.WriteLine(string.Format("Gateway (IMEI: {0}) Acknowledge message: {1}", receivedGPRSMessage.MainReceiverID, gatewayMessage.ToString()));
                        break;
                    case GatewayMessage.MessageTypes.WhoIAm:
                        Console.WriteLine(string.Format("Gateway (IMEI: {0}) WhoIAm message: {1}", receivedGPRSMessage.MainReceiverID, gatewayMessage.ToString()));
                        break;
                    default:
                        Console.WriteLine(string.Format("GPRS Message # {0}, IMEI: {1}, Address: {2}, Message type: {3}", receivedGPRSMessage.MessageNumber, receivedGPRSMessage.MainReceiverID, receivedGPRSMessage.ReceiverID, gatewayMessage.MessageType));
                        break;
                }
                return;
            }
            else
            {
                // Print receiver details such as IMEI and Address
                Console.WriteLine(string.Format("GPRS Message # {0}, IMEI: {1}, Address: {2}, Inner message type: {3}", receivedGPRSMessage.MessageNumber, receivedGPRSMessage.MainReceiverID, receivedGPRSMessage.ReceiverID, rcvMessage.GetType().ToString()));
            }
            if (rcvMessage.MessageType == ReceiverMessage.MessageTypes.TagDataMessages)
            {
                foreach (TagMessage tm in (rcvMessage as TagDataMessage).TagMessages)
                {
                    // Handle tag messages according its type
                    //if (tm.GetType() == typeof(TagTemperatureMessage))
                    //{
                    //    // Temperature message
                    //    TagTemperatureMessage tempMsg = tm as TagTemperatureMessage;
                    //    Console.WriteLine(string.Format("Tag ID {0}, Temperature: {1:0.00}", tempMsg.TagID, tempMsg.Temperature));
                    //}
                    //else if (tm.GetType() == typeof(TagPeriodicMessage))
                    //{
                    //    // Periodic message
                    //    TagPeriodicMessage periodicMsg = tm as TagPeriodicMessage;

                    //    Console.WriteLine(string.Format("Tag ID {0}, Voltage: {1:0.00}", periodicMsg.TagID, periodicMsg.Voltage));
                    //}
                    //else if (tm.GetType() == typeof(TagCounterMessage))
                    //{
                    //    // Counter
                    //    TagCounterMessage counterMsg = tm as TagCounterMessage;
                    //    Console.WriteLine(string.Format("Tag ID {0}, Counter: {1}, Voltage: {2:0.00}", counterMsg.TagID, counterMsg.Counter, counterMsg.Voltage));
                    //}
                    //else if (tm.GetType() == typeof(TagActivateMessage))
                    //{
                    //    // Activate message
                    //    TagActivateMessage activateMsg = tm as TagActivateMessage;

                    //    Console.WriteLine(string.Format("Tag ID {0}, Activator: {1}", activateMsg.TagID, activateMsg.Activator));
                    //}
                    //else if (tm.GetType() == typeof(TagPressureMessage))
                    //{
                    //    // Pressure message
                    //    TagPressureMessage pressureMsg = tm as TagPressureMessage;

                    //    Console.WriteLine(string.Format("Tag ID {0}, Pressure: {1:0.00}, Humidity: {2:0.00}, Temperature: {3:0.00}", pressureMsg.TagID, pressureMsg.Pressure, pressureMsg.Humidity, pressureMsg.Temperature));
                    //}
                    //else if (tm.GetType() == typeof(TagTemperatureRHMessage))
                    //{
                    //    // Temperature RH message
                    //    TagTemperatureRHMessage temperatureRHMsg = tm as TagTemperatureRHMessage;

                    //    Console.WriteLine(string.Format("Tag ID {0}, Temperature: {1:0.00}, Humidity: {2:0.00}, Voltage: {3:0.00}", temperatureRHMsg.TagID, temperatureRHMsg.Temperature, temperatureRHMsg.Humidity, temperatureRHMsg.Voltage));
                    //}

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
                    Console.WriteLine(string.Format("{0} - {1}", receivedGPRSMessage.Timestamp, tm.ToString()));
                }
            }
            else if (rcvMessage.MessageType == ReceiverMessage.MessageTypes.Transient)
            {
                byte[] transientData = (rcvMessage as ReceiverTransientMessage).TransientData;

                if ((transientData != null) && (transientData.Length > 0) && (transientData[0] == 0x96))
                {
                    // New protocol message
                    ClientInfo info = infos[((sender as GPRSParser).Info as IScsServerClient).ClientId];
                    if (info != null && info.new_parser != null)
                        info.new_parser.AppendBytes(RFPPFHelper.BitStuffEncode(transientData));
                }
                else
                    Console.WriteLine(RFPPFHelper.ByteArrayToHexString((rcvMessage as ReceiverTransientMessage).TransientData));
            }
            else if (rcvMessage.MessageType == ReceiverMessage.MessageTypes.PowerAlert)
            {
                // Receiver power alert. Is sent from the gateway when external power is connected or disconnected
                // Note: Available only on gateways with the inner battery
                ReceiverPowerAlertMessage pam = rcvMessage as ReceiverPowerAlertMessage;
                Console.WriteLine(string.Format("Gateway (IMEI: {0}) External power {1}", receivedGPRSMessage.MainReceiverID, pam.ExternalPowerConnected? "connected" : "disconnected"));
            }
            else if (rcvMessage.MessageType == ReceiverMessage.MessageTypes.ModemVoltage)
            {
                // Modem voltage. Actually sends voltage mesured inside GPRS modem module
                ReceiverModemVoltageMessage mv = rcvMessage as ReceiverModemVoltageMessage;
                Console.WriteLine(string.Format("Gateway (IMEI: {0}) Modem voltage message: {1}", receivedGPRSMessage.MainReceiverID, mv.ToString()));
            }
            else if (rcvMessage.MessageType == ReceiverMessage.MessageTypes.ReceiverMessage)
            {
                // Receiver configuration message.
                ReceiverConfigurationMessage rcm = rcvMessage as ReceiverConfigurationMessage;

                if (rcvMessage.GetType() == typeof(ReceiverConfigurationVoltageMessage))
                {
                    ReceiverConfigurationVoltageMessage cvm = rcvMessage as ReceiverConfigurationVoltageMessage;
                    Console.WriteLine(string.Format("Gateway (IMEI: {0}) Radio module voltage message: {1}", receivedGPRSMessage.MainReceiverID, cvm.ToString()));
                }
                else if (rcvMessage.GetType() == typeof(ReceiverConfigurationExternalVoltageMessage))
                {
                    ReceiverConfigurationExternalVoltageMessage cevm = rcvMessage as ReceiverConfigurationExternalVoltageMessage;
                    Console.WriteLine(string.Format("Gateway (IMEI: {0}) External voltage message: {1}", receivedGPRSMessage.MainReceiverID, cevm.ToString()));
                }
                else if (rcvMessage.GetType() == typeof(ReceiverConfigurationTemperatureMessage))
                {
                    ReceiverConfigurationTemperatureMessage ctm = rcvMessage as ReceiverConfigurationTemperatureMessage;
                    Console.WriteLine(string.Format("Gateway (IMEI: {0}) Radio module temperature message: {1}", receivedGPRSMessage.MainReceiverID, ctm.ToString()));
                }
                else
                {
                    Console.WriteLine(string.Format("Gateway (IMEI: {0}) Radio module configuration message: {1}", receivedGPRSMessage.MainReceiverID, rcm.ToString()));
                }               
            }
            else if (rcvMessage.MessageType == ReceiverMessage.MessageTypes.ModbusMeter)
            {
                ReceiverModbusMeterMessage mbmsg = rcvMessage as ReceiverModbusMeterMessage;

                Console.WriteLine(mbmsg.ToString());
                Console.WriteLine(RFPPFHelper.ByteArrayToHexString(mbmsg.Bytes));
            }
            else
            {
                Console.WriteLine(rcvMessage.ToString());
            }

            // To send SMS via GPRS gateway use following code
            // Change <Phone Number> to phone number string
            // Change <Message> to message string
            // client is an instance of IScsServerClient (in this case - client = ((sender as GPRSParser).Info as IScsServerClient) )
            // 
            //GPRSMessage smsMsg = GPRSMessage.GenerateSendSMSMessage(<Phone Number>, <Message>);
            //client.SendMessage(new ScsRawDataMessage(smsMsg.Bytes));

            // To set register on mesh tag use following code
            // Change <DeviceID> to required tag ID
            // Change <RegisterNumber> to required register number
            // Change <RegisterValue> to required register value
            // client is an instance of IScsServerClient (in this case - client = ((sender as GPRSParser).Info as IScsServerClient) )
            //RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessage getRegMsg = RFPPF.Messages.MessageFactory.CreateMessage(typeof(RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessage), new RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessageBuilder()
            //{
            //    Version = 1,
            //    DeviceID = <DeviceID>,
            //    MessageNumber = 0,
            //    RegisterNumber = <RegisterNumber>,
            //    RegisterValue = <RegisterValue>
            //}) as RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessage;
            //GPRSMessage transientMsg = GPRSMessage.GenerateTransientMessage(getRegMsg.Bytes);
            //client.SendMessage(new ScsRawDataMessage(transientMsg.Bytes));
        }

        private static void NewParser_MessageError(object sender, ProtocolEventArgs e)
        {
            // Parsing message failure
            Console.WriteLine(string.Format("Parsing error: {0}", e.Message.ToString()));
        }

        private static void NewParser_MessageReceived(object sender, ProtocolEventArgs e)
        {
            // RF-Networks Mesh protocol (New Protocol) message parsed
            Console.WriteLine(e.Message.ToString());
        }

        private static void Client_MessageReceived(object sender, MessageEventArgs e)
        {
            // Pass received data to the parser
            IScsServerClient client = sender as IScsServerClient;
            ScsRawDataMessage message = e.Message as ScsRawDataMessage;
            if (client != null && message != null && message.MessageData != null)
            {
                ClientInfo info = infos[client.ClientId];
                if (info != null)
                {
                    try
                    {
                        info.parser.AppendBytes(message.MessageData);
                    }
                    catch (Exception ex)
                    {
                        // Catch parsing error in order not to close connection to client
                        Console.WriteLine(ex.ToString());
                        Console.WriteLine(string.Format("Received message: {0}", RFPPFHelper.ByteArrayToHexString(message.MessageData)));
                    }
                }
            }
        }
    }
}
