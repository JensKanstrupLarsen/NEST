#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '..'))
from main import VPNSessionHandler, Authenticator, Client_A, Client_B, Client_C, Init, GetAuth, Acc, Deny, Rqs, Rsp, Term

def protocol(s):
    s.sendMsg(Client_A, Init("A"))
    s.sendMsg(Client_B, Init("B"))
    s.sendMsg(Client_C, Init("C"))
    auth_requests = 0
    while(True):
        s.recvMsg(Client_A, GetAuth)
        s.recvMsg(Client_B, GetAuth)
        s.recvMsg(Client_C, GetAuth)
        auth_requests += 1
        if (auth_requests > 3):
            s.sendMsg(Client_A, Acc("A"))
            s.sendMsg(Client_B, Acc("B"))
            s.sendMsg(Client_C, Acc("C"))
            s.recvMsg(Client_A, Term)
            s.recvMsg(Client_B, Term)
            s.recvMsg(Client_C, Term)
            return
        else:
            s.sendMsg(Client_A, Deny("A"))
            s.sendMsg(Client_B, Deny("B"))
            s.sendMsg(Client_C, Deny("C"))

if __name__ == '__main__':
    # Create a session handler
    role = Authenticator
    sh = VPNSessionHandler(role, 25)

    # Creates sessions with ID 1 to 4 a total of n=4 times.
    # Sessions instantiated with the same ID as an existing session
    # will block until the existing one terminates.
    for session_id in range(sh.max_sessions):
        time.sleep(0.01)
        sh.startSession(protocol, session_id+1, [role, Client_A])
    sh.closeAll()
