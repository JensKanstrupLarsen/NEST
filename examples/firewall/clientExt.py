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
    outgoingComms = True
    while outgoingComms:
        msg = s.recvMsg(ClientInt)
        if type(msg) == Request:
            s.sendMsg(ClientInt, Response("data"))
        elif type(msg) == Terminate:
            outgoingComms = False
        else:
            error_handling.unexpected_message()
            #print("Unexpected message!")
            #return 1
    for i in range(10):
        s.sendMsg(ClientInt, Request("params"))
        s.recvMsg(ClientInt, Response)
    s.sendMsg(ClientInt, Terminate())

if __name__ == '__main__':
    role = ClientExt
    sh = FirewallSessionHandler(role, 50)
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [])
    sh.closeAll()
