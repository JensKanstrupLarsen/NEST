#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import GameSessionHandler, Coordinator, GameServer, Player_1, Player_2, Init, GameFound, TurnStart, TakeAction, Move, YouWon, YouLost, GameOver

def playerAct(s, player):
    s.sendMsg(player, TurnStart())
    s.recvMsg(player, TakeAction)

def protocol(s):
    s.recvMsg(Coordinator, GameFound)
    totalRounds = 1
    while (totalRounds <= 5):
        playerAct(s, Player_1)
        playerAct(s, Player_2)
        totalRounds += 1
    s.sendMsg(Player_1, YouLost())
    s.sendMsg(Player_2, YouWon())
    s.sendMsg(Coordinator, GameOver())

if __name__ == '__main__':
    role = GameServer
    sh = GameSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
