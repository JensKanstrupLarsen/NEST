#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
import error_handling
from main import VPNSessionHandler, Authenticator, Client_A, Client_B, Client_C, Init, GetAuth, Acc, Deny, Rqs, Rsp, Term

def protocol(s):
    s.recvMsg(Authenticator, Init)
    authenticated = False
    while (not authenticated):
        s.sendMsg(Authenticator, GetAuth("B"))
        authStatus = s.recvMsg(Authenticator)
        if (type(authStatus) == Acc):
            authenticated = True
    stop_receiving = False
    while (not stop_receiving):
        msg = s.recvMsg(Client_A)
        if (type(msg) == Rqs):
            s.sendMsg(Client_A, Rsp("In"))
        elif (type(msg) == Term):
            stop_receiving = True
        else:
            error_handling.unexpected_message() #print("Unexpected message!")
            #return 1
    #s.sendMsg(Authenticator, Term("End"))
    print("Done.")

if __name__ == '__main__':
    # Create a session handler
    role = Client_B
    sh = VPNSessionHandler(role, 25)

    # Creates sessions with ID 1 to 4 a total of 4 times.
    # Sessions instantiated with the same ID as an existing session
    # will block until the current one terminates.
    for i in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
