#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import StoreSessionHandler, ExternalClient, HTTP_Gateway, Inventory, PlaceOrder, SKUDetails, ConfirmOrder, ValidCC, Payment, VerifyCC, InvalidCC, ExternalBank, MakeCharge

def protocol(s):
    # Send an error the first time
    s.recvMsg(HTTP_Gateway, VerifyCC)
    s.sendMsg(HTTP_Gateway, InvalidCC())
    # Validate on second attempt
    s.recvMsg(HTTP_Gateway, VerifyCC)
    s.sendMsg(HTTP_Gateway, ValidCC())
    s.recvMsg(HTTP_Gateway, MakeCharge)
    s.sendMsg(ExternalBank, MakeCharge("cc"))

if __name__ == '__main__':
    # Create a session handler
    role = Payment
    sh = StoreSessionHandler(role, 50)

    # Creates sessions with ID 1 to 4 a total of n=4 times.
    # Sessions instantiated with the same ID as an existing session
    # will block until the existing one terminates.
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
