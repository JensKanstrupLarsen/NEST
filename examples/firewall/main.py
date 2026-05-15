#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class ClientInt(Role):
    name = "ClientInt"
    id = 1
    ip = "10.0.1.1"

class ClientExt(Role):
    name = "ClientExt"
    id = 2
    ip = "10.0.2.2"

class Request(Message):
    id = 1
    label = "Request"

    def __init__(self, text=""):
       super().__init__(text)

class Response(Message):
    id = 2
    label = "Response"

    def __init__(self, text=""):
       super().__init__(text)

class Terminate(Message):
    id = 3
    label = "Terminate"

    def __init__(self, text=""):
       super().__init__(text)

class FirewallSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return Request(session_header.message_text)
        elif (session_header.message_id == 2):
            return Response(session_header.message_text)
        elif (session_header.message_id == 3):
            return Terminate(session_header.message_text)
        else:
            return Message()
