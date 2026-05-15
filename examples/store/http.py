#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import StoreSessionHandler, ExternalClient, HTTP_Gateway, Inventory, PlaceOrder, GetSKUDetails, SKUDetails, ConfirmOrder, ValidCC, Payment, VerifyCC, InvalidCC, MakeCharge, MakeOrder, CancelOrder

def protocol(s):
    s.recvMsg(ExternalClient, PlaceOrder)
    s.sendMsg(Inventory, GetSKUDetails("data"))
    s.recvMsg(Inventory, SKUDetails)
    s.sendMsg(ExternalClient, SKUDetails("data"))
    done = False
    while(not done):
        orderQuery = s.recvMsg(ExternalClient)
        if (type(orderQuery) == ConfirmOrder):
            s.sendMsg(Payment, VerifyCC("cc"))
            ccStatus = s.recvMsg(Payment)
            if (type(ccStatus) == ValidCC):
                s.sendMsg(Payment,   MakeCharge("cc"))
                s.sendMsg(Inventory, MakeOrder("item"))
                s.sendMsg(ExternalClient, ValidCC())
                done = True
            else:
                s.sendMsg(ExternalClient, InvalidCC())
        elif (type(orderQuery) == CancelOrder):
            s.sendMsg(ExternalClient, Cancel())
            done = True

if __name__ == '__main__':
    # Create a session handler
    role = HTTP_Gateway
    sh = StoreSessionHandler(role, 50)

    # Creates sessions with ID 1 to 4 a total of n=4 times.
    # Sessions instantiated with the same ID as an existing session
    # will block until the existing one terminates.
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
