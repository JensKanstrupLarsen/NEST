from netfilterqueue import NetfilterQueue
from scapy.all import *

sys.path.insert(1, os.path.join(sys.path[0], '../nest/nest_python'))
from session_header import SessionHeader

# Tamper examples:
# Store: client.py, TTL:2, RCV:2, LBL:4
# VPN: clientA.py, TTL:2, RCV:3, LBL:4
# Firewall: clientExt.py, TTL:1, RCV:1, LBL:1
# Auction: auction.py, TTL:2, RCV:1, LBL:4
# SIP: sipClientB.py, TTL:1, RCV:3, LBL:3
# POP3: client.py, TTL:1, RCV:2, LBL:5
# Game: gameserver.py, TTL:2, RCV:4, LBL:5

OUT_IFACE = "h1-eth0"
ttl = 0
target_receiver = None
target_label = None
sessions_seen = {}

def process(nfq_packet):
    pkt = IP(nfq_packet.get_payload())

    if TCP in pkt:
        data = bytes(pkt[TCP].payload)
        
        if len(data) == 128: # could indicate session packet
            sh = SessionHeader.from_bytes(pkt[Raw].load)

            if sh.receiver_id == target_receiver and sh.message_id == target_label:
                print("Target packet!")
                if sh.session_id not in sessions_seen:
                    print("Original packet (session "+str(sh.session_id)+"), adjusting TTL")
                    pkt[IP].ttl = ttl
                    sessions_seen[sh.session_id] = pkt[TCP].seq
                elif sessions_seen[sh.session_id] == pkt[TCP].seq:
                    print("Retransmitted packet (session "+str(sh.session_id)+"), fixing message ID")
                    sh.message_id = 63
                    pkt[Raw].load = sh.to_bytes()
            else:
                print("Not our packet.")

    del pkt[IP].len
    del pkt[IP].chksum
    del pkt[TCP].chksum

    nfq_packet.set_payload(bytes(pkt))
    nfq_packet.accept()

"""
def process_old(pkt):
    pkt[IP].tos = 0x20
    if TCP in pkt:
        data = bytes(pkt[TCP].payload)
        
        if len(data) == 128: # could indicate session packet
            sh = SessionHeader.from_bytes(pkt[Raw].load)

            if sh.receiver_id == target_receiver and sh.message_id == target_label:
                print("Target packet!")
                if sh.session_id not in sessions_seen:
                    print("Original packet (session "+str(sh.session_id)+"), adjusting TTL")
                    pkt[IP].ttl = ttl
                    sessions_seen[sh.session_id] = pkt[TCP].seq
                elif sessions_seen[sh.session_id] == pkt[TCP].seq:
                    print("Retransmitted packet (session "+str(sh.session_id)+"), fixing message ID")
                    sh.message_id = 63
                    pkt[Raw].load = sh.to_bytes()
            else:
                print("Not our packet.")

    del pkt[IP].len
    del pkt[IP].chksum
    del pkt[TCP].chksum
    
    sendp(pkt, iface="eth0", verbose=False)
"""

if __name__ == '__main__':
    if len(sys.argv) != 4:
        print("Usage: tamper.py <ttl> <receiver> <label>")
    else:
        print("Starting up tamperer...")
        ttl = int(sys.argv[1])
        target_receiver = int(sys.argv[2])
        target_label = int(sys.argv[3])
        nfq = NetfilterQueue()
        nfq.bind(1, process)
        nfq.run()
        #sniff(iface="vethB", filter="", prn=process, store=False)
