#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import SIPSessionHandler, ClientA, ClientB, Proxy, INVITE, Trying, Ringing, OK, Error

def protocol(s):
    s.recvMsg(ClientA, INVITE)
    s.sendMsg(ClientB, INVITE())
    s.sendMsg(ClientA, Trying())
    ringing = True
    while (ringing):
        status = s.recvMsg(ClientB)
        if (type(status) == Ringing):
            ringing=False
        elif (type(status) == Error):
            ringing=False
            s.sendMsg(ClientA, Error())
            return
    s.sendMsg(ClientA, Ringing())
    status = s.recvMsg(ClientB)
    if (type(status) == OK):
        s.sendMsg(ClientA, OK())
    elif (type(status) == Error):
        s.sendMsg(ClientA, Error())

if __name__ == '__main__':
    role = Proxy
    sh = SIPSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [])
    sh.closeAll()
