package firewall

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine

val a_ClientInt = newAgent("ClientInt", 1, bytes(10,0,1,1))
val a_ClientExt = newAgent("ClientExt", 2, bytes(10,0,2,2))

val l_Request  = newLabel("Request", 1)
val l_Response = newLabel("Response", 2)
val l_Terminate = newLabel("Terminate", 3)

val lst_ClientInt =
    Rec("Outgoing",
        IntChoice(Map(
            (a_ClientExt, l_Request) ->
                ExtChoice(a_ClientExt, Map(l_Response ->
                Var("Outgoing"))),
            (a_ClientExt, l_Terminate) ->
                Rec("Incoming",
                    ExtChoice(a_ClientExt, Map(
                        l_Request ->
                            IntChoice(Map((a_ClientExt, l_Response) ->
                            Var("Incoming"))),
                        l_Terminate ->
                            End()))))))

val lst_ClientExt =
    Rec("Outgoing",
        ExtChoice(a_ClientInt, Map(
            l_Request ->
                IntChoice(Map((a_ClientInt, l_Response) ->
                Var("Outgoing"))),
            l_Terminate ->
                Rec("Incoming",
                    IntChoice(Map(
                        (a_ClientInt, l_Request) ->
                            ExtChoice(a_ClientInt, Map(l_Response ->
                            Var("Incoming"))),
                        (a_ClientInt, l_Terminate) ->
                            End()))))))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1,2), List(1,2))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_clientInt = LST_to_SM(lst_ClientInt, a_ClientInt)
        val msa_clientExt = LST_to_SM(lst_ClientExt, a_ClientExt)

        val monitor_switches = List(
            (s1, restrict(List(a_ClientInt, a_ClientExt),
                    parallel(List(
                        monitor(a_ClientInt, msa_clientInt),
                        monitor(a_ClientExt, msa_clientExt))))))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "Firewall",
    List(a_ClientInt, a_ClientExt),
    List(l_Request, l_Response, l_Terminate)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)