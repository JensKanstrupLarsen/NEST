#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import StoreSessionHandler, ExternalClient, HTTP_Gateway, PlaceOrder, SKUDetails, ConfirmOrder, ValidCC, ExternalBank, ExternalMailserver

def protocol(s):
    s.sendMsg(HTTP_Gateway, PlaceOrder("Item"))
    s.recvMsg(HTTP_Gateway, SKUDetails)
    done = False
    while(not done):
        s.sendMsg(HTTP_Gateway, ConfirmOrder("Item"))
        orderStatus = s.recvMsg(HTTP_Gateway)
        if (type(orderStatus) == ValidCC):
            done = True

if __name__ == '__main__':
    # Create a session handler
    role = ExternalClient
    sh = StoreSessionHandler(role, 50)

    # Creates sessions with ID 1 to 4 a total of n=4 times.
    # Sessions instantiated with the same ID as an existing session
    # will block until the existing one terminates.
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role, ExternalBank, ExternalMailserver])
    sh.closeAll()
