package dns

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine
import sessiontypes.CompositeMonitor._
import vpn.a_Auth

val a_DNSClient   = STAgent("Client", bytes(1), bytes(10,0,1,1))
val a_LocalDNS = STAgent("LocalDNS", bytes(2), bytes(10,0,2,2))
val a_RootDNS  = STAgent("RootDNS", bytes(3), bytes(10,0,3,3))
val a_TLDDNS   = STAgent("TLDDNS", bytes(4), bytes(10,0,4,4))
val a_AuthDNS  = STAgent("AuthDNS", bytes(5), bytes(10,0,5,5))

val l_RequestIP    = newLabel("RqsIP",   1)
val l_ResponseIP   = newLabel("RspIP",   2)
val l_RequestRoot  = newLabel("RqsRoot", 3)
val l_ResponseRoot = newLabel("RspRoot", 4)
val l_RequestTLD   = newLabel("RqsTLD",  5)
val l_ResponseTLD  = newLabel("RspTLD",  6)
val l_RequestAuth  = newLabel("RqsAuth", 7)
val l_ResponseAuth = newLabel("RspAuth", 8)

val lst_DNSClient =
    IntChoice(Map((a_LocalDNS, l_RequestIP) ->
    ExtChoice(a_LocalDNS, Map(l_ResponseIP ->
    End()))))

val lst_LocalDNS =
    ExtChoice(a_DNSClient, Map(l_RequestIP ->
    IntChoice(Map((a_RootDNS, l_RequestRoot) ->
    ExtChoice(a_RootDNS, Map(l_ResponseRoot ->
    IntChoice(Map((a_TLDDNS, l_RequestTLD) ->
    ExtChoice(a_TLDDNS, Map(l_ResponseTLD ->
    IntChoice(Map((a_AuthDNS, l_RequestAuth) ->
    ExtChoice(a_AuthDNS, Map(l_ResponseAuth ->
    IntChoice(Map((a_DNSClient, l_ResponseIP) ->
    End()))))))))))))))))

val lst_RootDNS =
    ExtChoice(a_LocalDNS, Map(l_RequestRoot ->
    IntChoice(Map((a_LocalDNS, l_ResponseRoot) ->
    End()))))

val lst_TLDDNS =
    ExtChoice(a_LocalDNS, Map(l_RequestTLD ->
    IntChoice(Map((a_LocalDNS, l_ResponseTLD) ->
    End()))))

val lst_AuthDNS =
    ExtChoice(a_LocalDNS, Map(l_RequestAuth ->
    IntChoice(Map((a_LocalDNS, l_ResponseAuth) ->
    End()))))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
    val s2 = constructSwitch(1, "127.0.0.1", 50052, isTCP)
    val s3 = constructSwitch(2, "127.0.0.1", 50053, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s1, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(3)),
        (s1, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(3)),
        (s1, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(3)),

        (s2, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(3)),
        (s2, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(1)),
        (s2, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)),
        (s2, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),
        (s2, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(2)),

        (s3, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(4)),
        (s3, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(5)),
        (s3, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(1)),
        (s3, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),
        (s3, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(3)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1), List(1,2,3))
            ++ genPortPairs(s2, List(1), List(1,2,3))
            ++ genPortPairs(s3, List(1,2,3), List(1,2,3,4,5))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_client = LST_to_SM(lst_DNSClient, a_DNSClient)
        val msa_local  = LST_to_SM(lst_LocalDNS, a_LocalDNS)
        val msa_root   = LST_to_SM(lst_RootDNS, a_RootDNS)
        val msa_TLD    = LST_to_SM(lst_TLDDNS, a_TLDDNS)
        val msa_auth   = LST_to_SM(lst_AuthDNS, a_AuthDNS)

        val monitor_switches = List(
            (s1, monitor(a_DNSClient, msa_client)),
            (s2, monitor(a_LocalDNS, msa_local)),
            (s3, restrict(List(a_RootDNS, a_TLDDNS, a_AuthDNS),
                    parallel(List(
                        monitor(a_RootDNS, msa_root),
                        monitor(a_TLDDNS, msa_TLD),
                        monitor(a_AuthDNS, msa_auth))))))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()
    s2.disconnect()
    s3.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "DNS",
    List(a_DNSClient, a_LocalDNS, a_RootDNS, a_TLDDNS, a_AuthDNS),
    List(l_RequestIP, l_ResponseIP, l_RequestRoot, l_ResponseRoot, l_RequestTLD, l_ResponseTLD, l_RequestAuth, l_ResponseAuth)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)