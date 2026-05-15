#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import GameSessionHandler, Coordinator, GameServer, Player_1, Player_2, Init, LookForGame, GameFound, GameOver

def protocol(s):
    s.sendMsg(Player_1, Init("1"))
    s.sendMsg(Player_2, Init("2"))
    s.recvMsg(Player_1, LookForGame)
    s.recvMsg(Player_2, LookForGame)
    s.sendMsg(Player_1, GameFound("id"))
    s.sendMsg(Player_2, GameFound("id"))
    s.sendMsg(GameServer, GameFound("id"))
    s.recvMsg(GameServer, GameOver)

if __name__ == '__main__':
    role = Coordinator
    sh = GameSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role])
    sh.closeAll()
