package pop3

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine

val a_POPClient = newAgent("POPClient", 1, bytes(10,0,1,1))
val a_POPServer = newAgent("POPServer", 2, bytes(10,0,2,2))

val l_OK         = newLabel("OK", 1)
val l_ERR        = newLabel("ERR", 2)
val l_Username   = newLabel("USER", 3)
val l_Password   = newLabel("PASS", 4)
val l_ListMsgs   = newLabel("LIST", 5)
val l_Messages   = newLabel("MSGS", 6)
val l_Retransmit = newLabel("RETR", 7)
val l_Delete     = newLabel("DELE", 8)
val l_NoOp       = newLabel("NOOP", 9)
val l_Quit       = newLabel("QUIT", 10)

val lst_POPClient =
    Rec("TryLogin",
        IntChoice(Map((a_POPServer, l_Username) ->
        ExtChoice(a_POPServer, Map(
            l_ERR ->
                Var("TryLogin"),
            l_OK  ->
                IntChoice(Map((a_POPServer, l_Password) ->
                ExtChoice(a_POPServer, Map(
                    l_ERR ->
                        Var("TryLogin"),
                    l_OK ->
                        Rec("ClientQuery",
                            IntChoice(Map(
                                (a_POPServer, l_ListMsgs) ->
                                    ExtChoice(a_POPServer, Map(l_Messages ->
                                    Var("ClientQuery"))),
                                (a_POPServer, l_Retransmit) ->
                                    ExtChoice(a_POPServer, Map(
                                        l_OK ->
                                            Var("ClientQuery"),
                                        l_ERR ->
                                            Var("ClientQuery"))),
                                (a_POPServer, l_Delete) ->
                                    ExtChoice(a_POPServer, Map(
                                        l_OK ->
                                            Var("ClientQuery"),
                                        l_ERR ->
                                            Var("ClientQuery"))),
                                (a_POPServer, l_NoOp) ->
                                    ExtChoice(a_POPServer, Map(l_OK ->
                                    Var("ClientQuery"))),
                                (a_POPServer, l_Quit) ->
                                    ExtChoice(a_POPServer, Map(l_OK ->
                                    End()))))))))))))))

val lst_POPServer =
    Rec("TryLogin",
        ExtChoice(a_POPClient, Map(l_Username ->
        IntChoice(Map(
            (a_POPClient, l_ERR) ->
                Var("TryLogin"),
            (a_POPClient, l_OK) ->
                ExtChoice(a_POPClient, Map(l_Password ->
                IntChoice(Map(
                    (a_POPClient, l_ERR) ->
                        Var("TryLogin"),
                    (a_POPClient, l_OK)  ->
                        Rec("ClientQuery",
                            ExtChoice(a_POPClient, Map(
                                l_ListMsgs ->
                                    IntChoice(Map((a_POPClient, l_Messages) ->
                                    Var("ClientQuery"))),
                                l_Retransmit ->
                                    IntChoice(Map(
                                        (a_POPClient, l_OK) ->
                                            Var("ClientQuery"),
                                        (a_POPClient, l_ERR) ->
                                            Var("ClientQuery"))),
                                l_Delete ->
                                    IntChoice(Map(
                                        (a_POPClient, l_OK) ->
                                            Var("ClientQuery"),
                                        (a_POPClient, l_ERR) ->
                                            Var("ClientQuery"))),
                                l_NoOp ->
                                    IntChoice(Map((a_POPClient, l_OK) ->
                                    Var("ClientQuery"))),
                                l_Quit ->
                                    IntChoice(Map((a_POPClient, l_OK) ->
                                    End()))))))))))))))

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

        val msa_popClient = LST_to_SM(lst_POPClient, a_POPClient)
        val msa_popServer = LST_to_SM(lst_POPServer, a_POPServer)

        val monitor_switches = List(
            (s1, restrict(List(a_POPClient, a_POPServer),
                    parallel(List(
                        monitor(a_POPClient, msa_popClient),
                        monitor(a_POPServer, msa_popServer))))))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "POP3",
    List(a_POPClient, a_POPServer),
    List(l_OK, l_ERR, l_Username, l_Password, l_ListMsgs, l_Messages, l_Retransmit, l_Delete, l_NoOp, l_Quit)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)