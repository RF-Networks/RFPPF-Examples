using Hik.Communication.Scs.Communication.EndPoints.Tcp;
using Hik.Communication.Scs.Communication.Messages;
using Hik.Communication.Scs.Server;
using RFPPF.Common;
using RFPPF.Messages;
using RFPPF.Messages.GPRS;
using RFPPF.Messages.GPRS.Gateway;
using RFPPF.Messages.OldProtocol;
using RFPPF.Messages.RFM;
using RFPPF.Messages.RFM.Configuration;
using RFPPF.Messages.RFM.Data;
using RFPPF.Messages.RFM.Data.SensorMessages;
using RFPPF.Messages.RFM.Diagnostic;
using RFPPF.Messages.RFM.Event;
using RFPPF.Parsers;
using System;
using System.Collections.Generic;
using System.Net.Http.Headers;
using System.Numerics;
using System.Text;

namespace GPRSServerMeshProtocol
{
    class Program
    {
        public const int LISTENING_PORT = 4008;

        public static Dictionary<long, ClientInfo> infos = new Dictionary<long, ClientInfo>();

        public static System.Timers.Timer timer;

        public static IScsServer server;

        public class ClientInfo
        {
            public ProtocolParser parser;
        }

        static void Main(string[] args)
        {
            server = ScsServerFactory.CreateServer(new ScsTcpEndPoint(LISTENING_PORT));
            server.WireProtocolFactory = new MyWireProtocolFactory();
            server.ClientConnected += Server_ClientConnected;
            server.ClientDisconnected += Server_ClientDisconnected;

            timer = new System.Timers.Timer(TimeSpan.FromMinutes(1).TotalMilliseconds);
            timer.AutoReset = true;
            timer.Elapsed += TimerElapsed;

            server.Start(); //Start the server
            timer.Start();

            Console.WriteLine("Server is started successfully. Press enter to stop...");
            Console.ReadLine(); //Wait user to press enter

            // Disconnect all connected client
            foreach (IScsServerClient client in server.Clients.GetAllItems())
            {
                client.Disconnect();
            }
            server.Stop(); //Stop the server
        }

        private static void TimerElapsed(object sender, System.Timers.ElapsedEventArgs e)
        {
            ushort _outMsgNum = 0;
            //foreach (KeyValuePair<long, ClientInfo> info in infos)
            //{
            //    if (info.Value != null)
            //    {
            //        info.Value.parser.
            //    }
            //}
            foreach (IScsServerClient client in server.Clients.GetAllItems())
            {
                ClientInfo info = infos[client.ClientId];
                if (info != null)
                {
                    GPRSMessage gprsMessage = GPRSMessage.GenerateGatewayRPCRequestMessage(new BigInteger(Guid.NewGuid().ToByteArray()), Encoding.ASCII.GetBytes("Signal"));
                    gprsMessage.Version = 1;
                    gprsMessage.PayloadType = 1;
                    gprsMessage.MessageNumber = _outMsgNum++;
                    gprsMessage.Timestamp = DateTime.UtcNow;
                    gprsMessage.RSSI = 0xFF;

                    client.SendMessage(new ScsRawDataMessage(gprsMessage.Bytes));
                }
            }
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
                parser = new GPRSParser()
            };

            // Register event handlers
            info.parser.Info = e.Client;
            info.parser.MessageReceived += Parser_MessageReceived;
            info.parser.MessageError += Parser_MessageError;

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
            ReceiverMessage rcvMessage = null;
            RFMMessage meshMessage = null;
            GatewayMessage gatewayMessage = null;

            // Message was successfully parsed
            GPRSMessage receivedGPRSMessage = (e.Message as GPRSMessage);
            if (receivedGPRSMessage != null && ((receivedGPRSMessage.GatewayMessage == null) ||
                (receivedGPRSMessage.GatewayMessage != null 
                && receivedGPRSMessage.GatewayMessage.MessageType != RFPPF.Messages.GPRS.Gateway.GatewayMessage.MessageTypes.Acknowledge
                && receivedGPRSMessage.GatewayMessage.MessageType != RFPPF.Messages.GPRS.Gateway.GatewayMessage.MessageTypes.WhoIAm)))
            {
                // Because GPRS protocol requires acknowledgments except acknowledge message. Send acknowledge message.
                GPRSMessage ack = GPRSMessage.GenerateGPRSGatewayAcknowledgeMessage(receivedGPRSMessage.MessageNumber);
                ((sender as GPRSParser).Info as IScsServerClient).SendMessage(new ScsRawDataMessage(ack.Bytes));
            }
            // Handle received message. It can be mesh protocol message or old protocol receiver message
            try
            {
                gatewayMessage = receivedGPRSMessage.GatewayMessage;
                if (gatewayMessage == null)
                {
                    meshMessage = MessageFactory.ParseMeshMessage(receivedGPRSMessage.Body) as RFMMessage;
                    if (meshMessage == null)
                    {
                        rcvMessage = MessageFactory.ParseReceiverMessage(receivedGPRSMessage.Body) as ReceiverMessage;
                    }
                }
            }
            catch
            {
                try
                {
                    meshMessage = null;
                    rcvMessage = MessageFactory.ParseReceiverMessage(receivedGPRSMessage.Body) as ReceiverMessage;
                }
                catch
                {
                    rcvMessage = null;
                }
            }

            if (meshMessage != null)
            {
                // Mesh protocol

                // Print receiver details such as IMEI and Address
                Console.WriteLine(string.Format("GPRS Message # {0}, IMEI: {1}, Address: {2}", receivedGPRSMessage.MessageNumber, receivedGPRSMessage.MainReceiverID, receivedGPRSMessage.ReceiverID));

                // !!! Here you can implement your custom logics according to message type.
                Console.WriteLine(meshMessage.ToString()); // Use ToString method to print inner fields

                // Or handle message according its type as follows
                if (meshMessage.GetType() == typeof(DiagnosticGetShortAddressMessage))
                {
                    DiagnosticGetShortAddressMessage diagnosticGetShorAddressMessage = meshMessage as DiagnosticGetShortAddressMessage;
                    Console.WriteLine(string.Format("Tag {0} DiagnosticGetShortAddressMessage", diagnosticGetShorAddressMessage.DeviceID));
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
            }
            else if (rcvMessage != null)
            {
                // Old protocol receiver message

                // Print receiver details such as IMEI and Address
                Console.WriteLine(string.Format("GPRS Message # {0}, IMEI: {1}, Address: {2}, Inner message type: {3}", receivedGPRSMessage.MessageNumber, receivedGPRSMessage.MainReceiverID, receivedGPRSMessage.ReceiverID, rcvMessage.GetType().ToString()));

                if (rcvMessage.MessageType == ReceiverMessage.MessageTypes.PowerAlert)
                {
                    // Receiver power alert. Is sent from the gateway when external power is connected or disconnected
                    // Note: Available only on gateways with the inner battery
                    ReceiverPowerAlertMessage pam = rcvMessage as ReceiverPowerAlertMessage;
                    Console.WriteLine(string.Format("Gateway (IMEI: {0}) External power {1}", receivedGPRSMessage.MainReceiverID, pam.ExternalPowerConnected ? "connected" : "disconnected"));
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
                //else if(rcvMessage.MessageType == ReceiverMessage.MessageTypes.RPCResponse)
                //{
                //    ReceiverRPCResponseMessage rcpr = rcvMessage as ReceiverRPCResponseMessage;
                //    byte[] bytes = new byte[16];
                //    rcpr.RPCID.ToByteArray().CopyTo(bytes, 0);
                //    Console.WriteLine(string.Format("Gateway (IMEI: {0}) RPC response message: ID - {1}, Data - {2}", receivedGPRSMessage.MainReceiverID, new Guid(bytes), Encoding.ASCII.GetString(rcpr.Payload)));
                //}
                else
                {
                    Console.WriteLine(rcvMessage.ToString());
                }
            }
            else if (gatewayMessage != null)
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
            }
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
