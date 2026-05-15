#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import SIPSessionHandler, ClientA, Proxy, ClientB, INVITE, Trying, Ringing, OK

def protocol(s):
    s.sendMsg(Proxy, INVITE())
    status = s.recvMsg(Proxy)
    if (type(status) == Trying):
        status = s.recvMsg(Proxy)
        if (type(status) == Ringing):
            status = s.recvMsg(Proxy)
            if (type(status) == OK):
                print("Call accepted")
            else:
                print("Receiver did not respond")
        else:
            print("Receiver rejected invite")
    else:
        print("Proxy rejected invite")

if __name__ == '__main__':
    role = ClientA
    sh = SIPSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role, ClientB, Proxy])
    sh.closeAll()
