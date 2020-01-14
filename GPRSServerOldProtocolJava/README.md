[![Maven Central](https://img.shields.io/maven-central/v/org.bitbucket.rfnetwork/rfppf.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.bitbucket.rfnetwork%22%20AND%20a:%22rfppf%22)

# GPRSServerOldProtocol
This example is a basic server example that handles RF-Networks Ltd. GPRS gateway devices connections and data processing.

Example uses two parsers:
- **GPRSParser** for handling GPRS type messages
- **RFMeshParser** for handling RF-Networks Mesh protocol messages

All device messages are wrapped with GPRS message and are sent to the server. Each GPRS message should receive an acknowledgement in order to remove message from the gateway's message queue.
To send an acknowledgement use following code:

```java
GPRSMessage ack = GPRSMessage.GenerateGPRSAcknowledgeMessage(MessageNumber);
client.sendToClient(ack.GetBytes());
```

Each GPRS message has ReceiverMessage as its payload. According to the ReceiverMessage type you should implement application logics.

Tag messages are wrapped with TagDataMessage, which is the derivative of ReceiverMessage. Each TagDataMessage can contain up to 6 Tag messages.

RF-Networks Mesh protocol messages are wrapped with ReceiverTransientMessage, which is the derivative of ReceiverMessage. Message payload (that starts with the 0x96 byte) should be passed to the RFMParser for further parsing.

In order to change Mesh Tag registers use following code:

```java
SetRegisterRequestMessageBuilder setRegMsgBuilder = new SetRegisterRequestMessageBuilder();
setRegMsgBuilder.Version = 1;
setRegMsgBuilder.DeviceID = <DeviceID>;
setRegMsgBuilder.MessageNumber = 0;
setRegMsgBuilder.RegisterNumber = <RegisterNumber>;
setRegMsgBuilder.RegisterValue = <RegisterValue>;
SetRegisterRequestMessage setRegMsg = (SetRegisterRequestMessage)MessageFactory.CreateMessage(SetRegisterRequestMessage.class, setRegMsgBuilder);
GPRSMessage transientMsg = GPRSMessage.GenerateTransientMessage(setRegMsgBuilder.GetBytes());
client.sendToClient(transientMsg.GetBytes());
```

In order to send SMS via RF-Networks gateway use following code:

```java
GPRSMessage smsMsg = GPRSMessage.GenerateSendSMSMessage(<Phone Number>, <Message>);
client.sendToClient(smsMsg.GetBytes());
```
