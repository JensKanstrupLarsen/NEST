#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import StoreSessionHandler, ExternalMailserver, Shipping, SendOrderShippedEmail, Success

def protocol(s):
    s.recvMsg(Shipping, SendOrderShippedEmail)
    s.sendMsg(Shipping, Success())

if __name__ == '__main__':
    # Create a session handler
    role = ExternalMailserver
    sh = StoreSessionHandler(role, 50)

    # Creates sessions with ID 1 to 4 a total of n=4 times.
    # Sessions instantiated with the same ID as an existing session
    # will block until the existing one terminates.
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [])
    sh.closeAll()
