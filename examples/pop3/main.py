#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class POPClient(Role):
    name = "POPClient"
    id = 1
    ip = "10.0.1.1"

class POPServer(Role):
    name = "POPServer"
    id = 2
    ip = "10.0.2.2"

class OK(Message):
    id = 1
    label = "OK"

    def __init__(self, text=""):
       super().__init__(text)

class ERR(Message):
    id = 2
    label = "ERR"

    def __init__(self, text=""):
       super().__init__(text)

class USER(Message):
    id = 3
    label = "USER"

    def __init__(self, text=""):
       super().__init__(text)

class PASS(Message):
    id = 4
    label = "PASS"

    def __init__(self, text=""):
       super().__init__(text)

class LIST(Message):
    id = 5
    label = "LIST"

    def __init__(self, text=""):
       super().__init__(text)

class MSGS(Message):
    id = 6
    label = "MSGS"

    def __init__(self, text=""):
       super().__init__(text)

class RETR(Message):
    id = 7
    label = "RETR"

    def __init__(self, text=""):
       super().__init__(text)

class DELE(Message):
    id = 8
    label = "DELE"

    def __init__(self, text=""):
       super().__init__(text)

class NOOP(Message):
    id = 9
    label = "NOOP"

    def __init__(self, text=""):
       super().__init__(text)

class QUIT(Message):
    id = 10
    label = "QUIT"

    def __init__(self, text=""):
       super().__init__(text)

class POP3SessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return OK(session_header.message_text)
        elif (session_header.message_id == 2):
            return ERR(session_header.message_text)
        elif (session_header.message_id == 3):
            return USER(session_header.message_text)
        elif (session_header.message_id == 4):
            return PASS(session_header.message_text)
        elif (session_header.message_id == 5):
            return LIST(session_header.message_text)
        elif (session_header.message_id == 6):
            return MSGS(session_header.message_text)
        elif (session_header.message_id == 7):
            return RETR(session_header.message_text)
        elif (session_header.message_id == 8):
            return DELE(session_header.message_text)
        elif (session_header.message_id == 9):
            return NOOP(session_header.message_text)
        elif (session_header.message_id == 10):
            return QUIT(session_header.message_text)
        else:
            return Message()
