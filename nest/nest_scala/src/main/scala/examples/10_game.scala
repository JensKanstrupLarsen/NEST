package game

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine

val a_Coordinator = newAgent("Coordinator", 1, bytes(10,0,1,1))
val a_GameServer  = newAgent("GameServer", 2, bytes(10,0,2,2))
val a_Player1     = newAgent("Player_1", 3, bytes(10,10,3,3))
val a_Player2     = newAgent("Player_2", 4, bytes(10,10,4,4))
// val a_Player3     = newAgent("Player_3", 5, bytes(10,10,5,5))
// val a_Player4     = newAgent("Player_4", 6, bytes(10,10,6,6))

val l_Init        = newLabel("Init", 10)
val l_LookForGame = newLabel("LookForGame", 1)
val l_GameFound   = newLabel("GameFound", 2)
val l_TurnStart   = newLabel("TurnStart", 3)
val l_Move        = newLabel("Move", 4)
val l_TakeAction  = newLabel("TakeAction", 5)
val l_Victory     = newLabel("YouWon", 6)
val l_Defeat      = newLabel("YouLost", 7)
val l_GameOver    = newLabel("GameOver", 8)

val lst_Coordinator =
    IntChoice(Map((a_Player1, l_Init) ->
    IntChoice(Map((a_Player2, l_Init) ->
    ExtChoice(a_Player1, Map(l_LookForGame ->
    ExtChoice(a_Player2, Map(l_LookForGame ->
    IntChoice(Map((a_Player1, l_GameFound) ->
    IntChoice(Map((a_Player2, l_GameFound) ->
    IntChoice(Map((a_GameServer, l_GameFound) ->
    ExtChoice(a_GameServer, Map(l_GameOver ->
    End()))))))))))))))))

val lst_GameServer =
    ExtChoice(a_Coordinator, Map(l_GameFound ->
    Rec("GameRound",
        IntChoice(Map(
            (a_Player1, l_TurnStart) ->
                ExtChoice(a_Player1, Map(l_TakeAction ->
                IntChoice(Map((a_Player2, l_TurnStart) ->
                ExtChoice(a_Player2, Map(l_TakeAction ->
                Var("GameRound"))))))),
            (a_Player1, l_Victory) ->
                IntChoice(Map((a_Player2, l_Defeat) ->
                IntChoice(Map((a_Coordinator, l_GameOver) ->
                End())))),
            (a_Player1, l_Defeat) ->
                IntChoice(Map((a_Player2, l_Victory) ->
                IntChoice(Map((a_Coordinator, l_GameOver) ->
                End())))))))))

val lst_Player =
    ExtChoice(a_Coordinator, Map(l_Init ->
    IntChoice(Map((a_Coordinator, l_LookForGame) ->
    ExtChoice(a_Coordinator, Map(l_GameFound ->
    Rec("GameRound",
        ExtChoice(a_GameServer, Map(
            l_TurnStart ->
                IntChoice(Map((a_GameServer, l_TakeAction) ->
                Var("GameRound"))),
            l_Victory ->
                End(),
            l_Defeat ->
                End())))))))))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
    val s2 = constructSwitch(1, "127.0.0.1", 50052, isTCP)
    val s3 = constructSwitch(2, "127.0.0.1", 50053, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s1, bytes(10,10), bytes(8,0,0,0,0,0), bytes(3)),

        (s2, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s2, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(1)),
        (s2, bytes(10,10), bytes(8,0,0,0,0,0), bytes(3)),

        (s3, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s3, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s3, bytes(10,10,3,3), bytes(8,0,0,0,3,51), bytes(3)),
        (s3, bytes(10,10,4,4), bytes(8,0,0,0,4,68), bytes(4)))
        // (s3, bytes(10,10,5,5), bytes(8,0,0,0,5,85), bytes(5)),
        // (s3, bytes(10,10,6,6), bytes(8,0,0,0,6,102), bytes(6)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1), List(1,2,3))
            ++ genPortPairs(s2, List(1), List(1,2,3))
            ++ genPortPairs(s3, List(3,4), List(1,2,3,4))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_coordinator = LST_to_SM(lst_Coordinator, a_Coordinator)
        val msa_gameserver  = LST_to_SM(lst_GameServer, a_GameServer)
        val msa_player1     = LST_to_SM(lst_Player, a_Player1)
        val msa_player2     = LST_to_SM(lst_Player, a_Player2)
        // val msa_player3     = LST_to_SM(lst_Player, a_Player3)
        // val msa_player4     = LST_to_SM(lst_Player, a_Player4)

        val monitor_switches = List(
            (s1, monitor(a_Coordinator, msa_coordinator)),
            (s2, monitor(a_GameServer, msa_gameserver)),
            (s3, restrict(List(a_Player1, a_Player2),
                    parallel(List(
                        monitor(a_Player1, msa_player1),
                        monitor(a_Player2, msa_player2))))))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()
    s2.disconnect()
    s3.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "Game",
    List(a_Coordinator, a_GameServer, a_Player1, a_Player2),
    List(l_LookForGame, l_GameFound, l_TurnStart, l_Move, l_TakeAction, l_Victory, l_Defeat, l_Init, l_GameOver)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)