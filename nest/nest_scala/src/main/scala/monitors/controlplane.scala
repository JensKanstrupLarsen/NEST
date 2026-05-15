package controlplane

import com.google.protobuf.ByteString
import p4rtype._
import sessiontypes._
import p4.v1.p4runtime.StreamMessageResponse

enum MonitorSwitchType:
    case UDPSwitchType extends MonitorSwitchType
    case TCPSwitchType extends MonitorSwitchType
import MonitorSwitchType._

abstract class MonitorSwitch(private var json : String):
    def getID() : Int = -1
    def getMonitorType() : Option[MonitorSwitchType] =
        None
    def addJsonRule(rule : String) =
        if this.json == "" then
            this.json = rule
        else
            this.json += ",\n" + rule
    def getJsonRules() : String =
        "{" +
        "\"target\": \"bmv2\"," +
        "\"p4info\": \"build/monitor.p4.p4info.txt\"," +
        "\"bmv2_json\": \"build/monitor.json\"," +
        "\"table_entries\": [\n" +
        this.json +
        "]}"
    def getLog() : Seq[StreamMessageResponse] = Seq()
    def disconnect() : Unit = ()
    def addForwardingRule(dst_IP : ByteString, dst_MAC : ByteString, p_out : ByteString) : Unit
    def addPortCheck(p_in : ByteString, p_out : ByteString, is_ingress : Boolean, is_egress : Boolean) : Unit
    def addMonitor(cm : CompositeMonitor) : Unit
    def getTotalIPRules() : Int

def udp_ToTableEntry (tr : Transition) : (udp_monitor.TableMatchFields["Process.monitor_table"], udp_monitor.TableAction["Process.monitor_table"]) =
    val isSTTransition = tr.trsLabel.label.fields.contains("hdr.sessionH.messageID")
    (p4rtype.MatchFields(
        (("hdr.sessionH.senderRole", p4rtype.Exact(tr.trsLabel.sender.id)),
         ("hdr.sessionH.messageID", p4rtype.Exact(tr.trsLabel.label.fields("hdr.sessionH.messageID"))),
         ("hdr.sessionH.receiverRole", p4rtype.Exact(tr.trsLabel.receiver.id)),
         ("monitorState", p4rtype.Exact(intToBytes(tr.prevState))))),
    p4rtype.Action("Process.approve",
        p4rtype.Param("nextState", intToBytes(tr.nextState))))

class UDPSwitch(c : udp_monitor.Chan) extends MonitorSwitch(""):
    override def getID(): Int = c.getDeviceId()
    override def getMonitorType(): Option[MonitorSwitchType] =
        Some(UDPSwitchType)
    override def getLog() : Seq[StreamMessageResponse] =
        c.getLog()
    override def disconnect(): Unit =
        c.disconnect()
    override def addForwardingRule(dst_IP : ByteString, dst_MAC : ByteString, p_out : ByteString) : Unit =
        val ipMask = dst_IP.size()*8
        var ip = dst_IP
        for (_ <- 0 until (32-ipMask)/8) do
            ip = ip.concat(bytes(0))
        val result = insert(c, TableEntry(
            "Process.ipv4_lpm",
            MatchFields(("hdr.ipv4.dstAddr", Some(LPM(ip, ipMask)))),
            Action("Process.ipv4_forward", (Param("dstAddr", dst_MAC), Param("port", p_out))),
            0))
        if !result then
            println("Error: Failed to insert forwarding rule into s"+(c.getDeviceId()+1))
    override def addPortCheck(p_in: ByteString, p_out: ByteString, is_ingress : Boolean, is_egress : Boolean): Unit =
        val is_ingressOrEgressBytes = if is_ingress || is_egress then bytes(1) else bytes(0)
        val result = insert(c, TableEntry(
            "Process.check_ports",
            MatchFields(
                ("standard_metadata.ingress_port", Exact(p_in)),
                ("standard_metadata.egress_spec", Exact(p_out))),
            Action("Process.set_flags", Param("is_ingressOrEgress", is_ingressOrEgressBytes)),
            0))
        if !result then
            println("Error: Failed to insert port check rule into s"+(c.getDeviceId()+1))
    override def addMonitor(cm: CompositeMonitor): Unit =
        val msa = CM_to_MSA(cm)
        printDotFromMSA(msa)
        println("Adding entries to s" + (c.getDeviceId()+1) + " (States: " + msa.states.size + ", transitions: " + msa.transitions.size + ")")
        val tableEntries = MSA_to_TableEntries(msa, udp_ToTableEntry)
        val result = tableEntries.foldLeft(true)((acc, e) =>
            val (matchFields, action) = e
            acc && insert(c, TableEntry("Process.monitor_table", matchFields, action, 0)))
        if !result then
            println("Error: Failed to insert monitor rule into s"+(c.getDeviceId()+1))
    override def getTotalIPRules() : Int =
        read(c, TableEntry("Process.ipv4_lpm", "*", "*", 0)).size

def tcp_ToTableEntry (tr : Transition) :
    ((tcp_monitor.TableMatchFields["Process.receiver_table"], tcp_monitor.TableAction["Process.receiver_table"]),
     (tcp_monitor.TableMatchFields["Process.monitor_table"], tcp_monitor.TableAction["Process.monitor_table"])) =
    val isSTTransition = tr.trsLabel.label.fields.contains("hdr.sessionH.messageID")
    ((p4rtype.MatchFields(
        (("hdr.sessionH.senderRole", if isSTTransition then Some(p4rtype.Optional(tr.trsLabel.sender.id)) else None),
         ("hdr.sessionH.receiverRole", if isSTTransition then Some(p4rtype.Optional(tr.trsLabel.receiver.id)) else None),
         ("monitorState", p4rtype.Exact(intToBytes(tr.prevState))))),
      p4rtype.Action("Process.approve",())),
     (p4rtype.MatchFields(
        (("hdr.sessionH.senderRole", if isSTTransition then Some(p4rtype.Optional(tr.trsLabel.sender.id)) else None),
         ("hdr.sessionH.messageID", tr.trsLabel.label.fields.get("hdr.sessionH.messageID").map(p4rtype.Optional(_))),
         ("hdr.sessionH.receiverRole", if isSTTransition then Some(p4rtype.Optional(tr.trsLabel.receiver.id)) else None),
         ("monitorState", p4rtype.Exact(intToBytes(tr.prevState))))),
      p4rtype.Action("Process.approve_next_state",
          p4rtype.Param("nextStateTmp", intToBytes(tr.nextState)))))

class TCPSwitch(c : tcp_monitor.Chan) extends MonitorSwitch(""):
    override def getID(): Int = c.getDeviceId()
    override def getMonitorType(): Option[MonitorSwitchType] =
        Some(TCPSwitchType)
    override def getLog() : Seq[StreamMessageResponse] =
        c.getLog()
    override def disconnect(): Unit =
        c.disconnect()
    override def addForwardingRule(dst_IP : ByteString, dst_MAC : ByteString, p_out : ByteString) : Unit =
        val ipMask = dst_IP.size()*8
        var ip = dst_IP
        for (_ <- 0 until (32-ipMask)/8) do
            ip = ip.concat(bytes(0))
        val result = insert(c, TableEntry(
            "Process.ipv4_lpm",
            MatchFields(("hdr.ipv4.dstAddr", Some(LPM(ip, ipMask)))),
            Action("Process.ipv4_forward", (Param("dstAddr", dst_MAC), Param("port", p_out))),
            0))
        if !result then
            println("Error: Failed to insert forwarding rule into s"+(c.getDeviceId()+1))
    override def addPortCheck(p_in: ByteString, p_out: ByteString, is_ingress: Boolean, is_egress : Boolean): Unit =
        val is_ingressBytes = if is_ingress then bytes(1) else bytes(0)
        val is_egressBytes = if is_egress then bytes(1) else bytes(0)
        val result = insert(c, TableEntry(
            "Process.check_ports",
            MatchFields(
                ("standard_metadata.ingress_port", Exact(p_in)),
                ("standard_metadata.egress_spec", Exact(p_out))),
            if is_ingress then
                Action("Process.set_ingress", ())
            else
                Action("Process.set_egress", ()),
            0))
        if !result then
            println("Error: Failed to insert port check rule into s"+(c.getDeviceId()+1))
    override def addMonitor(cm: CompositeMonitor): Unit =
        val msa = CM_to_MSA(cm)
        printDotFromMSA(msa)
        println("Adding entries to s" + (c.getDeviceId()+1) + " (States: " + msa.states.size + ", transitions: " + msa.transitions.size + ")")
        val (recvTableEntries, monTableEntries) =
            msa.transitions.foldLeft((List[(tcp_monitor.TableMatchFields["Process.receiver_table"], tcp_monitor.TableAction["Process.receiver_table"])](),
                                      List[(tcp_monitor.TableMatchFields["Process.monitor_table"], tcp_monitor.TableAction["Process.monitor_table"])]()))((acc, tr) =>
                val (rtes, mtes) = acc
                val ((recvMatchFields, recvAction), (monMatchFields, monAction)) = tcp_ToTableEntry(tr)
                (if !rtes.exists(_ == (recvMatchFields, recvAction)) then (recvMatchFields, recvAction)::rtes else rtes,
                if !mtes.exists(_ == (monMatchFields,  monAction))  then (monMatchFields, monAction)::mtes else mtes))
        //val tableEntries = MSA_to_TableEntries(msa, tcp_ToTableEntry)
        val recvFails = recvTableEntries.foldLeft(0)((acc, e) =>
            val (recvMatchFields, recvAction) = e
            if insert(c, TableEntry("Process.receiver_table", recvMatchFields, recvAction, 1)) then acc else acc + 1)
        val monFails = monTableEntries.foldLeft(0)((acc, e) =>
            val (monMatchFields, monAction) = e
            if insert(c, TableEntry("Process.monitor_table", monMatchFields, monAction, 1)) then acc else acc + 1)
        if recvFails+monFails > 0 then
            println("Error: Failed to insert "+(recvFails+monFails)+" out of "+(recvTableEntries.length+monTableEntries.length)+" monitor rule into s"+(c.getDeviceId()+1))
    override def getTotalIPRules() : Int =
        read(c, TableEntry("Process.ipv4_lpm", "*", "*", 0)).size

def constructSwitch(id : Int, ip : String, port : Int, isTCP : Boolean) : MonitorSwitch =
    if isTCP then
        TCPSwitch(tcp_monitor.connect(id, ip, port))
    else
        UDPSwitch(udp_monitor.connect(id, ip, port))

def intToBytes(n : Int) : ByteString =
    if n == 0 then
        return bytes(0)
    else
        var i = n
        var b = bytes()
        while (i > 0) do
            val nextByte = (i % 256).toByte
            b = bytes(nextByte).concat(b)
            i = (i - nextByte) / 256
        return b

def bytesToInt(b : ByteString) : Int =
    if b.size() == 0 then
        return 0
    else
        return (b.byteAt(0).toInt << (b.size()-1)) + (bytesToInt(b.substring(1)))

def getIPString(b : ByteString) : String =
    if b.size() == 0 then
        return ""
    else if b.size == 1 then
        return b.byteAt(0).toString()
    else
        return return b.byteAt(0).toString() + "." + getIPString(b.substring(1))

def getMACString(b : ByteString) : String =
    if b.size() == 0 then
        return ""
    else if b.size == 1 then
        return "%02X" format b.byteAt(0)
    else
        return ("%02X" format b.byteAt(0)) + ":" + getMACString(b.substring(1))

def genPortPairs(switch : MonitorSwitch, borderPorts : List[Byte], allPorts : List[Byte]) : List[(MonitorSwitch, ByteString, ByteString, Boolean, Boolean)] =
    var portPairs = List[(MonitorSwitch, ByteString, ByteString, Boolean, Boolean)]()
    for (pIn <- allPorts) do
        for (pOut <- allPorts) do
            if (borderPorts.contains(pIn) || borderPorts.contains(pOut)) && pIn != pOut then
                portPairs = (switch, bytes(pIn), bytes(pOut), borderPorts.contains(pIn), borderPorts.contains(pOut)) :: portPairs
    return portPairs

// UDP

def genForwardingRuleJson(dst_IP : ByteString, dst_MAC : ByteString, p_out : ByteString) : String =
    val ipMask = dst_IP.size()*8
    var ip = dst_IP
    for (_ <- 0 until (32-ipMask)/8) do
        ip = ip.concat(bytes(0))
    "{" +
    "\"table\": \"Process.ipv4_lpm\", " +
    "\"match\": {\"hdr.ipv4.dstAddr\": [" + getIPString(ip) + "," + ipMask + "]}, " +
    "\"action_name\": \"Process.ipv4_forward\", " +
    "\"action_params\": {" +
    "\"dstAddr\":" + getMACString(dst_MAC) + ", " +
    "\"port\":" + bytesToInt(p_out) +
    "}}"

def genDefaultForwardingRuleJson() : String =
    "{" +
    "\"table\": \"Process.ipv4_lpm\", " +
    "\"default_action\": true, " +
    "\"action_name\": \"Process.drop\", " +
    "\"action_params\": { }" +
    "}"

def genPortCheckRuleJson(p_in : ByteString, p_out : ByteString, is_ingressOrEgress : Boolean) : String =
    val is_ingressOrEgressBytes = if is_ingressOrEgress then bytes(1) else bytes(0)
    "{" +
    "\"table\":\"Process.check_ports\", " +
    "\"match\": {" +
    "\"standard_metadata.ingress_port\":" + bytesToInt(p_in) + ", " +
    "\"standard_metadata.egress_spec\":" + bytesToInt(p_out) + "} " +
    "\"action_name\": \"Process.set_flags\", " +
    "\"action_params\": {" +
    "\"is_ingressOrEgress\": " + bytesToInt(is_ingressOrEgressBytes) +
    "}}"

def genDefaultPortCheckJson() : String =
    "{" +
    "\"table\":\"Process.check_ports\", " +
    "\"default_action\": true, " +
    "\"action_name\": \"NoAction\", " +
    "\"action_params\": {" +
    "}}"

def genMonitorEntryJson(tr : Transition) : String =
    val isSTTransition = tr.trsLabel.label.fields.contains("hdr.sessionH.messageID")
    "{" +
    "\"table\":\"Process.monitor_table\", " +
    "\"match\": {" +
    "\"hdr.sessionH.senderRole\": " + bytesToInt(tr.trsLabel.sender.id) + ", " +
    "\"hdr.sessionH.messageID\": " + bytesToInt(tr.trsLabel.label.fields("hdr.sessionH.messageID")) + ", " +
    "\"hdr.sessionH.receiverRole\": " + bytesToInt(tr.trsLabel.receiver.id) + "} " +
    "\"action_name\": \"Process.approve\", " +
    "\"action_params\": {" +
    "\"nextState\":" + tr.nextState +
    "}}"

def genDefaultMonitorEntryJson() : String =
    "{" +
    "\"table\":\"Process.monitor_table\", " +
    "\"default_action\": true, " +
    "\"action_name\": \"Process.disapprove\", " +
    "\"action_params\": {" +
    "}}"

def genMonitorEntriesJson(cm : CompositeMonitor) : String =
    val msa = CM_to_MSA(cm)
    msa.transitions.foldLeft("")((acc,tr) =>
        if acc == "" then
            genMonitorEntryJson(tr)
        else
            acc + ",\n" + genMonitorEntryJson(tr))

// Other
def packetPrinter(switch : Int, log : Seq[StreamMessageResponse]) : Unit =
    var packets = List[String]()
    println("Switch " + switch + ":")
    if (log.isEmpty) then
        println("No irregular packets detected.")
    else
        for (r <- log)
            r.update.packet match
                case None =>
                    ()
                case Some(p) =>
                    val labelText = p.payload.substring(68).toStringUtf8()
                    println("Unexpected packet at switch s" + switch + " with label " + labelText)
    println()

def genMainFile(title : String, agents : List[STAgent], labels : List[STLabel]) : String =
    "#!/usr/bin/env python3\n" +
    "import argparse\n" +
    "import random\n" +
    "import socket\n" +
    "import time\n" +
    "import sys\n" +
    "import os\n\n" +
    "sys.path.insert(1, os.path.join(sys.path[0], '..'))\n" +
    "from monitor import SessionHandler, Role, Message\n\n" +
    (agents.foldLeft("")((acc, agent) =>
    acc +
    "class " + agent.name + "(Role):\n" +
    "    name = \""+agent.name+"\"\n" +
    "    id = "+bytesToInt(agent.id)+"\n" +
    "    ip = \""+getIPString(agent.ip)+"\"\n\n")) +
    (labels.foldLeft("")((acc, label) =>
    acc +
    "class " + label.text + "(Message):\n" +
    "    id = "+bytesToInt(label.fields("hdr.sessionH.messageID"))+"\n" +
    "    label = \""+label.text+"\"\n\n" +
    "    def __init__(self, text=\"\"):\n" +
    "       super().__init__(text)\n\n")) +
    "class "+title+"SessionHandler(SessionHandler):\n" +
    "    def __init__(self, role, max_sessions):\n" +
    "        super().__init__(role, \"session\", max_sessions)\n\n" +
    "    def toMessage(self, session_header):\n" +
    (labels.foldLeft("")((acc, label) =>
        if acc == "" then
            "        if (session_header.message_id == "+bytesToInt(label.fields("hdr.sessionH.messageID"))+"):\n" +
            "            return "+label.text+"(session_header.message_text)\n"
        else
            acc +
            "        elif (session_header.message_id == "+bytesToInt(label.fields("hdr.sessionH.messageID"))+"):\n" +
            "            return "+label.text+"(session_header.message_text)\n")) +
    "        else:\n"+
    "            return Message()\n\n"