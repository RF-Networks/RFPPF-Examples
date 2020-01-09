using Hik.Communication.Scs.Communication.Messages;
using Hik.Communication.Scs.Communication.Protocols;
using System.Collections.Generic;
using System.Linq;

namespace GPRSServerOldProtocol
{
    public class MyWireProtocol : IScsWireProtocol
    {
        public byte[] GetBytes(IScsMessage message)
        {
            return ((ScsRawDataMessage)message).MessageData;
        }

        public IEnumerable<IScsMessage> CreateMessages(byte[] receivedBytes)
        {
            List<IScsMessage> msgs = new List<IScsMessage>();
            msgs.Add(new ScsRawDataMessage(receivedBytes));
            return msgs.AsEnumerable();
        }

        public void Reset()
        {

        }
    }
}
