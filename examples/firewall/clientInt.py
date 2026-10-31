#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
import error_handling
from main import FirewallSessionHandler, ClientInt, ClientExt, Request, Response, Terminate

def protocol(s):
    for i in range(10):
        s.sendMsg(ClientExt, Request("params"))
        s.recvMsg(ClientExt, Response)
    s.sendMsg(ClientExt, Terminate())
    incomingComms = True
    while incomingComms:
        msg = s.recvMsg(ClientExt)
        if type(msg) == Request:
            s.sendMsg(ClientExt, Response("data"))
        elif type(msg) == Terminate:
            incomingComms = False
        else:
            error_handling.unexpected_message()
            # print("Unexpected message!")
            # return 1

if __name__ == '__main__':
    role = ClientInt
    sh = FirewallSessionHandler(role, 50)
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role, ClientExt])
    sh.closeAll()
