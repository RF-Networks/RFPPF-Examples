![Nuget](https://img.shields.io/nuget/v/RFPPF?style=flat-square)


# GPRSServerMeshProtocol
This example is a basic server example that handles RF-Networks Ltd. GPRS gateway devices connections and data processing.

Example uses two parsers:
- **GPRSParser** for handling GPRS type messages
- **RFMeshParser** for handling RF-Networks Mesh protocol messages

All device messages are wrapped with GPRS message and are sent to the server. Each GPRS message should receive an acknowledgement in order to remove message from the gateway's message queue.
To send an acknowledgement use following code:
```csharp
GPRSMessage ack = GPRSMessage.GenerateGPRSAcknowledgeMessage(MessageNumber);
client.SendMessage(new ScsRawDataMessage(ack.Bytes)
```

Each GPRS message has RFMMessage as its payload. According to the RFMMessage type you should implement application logics.

In order to change Mesh Tag registers use following code:

```csharp
RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessage getRegMsg = RFPPF.Messages.MessageFactory.CreateMessage(typeof(RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessage), new RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessageBuilder() 
{
    Version = 1,
    DeviceID = <DeviceID>,
    MessageNumber = 0,
    RegisterNumber = <RegisterNumber>,
    RegisterValue = <RegisterValue>
}) as RFPPF.Messages.RFM.Configuration.SetRegisterRequestMessage;
GPRSMessage transientMsg = GPRSMessage.GenerateTransientMessage(getRegMsg.Bytes);
client.SendMessage(new ScsRawDataMessage(transientMsg.Bytes));
```

In order to send SMS via RF-Networks gateway use following code:
```csharp
GPRSMessage smsMsg = GPRSMessage.GenerateSendSMSMessage(<Phone Number>, <Message>);
client.SendMessage(new ScsRawDataMessage(smsMsg.Bytes));
```
