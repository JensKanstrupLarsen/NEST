package auction

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine

val a_Auction = newAgent("Auction", 1, bytes(10,0,1,1))
val a_BuyerA  = newAgent("BuyerA", 2, bytes(10,0,2,2))
val a_BuyerB  = newAgent("BuyerB", 3, bytes(10,0,3,3))

val l_Init      = newLabel("Init", 1)
val l_Bid       = newLabel("Bid", 2)
val l_Confirm   = newLabel("Confirm", 3)
val l_Resend    = newLabel("Resend", 4)
val l_Winner    = newLabel("Winner", 5)
val l_Loser     = newLabel("Loser", 6)
val l_SendItem  = newLabel("SendItem", 7)
val l_Pay       = newLabel("Pay", 8)

val lst_Auction =
    IntChoice(Map((a_BuyerA, l_Init) ->
    IntChoice(Map((a_BuyerB, l_Init) ->
    Rec("GetBids",
        ExtChoice(a_BuyerA, Map(l_Bid ->
        ExtChoice(a_BuyerA, Map(l_Confirm ->
        ExtChoice(a_BuyerB, Map(l_Bid ->
        ExtChoice(a_BuyerB, Map(l_Confirm ->
        IntChoice(Map(
            (a_BuyerA, l_Resend) ->
                IntChoice(Map((a_BuyerB, l_Resend) ->
                Var("GetBids"))),
            (a_BuyerA, l_Winner) ->
                IntChoice(Map((a_BuyerB, l_Loser) ->
                ExtChoice(a_BuyerA, Map(l_Pay ->
                IntChoice(Map((a_BuyerA, l_SendItem) ->
                End())))))),
            (a_BuyerA, l_Loser) ->
                IntChoice(Map((a_BuyerB, l_Winner) ->
                ExtChoice(a_BuyerB, Map(l_Pay ->
                IntChoice(Map((a_BuyerB, l_SendItem) ->
                End())))))))))))))))))))))

val lst_BuyerA =
    ExtChoice(a_Auction, Map(l_Init ->
    Rec("SendBids",
        IntChoice(Map((a_Auction, l_Bid) ->
        IntChoice(Map((a_Auction, l_Confirm) ->
        ExtChoice(a_Auction, Map(
            l_Resend ->
                Var("SendBids"),
            l_Winner ->
                IntChoice(Map((a_Auction, l_Pay) ->
                ExtChoice(a_Auction, Map(l_SendItem ->
                End())))),
            l_Loser ->
                End())))))))))

val lst_BuyerB =
    ExtChoice(a_Auction, Map(l_Init ->
    Rec("SendBids",
        IntChoice(Map((a_Auction, l_Bid) ->
        IntChoice(Map((a_Auction, l_Confirm) ->
        ExtChoice(a_Auction, Map(
            l_Resend ->
                Var("SendBids"),
            l_Winner ->
                IntChoice(Map((a_Auction, l_Pay) ->
                ExtChoice(a_Auction, Map(l_SendItem ->
                End())))),
            l_Loser ->
                End())))))))))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
    val s2 = constructSwitch(1, "127.0.0.1", 50052, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s1, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)),

        (s2, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(3)),
        (s2, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(1)),
        (s2, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1), List(1,2))
            ++ genPortPairs(s2, List(1,2), List(1,2,3))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_auction = LST_to_SM(lst_Auction, a_Auction)
        val msa_buyerA  = LST_to_SM(lst_BuyerA, a_BuyerA)
        val msa_buyerB  = LST_to_SM(lst_BuyerB, a_BuyerB)

        val monitor_switches = List(
            (s1, monitor(a_Auction, msa_auction)),
            (s2, restrict(List(a_BuyerA, a_BuyerB),
                    parallel(List(
                        monitor(a_BuyerA, msa_buyerA),
                        monitor(a_BuyerB, msa_buyerB))))))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()
    s2.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "Auction",
    List(a_Auction, a_BuyerA, a_BuyerB),
    List(l_Init, l_Bid, l_Confirm,  l_Resend, l_Winner, l_SendItem, l_Pay, l_Loser)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)