using Hik.Communication.Scs.Communication.Protocols;

namespace GPRSServerOldProtocol
{
    public class MyWireProtocolFactory : IScsWireProtocolFactory
    {
        public IScsWireProtocol CreateWireProtocol()
        {
            return new MyWireProtocol();
        }
    }
}
