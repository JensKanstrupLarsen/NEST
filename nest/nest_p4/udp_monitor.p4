/* -*- P4_16 -*- */
#include <core.p4>
#include <v1model.p4>

const bit<16> TYPE_IPV4 = 0x800;
const bit<8>  TYPE_UDP  = 0x11;

/*************************************************************************
*********************** H E A D E R S  ***********************************
*************************************************************************/

typedef bit<9>  egressSpec_t;
typedef bit<48> macAddr_t;
typedef bit<32> ip4Addr_t;
typedef bit<16> port_t;
typedef bit<4>  agent_t;

header ethernet_t {
    macAddr_t dstAddr;
    macAddr_t srcAddr;
    bit<16>   etherType;
}

header ipv4_t {
    bit<4>    version;
    bit<4>    ihl;
    bit<8>    diffserv;
    bit<16>   totalLen;
    bit<16>   identification;
    bit<3>    flags;
    bit<13>   fragOffset;
    bit<8>    ttl;
    bit<8>    protocol;
    bit<16>   hdrChecksum;
    ip4Addr_t srcAddr;
    ip4Addr_t dstAddr;
}

header udp_t {
    port_t  srcPort;
    port_t  dstPort;
    bit<16> packetLength;
    bit<16> checksum;
}

// 64 bits / 8 bytes
header sessionH_t {
    bit<8>  msgLength;
    bit<10> sessionID;
    bit<6>  messageID;
    agent_t senderRole;
    agent_t receiverRole;
    bit<8>  checksum;
    bit<16> seqNum;
}

struct metadata {
    /* empty */
}

struct headers {
    ethernet_t   ethernet;
    ipv4_t       ipv4;
    udp_t        udp;
    sessionH_t   sessionH;
}

/*************************************************************************
*********************** P A R S E R  ***********************************
*************************************************************************/

parser MyParser(packet_in packet,
                out headers hdr,
                inout metadata meta,
                inout standard_metadata_t standard_metadata) {

    state start {
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.etherType) {
            TYPE_IPV4: parse_ipv4;
            default: accept;
        }
    }

    state parse_ipv4 {
        packet.extract(hdr.ipv4);
        transition parse_transport;
    }

    state parse_transport {
        packet.extract(hdr.udp);
        transition parse_session;
    }

    state parse_session {
        packet.extract(hdr.sessionH);
        transition accept;
    }

}

/*************************************************************************
************   C H E C K S U M    V E R I F I C A T I O N   *************
*************************************************************************/

control MyVerifyChecksum(inout headers hdr, inout metadata meta) {
    apply {  }
}


/*************************************************************************
**************  I N G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control Process(inout headers hdr,
                  inout metadata meta,
                  inout standard_metadata_t standard_metadata) {

    register<bit<32>>(1024) states;
    bit<1> ingressOrEgress;
    bit<32> monitorState;
    bit<10> sessionID;

    action drop() {
        mark_to_drop(standard_metadata);
    }

    action ipv4_forward(macAddr_t dstAddr, egressSpec_t port) {
        standard_metadata.egress_spec = port;
        hdr.ethernet.srcAddr = hdr.ethernet.dstAddr;
        hdr.ethernet.dstAddr = dstAddr;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }

    action approve(bit<32> nextState) {
	states.write((bit<32>)hdr.sessionH.sessionID, nextState);
	log_msg("[LOG]: > [{}] Message accepted, updating state ({} --> {}) and forwarding. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
	    {sessionID, monitorState, nextState,
		hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
    }

    action disapprove() {
	mark_to_drop(standard_metadata);
	log_msg("[LOG]: > [{}] Bad message, rejecting. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
	    {sessionID,
		hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
        // standard_metadata.egress_spec = 510;
    }

    action set_flags(bit<1> is_ingressOrEgress) {
	ingressOrEgress = is_ingressOrEgress;
    }

    table monitor_table {
        key = {
	    hdr.sessionH.senderRole: exact;
            hdr.sessionH.messageID: exact;
	    hdr.sessionH.receiverRole: exact;
            monitorState: exact;
        }
        actions = {
            approve;
            disapprove;
        }
        size = 1024;
        default_action = disapprove();
    }

    table ipv4_lpm {
        key = {
            hdr.ipv4.dstAddr: lpm;
        }
        actions = {
            ipv4_forward;
            drop;
        }
        size = 1024;
        default_action = drop();
    }

    table check_ports {
        key = {
            standard_metadata.ingress_port: exact;
            standard_metadata.egress_spec: exact;
        }
        actions = {
            set_flags;
            NoAction;
        }
        size = 1024;
        default_action = NoAction();
    }

    apply {
	if (hdr.sessionH.isValid() && hdr.ipv4.isValid()){
	    sessionID = hdr.sessionH.sessionID;
	    if (hdr.sessionH.messageID == 0 && hdr.sessionH.receiverRole == 0) {
		log_msg("[LOG]: > Received termination message, dropping session {}.", {sessionID});
		states.write((bit<32>)sessionID, 0);
	    } else {
		ipv4_lpm.apply();
		ingressOrEgress = 0;
		check_ports.apply();
		if (ingressOrEgress == 1) {
		    states.read(monitorState, (bit<32>)sessionID);
		    monitor_table.apply();
		}
	    }
	} else {
	    mark_to_drop(standard_metadata);
	}
    }
}

/*************************************************************************
****************  E G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control MyEgress(inout headers hdr,
                 inout metadata meta,
                 inout standard_metadata_t standard_metadata) {
    apply {  }
}

/*************************************************************************
*************   C H E C K S U M    C O M P U T A T I O N   **************
*************************************************************************/

control MyComputeChecksum(inout headers  hdr, inout metadata meta) {
     apply {
        update_checksum(
            hdr.ipv4.isValid(),
            { hdr.ipv4.version,
              hdr.ipv4.ihl,
              hdr.ipv4.diffserv,
              hdr.ipv4.totalLen,
              hdr.ipv4.identification,
              hdr.ipv4.flags,
              hdr.ipv4.fragOffset,
              hdr.ipv4.ttl,
              hdr.ipv4.protocol,
              hdr.ipv4.srcAddr,
              hdr.ipv4.dstAddr },
            hdr.ipv4.hdrChecksum,
            HashAlgorithm.csum16);
    }
}

/*************************************************************************
***********************  D E P A R S E R  *******************************
*************************************************************************/

control MyDeparser(packet_out packet, in headers hdr) {
    apply {
        packet.emit(hdr.ethernet);
        packet.emit(hdr.ipv4);
	packet.emit(hdr.udp);
	packet.emit(hdr.sessionH);
    }
}

/*************************************************************************
***********************  S W I T C H  *******************************
*************************************************************************/

V1Switch(
MyParser(),
MyVerifyChecksum(),
Process(),
MyEgress(),
MyComputeChecksum(),
MyDeparser()
) main;
