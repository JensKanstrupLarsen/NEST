#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class Client(Role):
    name = "Client"
    id = 1
    ip = "10.0.1.1"

class LocalDNS(Role):
    name = "LocalDNS"
    id = 2
    ip = "10.0.2.2"

class RootDNS(Role):
    name = "RootDNS"
    id = 3
    ip = "10.0.3.3"

class TLDDNS(Role):
    name = "TLDDNS"
    id = 4
    ip = "10.0.4.4"

class AuthDNS(Role):
    name = "AuthDNS"
    id = 5
    ip = "10.0.5.5"

class RqsIP(Message):
    id = 1
    label = "RqsIP"

    def __init__(self, text=""):
       super().__init__(text)

class RspIP(Message):
    id = 2
    label = "RspIP"

    def __init__(self, text=""):
       super().__init__(text)

class RqsRoot(Message):
    id = 3
    label = "RqsRoot"

    def __init__(self, text=""):
       super().__init__(text)

class RspRoot(Message):
    id = 4
    label = "RspRoot"

    def __init__(self, text=""):
       super().__init__(text)

class RqsTLD(Message):
    id = 5
    label = "RqsTLD"

    def __init__(self, text=""):
       super().__init__(text)

class RspTLD(Message):
    id = 6
    label = "RspTLD"

    def __init__(self, text=""):
       super().__init__(text)

class RqsAuth(Message):
    id = 7
    label = "RqsAuth"

    def __init__(self, text=""):
       super().__init__(text)

class RspAuth(Message):
    id = 8
    label = "RspAuth"

    def __init__(self, text=""):
       super().__init__(text)

class DNSSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return RqsIP(session_header.message_text)
        elif (session_header.message_id == 2):
            return RspIP(session_header.message_text)
        elif (session_header.message_id == 3):
            return RqsRoot(session_header.message_text)
        elif (session_header.message_id == 4):
            return RspRoot(session_header.message_text)
        elif (session_header.message_id == 5):
            return RqsTLD(session_header.message_text)
        elif (session_header.message_id == 6):
            return RspTLD(session_header.message_text)
        elif (session_header.message_id == 7):
            return RqsAuth(session_header.message_text)
        elif (session_header.message_id == 8):
            return RspAuth(session_header.message_text)
        else:
            return Message()
