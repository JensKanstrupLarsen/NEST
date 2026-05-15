#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class ClientA(Role):
    name = "ClientA"
    id = 1
    ip = "10.0.1.1"

class ClientB(Role):
    name = "ClientB"
    id = 2
    ip = "10.0.2.2"

class Proxy(Role):
    name = "Proxy"
    id = 3
    ip = "10.0.3.3"

class INVITE(Message):
    id = 1
    label = "INVITE"

    def __init__(self, text=""):
       super().__init__(text)

class Trying(Message):
    id = 2
    label = "Trying"

    def __init__(self, text=""):
       super().__init__(text)

class Ringing(Message):
    id = 3
    label = "Ringing"

    def __init__(self, text=""):
       super().__init__(text)

class OK(Message):
    id = 4
    label = "OK"

    def __init__(self, text=""):
       super().__init__(text)

class Error(Message):
    id = 5
    label = "Error"

    def __init__(self, text=""):
       super().__init__(text)

class SIPSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return INVITE(session_header.message_text)
        elif (session_header.message_id == 2):
            return Trying(session_header.message_text)
        elif (session_header.message_id == 3):
            return Ringing(session_header.message_text)
        elif (session_header.message_id == 4):
            return OK(session_header.message_text)
        elif (session_header.message_id == 5):
            return Error(session_header.message_text)
        else:
            return Message()
