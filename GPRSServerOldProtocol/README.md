# GPRSServerOldProtocol
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

Each GPRS message has ReceiverMessage as its payload. According to the ReceiverMessage type you should implement application logics.

Tag messages are wrapped with TagDataMessage, which is the derivative of ReceiverMessage. Each TagDataMessage can contain up to 6 Tag messages.

RF-Networks Mesh protocol messages are wrapped with ReceiverTransientMessage, which is the derivative of ReceiverMessage. Message payload (that starts with the 0x96 byte) should be passed to the RFMParser for further parsing.

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