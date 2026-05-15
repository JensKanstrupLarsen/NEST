package sip

import controlplane._
import sessiontypes.LocalSessionType._
import sessiontypes._
import p4rtype._
import scala.io.StdIn.readLine

val a_SIPClientA = newAgent("ClientA", 1, bytes(10,0,1,1))
val a_SIPClientB = newAgent("ClientB", 2, bytes(10,0,2,2))
val a_SIPProxy  = newAgent("Proxy", 3, bytes(10,0,3,3))

val l_INVITE  = newLabel("INVITE",  1)
val l_Trying  = newLabel("Trying",  2)
val l_Ringing = newLabel("Ringing", 3)
val l_OK      = newLabel("OK", 4)
val l_Error   = newLabel("Error",   5)

val lst_SIPClientA =
    IntChoice(Map((a_SIPProxy, l_INVITE) ->
    ExtChoice(a_SIPProxy, Map(
        l_Trying ->
            ExtChoice(a_SIPProxy, Map(
                l_Ringing ->
                    ExtChoice(a_SIPProxy, Map(
                        l_OK ->
                            End(),
                        l_Error ->
                            End())),
                l_Error ->
                    End())),
        l_Error ->
            End()))))

val lst_SIPClientB =
    ExtChoice(a_SIPProxy, Map(
        l_INVITE -> IntChoice(Map(
            (a_SIPProxy, l_Ringing) ->
                IntChoice(Map(
                    (a_SIPProxy, l_OK) ->
                        End(),
                    (a_SIPProxy, l_Error) ->
                        End())),
            (a_SIPProxy, l_Error) ->
                End()))))

val lst_SIPProxy =
    ExtChoice(a_SIPClientA, Map(l_INVITE ->
    IntChoice(Map(
        (a_SIPClientB, l_INVITE) ->
            IntChoice(Map((a_SIPClientA, l_Trying) ->
            ExtChoice(a_SIPClientB, Map(
                l_Ringing ->
                    IntChoice(Map((a_SIPClientA, l_Ringing) ->
                    ExtChoice(a_SIPClientB, Map(
                        l_OK ->
                            IntChoice(Map((a_SIPClientA, l_OK) ->
                            End())),
                        l_Error ->
                            IntChoice(Map((a_SIPClientA, l_Error) ->
                            End())))))),
                l_Error ->
                    IntChoice(Map((a_SIPClientA, l_Error) ->
                    End())))))),
        (a_SIPClientA, l_Error) ->
            End()))))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s1, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(3)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs = genPortPairs(s1, List(1,2,3), List(1,2,3))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_SIPClientA = LST_to_SM(lst_SIPClientA, a_SIPClientA)
        val msa_SIPClientB = LST_to_SM(lst_SIPClientB, a_SIPClientB)
        val msa_SIPProxy   = LST_to_SM(lst_SIPProxy,   a_SIPProxy)

        val monitor_switches = List(
            (s1, restrict(List(a_SIPClientA, a_SIPClientB, a_SIPProxy),
                    parallel(List(
                        monitor(a_SIPClientA, msa_SIPClientA),
                        monitor(a_SIPClientB, msa_SIPClientB),
                        monitor(a_SIPProxy,   msa_SIPProxy))))))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "SIP",
    List(a_SIPClientA, a_SIPClientB, a_SIPProxy),
    List(l_INVITE, l_Trying, l_Ringing, l_OK, l_Error)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)