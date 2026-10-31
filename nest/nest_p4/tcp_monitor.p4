/* -*- P4_16 -*- */
#include <core.p4>
#include <v1model.p4>

const bit<16> TYPE_IPV4 = 0x800;
const bit<8>  TYPE_UDP  = 0x11;
const bit<8>  TYPE_TCP  = 0x06;

/*************************************************************************
*********************** H E A D E R S  ***********************************
*************************************************************************/

typedef bit<9>  egressSpec_t;
typedef bit<48> macAddr_t;
typedef bit<32> ip4Addr_t;
typedef bit<16> port_t;
typedef bit<4>  agent_t;

// 112 bits / 14 bytes
header ethernet_t {
    macAddr_t dstAddr;
    macAddr_t srcAddr;
    bit<16>   etherType;
}

// 160 bits / 20 bytes
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

// 64 bits / 8 bytes
header udp_t {
    port_t  srcPort;
    port_t  dstPort;
    bit<16> packetLength;
    bit<16> checksum;
}

// 160 bits / 20 bytes
header tcp_t {
    port_t srcPort;
    port_t dstPort;
    bit<32> seqNum;
    bit<32> ackNum;
    bit<4> dataOffset;
    bit<4> reserved;
    bit<1> CWR;
    bit<1> ECE;
    bit<1> URG;
    bit<1> ACK;
    bit<1> PSH;
    bit<1> RST;
    bit<1> SYN;
    bit<1> FIN;
    bit<16> window;
    bit<16> checksum;
    bit<16> urgentPointer;
}

// Up to 320 bits / 40 bytes
header tcpOptions_t {
    varbit<320> options;
}

// 56 bits / 7 bytes
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
    bit<16> tcpLength;
    /* empty */
}

struct headers {
    ethernet_t      ethernet;
    ipv4_t          ipv4;
    tcp_t           tcp;
    udp_t           udp;
    tcpOptions_t    tcpOptions;
    sessionH_t      sessionH;
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
        transition select(hdr.ipv4.protocol) {
	    TYPE_TCP: parse_tcp;
	    TYPE_UDP: parse_udp;
	    default: accept;
	}
    }

    state parse_tcp {
        packet.extract(hdr.tcp);
        transition select(hdr.tcp.dataOffset) {
	    0: parse_session;
	    default: parse_tcpOptions;
	}
    }

    state parse_tcpOptions {
	packet.extract(hdr.tcpOptions, (bit<32>) (((bit<9>) hdr.tcp.dataOffset-5)*8*4));
	transition parse_session;
    }

    state parse_udp {
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

    bit<8> doDrop;

    register<bit<32>>(1024) states;
    bit<32> monitorState;

    bit<10> sessionID;

    bit<32> nextState;

    register<bit<16>>(16384) storedSSQN;
    // hash using the sender ID, store based on receiver ID
    register<bit<16>>(16384) storedChecksum;
    bit<32> ssqn_index;
    bit<16> ssqn;
    bit<32> chksum_index;
    bit<16> chksum;

    bool is_egress = false;
    bool is_ingress = false;
    bool acceptMessage = false;
 
    action drop() {
        mark_to_drop(standard_metadata);
    }

    action ipv4_forward(macAddr_t dstAddr, egressSpec_t port) {
        standard_metadata.egress_spec = port;
        hdr.ethernet.srcAddr = hdr.ethernet.dstAddr;
        hdr.ethernet.dstAddr = dstAddr;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }

    action compute_new_checksum() {
        chksum_index = ((bit<32>)sessionID << 4) + (bit<32>)hdr.sessionH.receiverRole;
        storedChecksum.read(chksum, chksum_index);
        chksum = chksum + (bit<16>)hdr.sessionH.senderRole * (bit<16>)hdr.sessionH.messageID;
	storedChecksum.write((bit<32>)chksum_index, chksum);
    }

    action update_packet_checksum() {
        chksum_index = ((bit<32>)sessionID << 4) + (bit<32>)hdr.sessionH.receiverRole;
        storedChecksum.read(chksum, chksum_index);
        hdr.sessionH.checksum = (bit<8>)chksum;
    }

    action approve_next_state(bit<32> nextStateTmp) {
        nextState = nextStateTmp;
        acceptMessage = true;
    }

    action approve() {
	    acceptMessage = true;
    }

    action disapprove() {
        // mark_to_drop(standard_metadata);
        acceptMessage = false;
    }

    action set_ingress() {
	    is_ingress = true;
    }

    action set_egress() {
	    is_egress = true;
    }

    action end_session() {
	    states.write((bit<32>)sessionID, 0);

	    storedSSQN.write(((bit<32>)sessionID << 4) + (bit<32>)hdr.sessionH.senderRole, 0);
    }

    table receiver_table {
        key = {
            hdr.sessionH.senderRole:   optional;
            hdr.sessionH.receiverRole: optional;
            monitorState:              exact;
        }
        actions = {
	    approve;
            disapprove;
        }
        size = 1024;
        default_action = disapprove();
    }

    table monitor_table {
        key = {
	    hdr.sessionH.senderRole:   optional;
            hdr.sessionH.messageID:    optional;
	    hdr.sessionH.receiverRole: optional;
            monitorState:              exact;
        }
        actions = {
	    approve_next_state;
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
            set_ingress;
	    set_egress;
            NoAction;
        }
        size = 1024;
        default_action = NoAction();
    }

    apply {
	if (hdr.ipv4.isValid()) {
	    ipv4_lpm.apply();
	    if (hdr.tcp.isValid()) {
		if (hdr.sessionH.isValid()) {
		    meta.tcpLength = hdr.ipv4.totalLen - (bit<16>)(hdr.ipv4.ihl)*4; // Session header length
		    sessionID = hdr.sessionH.sessionID;
		    is_ingress = false; is_egress = false;

		    check_ports.apply();
		    // It's an incoming/outgoing packet, so it must be monitored.
		    if (is_ingress || is_egress) {

			ssqn_index = ((bit<32>)sessionID << 4) + (bit<32>)hdr.sessionH.senderRole;

			storedSSQN.read(ssqn, ssqn_index);

			nextState = 0;
			states.read(monitorState, (bit<32>)sessionID);

			receiver_table.apply();

			// Correct receiver, high SSQN
			if (acceptMessage && hdr.sessionH.seqNum > ssqn) {
			    monitor_table.apply();
			    if (acceptMessage) {
				if (hdr.sessionH.seqNum == ssqn+1 || is_egress) {
				    log_msg("[LOG]: > [{}] Message accepted, updating state ({} --> {}) and forwarding. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
					{sessionID, monitorState, nextState, hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
				    states.write((bit<32>)sessionID, nextState);
				    storedSSQN.write(ssqn_index, hdr.sessionH.seqNum);
				    compute_new_checksum();
				    update_packet_checksum();
				} else{
				    log_msg("[LOG]: > [{}] Bad message, rejecting. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
					{sessionID, hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
				    mark_to_drop(standard_metadata);
				}
			    } else {
				if (hdr.sessionH.seqNum == ssqn+1 && is_ingress) {
				    log_msg("[LOG]: > [{}] Bad message, rejecting and dropping the session. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
					{sessionID, hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
				    mark_to_drop(standard_metadata);
				    end_session();
				} else {
				    log_msg("[LOG]: > [{}] Bad message, rejecting. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
					{sessionID, hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
				    mark_to_drop(standard_metadata);
				}
			    }

			// Low SSQN, possible retransmit: forward but do not update state
			} else if (hdr.sessionH.seqNum <= ssqn) {
			    log_msg("[LOG]: > [{}] Low SSQN, possible retransmit ({} <= {}); forwarding. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
				{sessionID, hdr.sessionH.seqNum, ssqn, hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
			    update_packet_checksum();

			// Unexpected receiver, possibly out-of-order: reject but do not drop session
			} else {
			    log_msg("[LOG]: > [{}] Out-of-order message, rejecting. Message contents: [SND:{},ID:{},SSQN:{},RCV:{}]",
				{sessionID, hdr.sessionH.senderRole, hdr.sessionH.messageID, hdr.sessionH.seqNum, hdr.sessionH.receiverRole});
			    mark_to_drop(standard_metadata);
			}
		    }
		// If packet has no session header or relevant TCP metadata, then drop.
		} else if (hdr.tcp.ACK == 0 && hdr.tcp.SYN == 0 && hdr.tcp.FIN == 0) { //|| (hdr.sessionLength.isValid() && !hdr.sessionH.isValid())) {
		    log_msg("[LOG]: > Dropping non-session message.");
		    mark_to_drop(standard_metadata);
		}
	    // UDP header, not meant for monitoring, but might be a termination message.
	    } else if (hdr.udp.isValid()) {
		if (hdr.sessionH.isValid()) {
		    sessionID = hdr.sessionH.sessionID;
		    // If session header has message ID 0 *and* receiver ID 0, then terminate the session.
		    if (hdr.sessionH.messageID == 0 && hdr.sessionH.receiverRole == 0) {
			log_msg("[LOG]: > Received termination message, dropping session {}.", {sessionID});
			end_session();

			mark_to_drop(standard_metadata);
		    }
		} else {
		    mark_to_drop(standard_metadata);
		}
	    } else {
		mark_to_drop(standard_metadata);
	    }
	}
	if (hdr.ipv4.ttl <= 0) {
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

	update_checksum_with_payload(hdr.tcp.isValid() && hdr.sessionH.isValid(),
	    { hdr.ipv4.srcAddr,
                hdr.ipv4.dstAddr,
                8w0,
                hdr.ipv4.protocol,
                meta.tcpLength,
                hdr.tcp.srcPort,
                hdr.tcp.dstPort,
                hdr.tcp.seqNum,
                hdr.tcp.ackNum,
                hdr.tcp.dataOffset,
                hdr.tcp.reserved,
		hdr.tcp.CWR,
                hdr.tcp.ECE,
                hdr.tcp.URG,
                hdr.tcp.ACK,
                hdr.tcp.PSH,
                hdr.tcp.RST,
                hdr.tcp.SYN,
                hdr.tcp.FIN,
                hdr.tcp.window,
                16w0,
                hdr.tcp.urgentPointer,
		hdr.tcpOptions.options,
		hdr.sessionH.msgLength,
		hdr.sessionH.sessionID,
		hdr.sessionH.messageID,
		hdr.sessionH.senderRole,
		hdr.sessionH.receiverRole,
		hdr.sessionH.checksum,
		hdr.sessionH.seqNum
            },
            hdr.tcp.checksum, HashAlgorithm.csum16);
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
	packet.emit(hdr.tcp);
	packet.emit(hdr.tcpOptions);
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
