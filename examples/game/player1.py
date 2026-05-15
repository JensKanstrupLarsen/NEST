#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys

from main import GameSessionHandler, Coordinator, GameServer, Player_1, Player_2, Init, LookForGame, GameFound, TurnStart, TakeAction, Move, YouWon, YouLost, GameOver

def protocol(s):
    s.recvMsg(Coordinator, Init)
    s.sendMsg(Coordinator, LookForGame())
    s.recvMsg(Coordinator, GameFound)
    gameOver = False
    while (not gameOver):
        gameStatus = s.recvMsg(GameServer)
        if (type(gameStatus) == TurnStart):
            s.sendMsg(GameServer, TakeAction("act"))
        elif (type(gameStatus) == YouWon or type(gameStatus) == YouLost):
            gameOver = True

if __name__ == '__main__':
    role = Player_1
    sh = GameSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
