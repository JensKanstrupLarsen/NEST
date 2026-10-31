#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class User(Role):
    name = "User"
    id = 1
    ip = "10.0.1.1"

class LocalDNS(Role):
    name = "LocalDNS"
    id = 2
    ip = "10.10.2.2"

class IntAuthDNS(Role):
    name = "IntAuthDNS"
    id = 3
    ip = "10.10.3.3"

class ExtAuthDNS(Role):
    name = "ExtAuthDNS"
    id = 4
    ip = "10.0.4.4"

class DNSQuery(Message):
    id = 1
    label = "DNSQuery"

    def __init__(self, text=""):
       super().__init__(text)

class AuthQuery(Message):
    id = 2
    label = "AuthQuery"

    def __init__(self, text=""):
       super().__init__(text)

class Hostname(Message):
    id = 3
    label = "Hostname"

    def __init__(self, text=""):
       super().__init__(text)

class IPAddr(Message):
    id = 4
    label = "IPAddr"

    def __init__(self, text=""):
       super().__init__(text)

class CDNSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return DNSQuery(session_header.message_text)
        elif (session_header.message_id == 2):
            return AuthQuery(session_header.message_text)
        elif (session_header.message_id == 3):
            return Hostname(session_header.message_text)
        elif (session_header.message_id == 4):
            return IPAddr(session_header.message_text)
        else:
            return Message()

