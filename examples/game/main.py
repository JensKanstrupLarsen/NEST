#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class Coordinator(Role):
    name = "Coordinator"
    id = 1
    ip = "10.0.1.1"

class GameServer(Role):
    name = "GameServer"
    id = 2
    ip = "10.0.2.2"

class Player_1(Role):
    name = "Player_1"
    id = 3
    ip = "10.10.3.3"

class Player_2(Role):
    name = "Player_2"
    id = 4
    ip = "10.10.4.4"

class LookForGame(Message):
    id = 1
    label = "LookForGame"

    def __init__(self, text=""):
       super().__init__(text)

class GameFound(Message):
    id = 2
    label = "GameFound"

    def __init__(self, text=""):
       super().__init__(text)

class TurnStart(Message):
    id = 3
    label = "TurnStart"

    def __init__(self, text=""):
       super().__init__(text)

class Move(Message):
    id = 4
    label = "Move"

    def __init__(self, text=""):
       super().__init__(text)

class TakeAction(Message):
    id = 5
    label = "TakeAction"

    def __init__(self, text=""):
       super().__init__(text)

class YouWon(Message):
    id = 6
    label = "YouWon"

    def __init__(self, text=""):
       super().__init__(text)

class YouLost(Message):
    id = 7
    label = "YouLost"

    def __init__(self, text=""):
       super().__init__(text)

class Init(Message):
    id = 10
    label = "Init"

    def __init__(self, text=""):
       super().__init__(text)

class GameOver(Message):
    id = 8
    label = "GameOver"

    def __init__(self, text=""):
       super().__init__(text)

class GameSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return LookForGame(session_header.message_text)
        elif (session_header.message_id == 2):
            return GameFound(session_header.message_text)
        elif (session_header.message_id == 3):
            return TurnStart(session_header.message_text)
        elif (session_header.message_id == 4):
            return Move(session_header.message_text)
        elif (session_header.message_id == 5):
            return TakeAction(session_header.message_text)
        elif (session_header.message_id == 6):
            return YouWon(session_header.message_text)
        elif (session_header.message_id == 7):
            return YouLost(session_header.message_text)
        elif (session_header.message_id == 10):
            return Init(session_header.message_text)
        elif (session_header.message_id == 8):
            return GameOver(session_header.message_text)
        else:
            return Message()
