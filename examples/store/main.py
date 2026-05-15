#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class ExternalClient(Role):
    name = "ExternalClient"
    id = 1
    ip = "10.10.1.1"

class HTTP_Gateway(Role):
    name = "HTTP_Gateway"
    id = 2
    ip = "10.0.2.2"

class Payment(Role):
    name = "Payment"
    id = 4
    ip = "10.20.4.4"

class Inventory(Role):
    name = "Inventory"
    id = 3
    ip = "10.20.3.3"

class Shipping(Role):
    name = "Shipping"
    id = 5
    ip = "10.20.5.5"

class ExternalBank(Role):
    name = "ExternalBank"
    id = 6
    ip = "10.10.6.6"

class ExternalMailserver(Role):
    name = "ExternalMailserver"
    id = 7
    ip = "10.10.7.7"

class PlaceOrder(Message):
    id = 1
    label = "PlaceOrder"

    def __init__(self, text=""):
       super().__init__(text)

class GetSKUDetails(Message):
    id = 2
    label = "GetSKUDetails"

    def __init__(self, text=""):
       super().__init__(text)

class SKUDetails(Message):
    id = 3
    label = "SKUDetails"

    def __init__(self, text=""):
       super().__init__(text)

class ConfirmOrder(Message):
    id = 4
    label = "ConfirmOrder"

    def __init__(self, text=""):
       super().__init__(text)

class VerifyCC(Message):
    id = 5
    label = "VerifyCC"

    def __init__(self, text=""):
       super().__init__(text)

class ValidCC(Message):
    id = 6
    label = "ValidCC"

    def __init__(self, text=""):
       super().__init__(text)

class InvalidCC(Message):
    id = 7
    label = "InvalidCC"

    def __init__(self, text=""):
       super().__init__(text)

class MakeCharge(Message):
    id = 8
    label = "MakeCharge"

    def __init__(self, text=""):
       super().__init__(text)

class MakeOrder(Message):
    id = 9
    label = "MakeOrder"

    def __init__(self, text=""):
       super().__init__(text)

class ShipOrder(Message):
    id = 10
    label = "ShipOrder"

    def __init__(self, text=""):
       super().__init__(text)

class ValidOrder(Message):
    id = 11
    label = "ValidOrder"

    def __init__(self, text=""):
       super().__init__(text)

class InvalidOrder(Message):
    id = 12
    label = "InvalidOrder"

    def __init__(self, text=""):
       super().__init__(text)

class SendOrderShippedEmail(Message):
    id = 13
    label = "SendOrderShippedEmail"

    def __init__(self, text=""):
       super().__init__(text)

class Success(Message):
    id = 14
    label = "Success"

    def __init__(self, text=""):
       super().__init__(text)

class CancelOrder(Message):
    id = 15
    label = "CancelOrder"

    def __init__(self, text=""):
       super().__init__(text)

class Cancel(Message):
    id = 16
    label = "Cancel"

    def __init__(self, text=""):
       super().__init__(text)

class StoreSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return PlaceOrder(session_header.message_text)
        elif (session_header.message_id == 2):
            return GetSKUDetails(session_header.message_text)
        elif (session_header.message_id == 3):
            return SKUDetails(session_header.message_text)
        elif (session_header.message_id == 4):
            return ConfirmOrder(session_header.message_text)
        elif (session_header.message_id == 5):
            return VerifyCC(session_header.message_text)
        elif (session_header.message_id == 6):
            return ValidCC(session_header.message_text)
        elif (session_header.message_id == 7):
            return InvalidCC(session_header.message_text)
        elif (session_header.message_id == 8):
            return MakeCharge(session_header.message_text)
        elif (session_header.message_id == 9):
            return MakeOrder(session_header.message_text)
        elif (session_header.message_id == 10):
            return ShipOrder(session_header.message_text)
        elif (session_header.message_id == 11):
            return ValidOrder(session_header.message_text)
        elif (session_header.message_id == 12):
            return InvalidOrder(session_header.message_text)
        elif (session_header.message_id == 13):
            return SendOrderShippedEmail(session_header.message_text)
        elif (session_header.message_id == 14):
            return Success(session_header.message_text)
        elif (session_header.message_id == 15):
            return CancelOrder(session_header.message_text)
        elif (session_header.message_id == 16):
            return Cancel(session_header.message_text)
        else:
            return Message()
