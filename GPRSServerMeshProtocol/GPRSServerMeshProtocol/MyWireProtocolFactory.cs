using Hik.Communication.Scs.Communication.Protocols;

namespace GPRSServerMeshProtocol
{
    public class MyWireProtocolFactory : IScsWireProtocolFactory
    {
        public IScsWireProtocol CreateWireProtocol()
        {
            return new MyWireProtocol();
        }
    }
}
