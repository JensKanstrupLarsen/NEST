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
        s.sendMsg(Authenticator, GetAuth("A"))
        authStatus = s.recvMsg(Authenticator)
        if (type(authStatus) == Acc):
            authenticated = True
    num_requests = 0
    while (num_requests < 3):
        s.sendMsg(Client_B, Rsp("In"))
        s.sendMsg(Client_B, Rqs("Out"))
        s.recvMsg(Client_B, Rsp)
        num_requests += 1
    s.sendMsg(Client_B, Term("End"))
    num_requests = 0
    while (num_requests < 3):
        s.sendMsg(Client_C, Rsp("In"))
        s.sendMsg(Client_C, Rqs("Out"))
        s.recvMsg(Client_C, Rsp)
        num_requests += 1
    s.sendMsg(Client_C, Term("End"))
    s.sendMsg(Authenticator, Term("End"))

if __name__ == '__main__':
    role = Client_A
    sh = VPNSessionHandler(role, 25)
    for i in range(sh.max_sessions):
        sh.startSession(protocol, 0, [])
    sh.closeAll()
