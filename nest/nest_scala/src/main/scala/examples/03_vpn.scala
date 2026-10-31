package vpn

import tcp_monitor._
import scala.annotation.switch
import java.util.Dictionary
import sessiontypes._
import p4rtype._
import controlplane._
import scala.io.StdIn.readLine
import sessiontypes.CompositeMonitor._
import sessiontypes.LocalSessionType._

val a_Auth    = STAgent("Auth", bytes(1), bytes(10,0,1,1))
val a_ClientA = STAgent("C_A", bytes(2), bytes(10,0,2,2))
val a_ClientB = STAgent("C_B", bytes(3), bytes(10,0,3,3))
val a_ClientC = STAgent("C_C", bytes(4), bytes(10,0,4,4))

val l_Init           = newLabel("Init", 10)
val l_Auth           = newLabel("GetAuth", 1)
val l_Accept         = newLabel("Acc",     2)
val l_Deny           = newLabel("Deny",    3)
val l_ClientRequest  = newLabel("Rqs",     4)
val l_ClientResponse = newLabel("Rsp",     5)
val l_Terminate      = newLabel("Term",    6)

val lst_Auth =
    IntChoice(Map((a_ClientA, l_Init) ->
    IntChoice(Map((a_ClientB, l_Init) ->
    IntChoice(Map((a_ClientC, l_Init) ->
    Rec("acceptOrDeny",
        ExtChoice(a_ClientA, Map(l_Auth ->
        ExtChoice(a_ClientB, Map(l_Auth ->
        ExtChoice(a_ClientC, Map(l_Auth ->
        IntChoice(Map(
            (a_ClientA, l_Accept) ->
                IntChoice(Map((a_ClientB, l_Accept) ->
                IntChoice(Map((a_ClientC, l_Accept) ->
                ExtChoice(a_ClientA, Map(l_Terminate ->
//                ExtChoice(a_ClientB, Map(l_Terminate ->
//                ExtChoice(a_ClientC, Map(l_Terminate ->
                End())))))),
            (a_ClientA, l_Deny) ->
                IntChoice(Map((a_ClientB, l_Deny) ->
                IntChoice(Map((a_ClientC, l_Deny) ->
                Var("acceptOrDeny"))))))))))))))))))))

val lst_ClientA =
    ExtChoice(a_Auth, Map(l_Init ->
    Rec("tryAuth",
        IntChoice(Map((a_Auth, l_Auth) ->
        ExtChoice(a_Auth, Map(
            l_Deny ->
                Var("tryAuth"),
            l_Accept ->
                Rec("requestB",
                    IntChoice(Map(
                        (a_ClientB, l_ClientRequest) ->
                            ExtChoice(a_ClientB, Map(l_ClientResponse ->
                            Var("requestB"))),
                        (a_ClientB, l_Terminate) ->
                            Rec("requestC",
                                IntChoice(Map(
                                    (a_ClientC, l_ClientRequest) ->
                                        ExtChoice(a_ClientC, Map(l_ClientResponse ->
                                        Var("requestC"))),
                                    (a_ClientC, l_Terminate) ->
                                        IntChoice(Map((a_Auth, l_Terminate) ->
                                        End())))))))))))))))

val lst_ClientB =
    ExtChoice(a_Auth, Map(l_Init ->
    Rec("tryAuth",
        IntChoice(Map((a_Auth, l_Auth) ->
        ExtChoice(a_Auth, Map(
            l_Deny ->
                Var("tryAuth"),
            l_Accept ->
                Rec("responseA",
                    ExtChoice(a_ClientA, Map(
                        l_ClientRequest ->
                            IntChoice(Map((a_ClientA, l_ClientResponse) ->
                            Var("responseA"))),
                        l_Terminate ->
//                            IntChoice(Map((a_Auth, l_Terminate) ->
                            End()))))))))))

val lst_ClientC =
    ExtChoice(a_Auth, Map(l_Init ->
    Rec("tryAuth",
        IntChoice(Map((a_Auth, l_Auth) ->
        ExtChoice(a_Auth, Map(
            l_Deny ->
                Var("tryAuth"),
            l_Accept ->
                Rec("responseA",
                    ExtChoice(a_ClientA, Map(
                        l_ClientRequest ->
                            IntChoice(Map((a_ClientA, l_ClientResponse) ->
                            Var("responseA"))),
                        l_Terminate ->
//                            IntChoice(Map((a_Auth, l_Terminate) ->
                            End()))))))))))

@main def debug() =
    val questionable = Rec("rec", ExtChoice(a_ClientA, Map(
        l_Auth -> ExtChoice(a_ClientB, Map(
            l_Auth -> Var("rec"))))))
    printDotFromMSA(LST_to_SM(questionable, a_Auth))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
    val s2 = constructSwitch(1, "127.0.0.1", 50052, isTCP)
    val s3 = constructSwitch(2, "127.0.0.1", 50053, isTCP)

    // Always routes clockwise
    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(4)),
        (s1, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)),
        (s1, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),

        (s2, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s2, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s2, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(1)),
        (s2, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),

        (s3, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s3, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s3, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)),
        (s3, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(1)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1,4), List(1,2,3,4))
            ++ genPortPairs(s2, List(1), List(1,2,3))
            ++ genPortPairs(s3, List(1), List(1,2,3))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_auth    = LST_to_SM(lst_Auth, a_Auth)
        val msa_clientA = LST_to_SM(lst_ClientA, a_ClientA)
        val msa_clientB = LST_to_SM(lst_ClientB, a_ClientB)
        val msa_clientC = LST_to_SM(lst_ClientC, a_ClientC)

        val monitor_switches = List(
            (s1, restrict(List(a_Auth, a_ClientA),
                    parallel(List(
                        monitor(a_Auth, msa_auth),
                        monitor(a_ClientA, msa_clientA))))),
            (s2, monitor(a_ClientB, msa_clientB)),
            (s3, monitor(a_ClientC, msa_clientC)))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()
    s2.disconnect()
    s3.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "VPN",
    List(a_Auth, a_ClientA, a_ClientB, a_ClientC),
    List(l_Init, l_Auth, l_Accept, l_Deny, l_ClientRequest, l_ClientResponse, l_Terminate)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)

/*
Status:
    h1 (ClientA+Auth):  Waiting for Client B auth retransmit
    h2 (ClientB):       Received reject, should be resending auth...?
    h3 (ClientC):       Resending auth
*/