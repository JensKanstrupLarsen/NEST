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
    s.recvMsg(Authenticator, Init)
    authenticated = False
    while (not authenticated):
        s.sendMsg(Authenticator, GetAuth("C"))
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
    #s.sendMsg(Authenticator, Term("End"))
    print("Done.")

if __name__ == '__main__':
    role = Client_C
    sh = VPNSessionHandler(role, 25)
    for i in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()

