#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import SIPSessionHandler, ClientB, Proxy, INVITE, Ringing, OK

def protocol(s):
    s.recvMsg(Proxy, INVITE)
    s.sendMsg(Proxy, Ringing())
    s.sendMsg(Proxy, OK())

if __name__ == '__main__':
    role = ClientB
    sh = SIPSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [])
    sh.closeAll()
