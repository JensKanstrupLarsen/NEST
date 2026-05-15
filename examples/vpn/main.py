#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class Authenticator(Role):
    name = "Authenticator"
    id = 1
    ip = "10.0.1.1"

class Client_A(Role):
    name = "Client_A"
    id = 2
    ip = "10.0.2.2"

class Client_B(Role):
    name = "Client_B"
    id = 3
    ip = "10.0.3.3"

class Client_C(Role):
    name = "Client_C"
    id = 4
    ip = "10.0.4.4"

class Init(Message):
    id = 10
    label = "Init"

    def __init__(self, text=""):
        super().__init__(text)

class GetAuth(Message):
    id = 1
    label = "GetAuth"

    def __init__(self, text=""):
        super().__init__(text)

class Acc(Message):
    id = 2
    label = "Acc"

    def __init__(self, text=""):
        super().__init__(text)

class Deny(Message):
    id = 3
    label = "Deny"

    def __init__(self, text=""):
        super().__init__(text)

class Rqs(Message):
    id = 4
    label = "Rqs"

    def __init__(self, text=""):
        super().__init__(text)

class Rsp(Message):
    id = 5
    label = "Rsp"

    def __init__(self, text=""):
        super().__init__(text)

class Term(Message):
    id = 6
    label = "Term"

    def __init__(self, text=""):
        super().__init__(text)

class VPNSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return GetAuth(session_header.message_text)
        elif (session_header.message_id == 2):
            return Acc(session_header.message_text)
        elif (session_header.message_id == 3):
            return Deny(session_header.message_text)
        elif (session_header.message_id == 4):
            return Rqs(session_header.message_text)
        elif (session_header.message_id == 5):
            return Rsp(session_header.message_text)
        elif (session_header.message_id == 6):
            return Term(session_header.message_text)
        elif (session_header.message_id == 10):
            return Init(session_header.message_text)
        else:
            return Message()
