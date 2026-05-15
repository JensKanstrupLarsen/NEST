#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import StoreSessionHandler, ExternalClient, HTTP_Gateway, Inventory, GetSKUDetails, SKUDetails, MakeOrder, ShipOrder, Shipping

def protocol(s):
    s.recvMsg(HTTP_Gateway, GetSKUDetails)
    s.sendMsg(HTTP_Gateway, SKUDetails("data"))
    s.recvMsg(HTTP_Gateway, MakeOrder)
    s.sendMsg(Shipping, ShipOrder("item"))

if __name__ == '__main__':
    # Create a session handler
    role = Inventory
    sh = StoreSessionHandler(role, 50)

    # Creates sessions with ID 1 to 4 a total of n=4 times.
    # Sessions instantiated with the same ID as an existing session
    # will block until the existing one terminates.
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
