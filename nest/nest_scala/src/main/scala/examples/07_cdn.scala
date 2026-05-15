package cdn

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine

val a_User       = newAgent("User", 1, bytes(10,0,1,1))
val a_LocalDNS   = newAgent("LocalDNS", 2, bytes(10,10,2,2))
val a_IntServer = newAgent("IntAuthDNS", 3, bytes(10,10,3,3))
val a_ExtServer = newAgent("ExtAuthDNS", 4, bytes(10,0,4,4))

val l_DNSQuery   = newLabel("DNSQuery", 1)
val l_AuthQuery  = newLabel("AuthQuery", 2)
val l_Hostname   = newLabel("Hostname", 3)
val l_IPAddr     = newLabel("IPAddr", 4)

val lst_User =
    IntChoice(Map((a_LocalDNS, l_DNSQuery) ->
    ExtChoice(a_LocalDNS, Map(l_IPAddr ->
    End()))))

val lst_LocalDNS =
    ExtChoice(a_User, Map(l_DNSQuery ->
    IntChoice(Map((a_IntServer, l_AuthQuery) ->
    ExtChoice(a_IntServer, Map(l_Hostname ->
    IntChoice(Map((a_ExtServer, l_AuthQuery) ->
    ExtChoice(a_ExtServer, Map(l_IPAddr ->
    IntChoice(Map((a_User, l_IPAddr) ->
    End()))))))))))))

val lst_IntAuthDNS =
    ExtChoice(a_LocalDNS, Map(l_AuthQuery ->
    IntChoice(Map((a_LocalDNS, l_Hostname) ->
    End()))))

val lst_ExtAuthDNS =
    ExtChoice(a_LocalDNS, Map(l_AuthQuery ->
    IntChoice(Map((a_LocalDNS, l_IPAddr) ->
    End()))))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
    val s2 = constructSwitch(1, "127.0.0.1", 50052, isTCP)
    val s3 = constructSwitch(2, "127.0.0.1", 50053, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,10), bytes(8,0,0,0,0,0), bytes(2)),
        (s1, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(3)),

        (s2, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(4)),
        (s2, bytes(10,10,2,2), bytes(8,0,0,0,2,34), bytes(1)),
        (s2, bytes(10,10,3,3), bytes(8,0,0,0,3,51), bytes(2)),
        (s2, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(3)),

        (s3, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s3, bytes(10,10), bytes(8,0,0,0,0,0), bytes(3)),
        (s3, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(1)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1), List(1,2,3))
            ++ genPortPairs(s2, List(1,2), List(1,2,3,4))
            ++ genPortPairs(s3, List(1), List(1,2,3))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_user       = LST_to_SM(lst_User, a_User)
        val msa_localDNS   = LST_to_SM(lst_LocalDNS, a_LocalDNS)
        val msa_IntServer = LST_to_SM(lst_IntAuthDNS, a_IntServer)
        val msa_ExtServer = LST_to_SM(lst_ExtAuthDNS, a_ExtServer)

        val monitor_switches = List(
            (s1, monitor(a_User, msa_user)),
            (s2, restrict(List(a_LocalDNS, a_IntServer),
                    parallel(List(
                        monitor(a_LocalDNS, msa_localDNS),
                        monitor(a_IntServer, msa_IntServer))))),
            (s3, monitor(a_ExtServer, msa_ExtServer)))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()
    s2.disconnect()
    s3.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "CDN",
    List(a_User, a_LocalDNS, a_IntServer, a_ExtServer),
    List(l_DNSQuery, l_AuthQuery, l_Hostname, l_IPAddr)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)