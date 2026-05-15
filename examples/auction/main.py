#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class Auction(Role):
    name = "Auction"
    id = 1
    ip = "10.0.1.1"

class BuyerA(Role):
    name = "BuyerA"
    id = 2
    ip = "10.0.2.2"

class BuyerB(Role):
    name = "BuyerB"
    id = 3
    ip = "10.0.3.3"

class Init(Message):
    id = 1
    label = "Init"

    def __init__(self, text=""):
       super().__init__(text)

class Bid(Message):
    id = 2
    label = "Bid"

    def __init__(self, text=""):
       super().__init__(text)

class Confirm(Message):
    id = 3
    label = "Confirm"

    def __init__(self, text=""):
       super().__init__(text)

class Resend(Message):
    id = 4
    label = "Resend"

    def __init__(self, text=""):
       super().__init__(text)

class Winner(Message):
    id = 5
    label = "Winner"

    def __init__(self, text=""):
       super().__init__(text)

class SendItem(Message):
    id = 7
    label = "SendItem"

    def __init__(self, text=""):
       super().__init__(text)

class Pay(Message):
    id = 8
    label = "Pay"

    def __init__(self, text=""):
       super().__init__(text)

class Loser(Message):
    id = 6
    label = "Loser"

    def __init__(self, text=""):
       super().__init__(text)

class AuctionSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)
        self.mss = 14

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return Init(session_header.message_text)
        elif (session_header.message_id == 2):
            return Bid(session_header.message_text)
        elif (session_header.message_id == 3):
            return Confirm(session_header.message_text)
        elif (session_header.message_id == 4):
            return Resend(session_header.message_text)
        elif (session_header.message_id == 5):
            return Winner(session_header.message_text)
        elif (session_header.message_id == 7):
            return SendItem(session_header.message_text)
        elif (session_header.message_id == 8):
            return Pay(session_header.message_text)
        elif (session_header.message_id == 6):
            return Loser(session_header.message_text)
        else:
            return Message()
