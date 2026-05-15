#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import FirewallSessionHandler, ClientInt, ClientExt, Request, Response, Terminate

def protocol(s):
    for i in range(10):
        s.sendMsg(ClientExt, Request("params"))
        s.recvMsg(ClientExt, Response)
    s.sendMsg(ClientExt, Terminate())
    for i in range(10):
        s.recvMsg(ClientExt, Request)
        s.sendMsg(ClientExt, Response("data"))
    s.recvMsg(ClientExt, Terminate)

if __name__ == '__main__':
    role = ClientInt
    sh = FirewallSessionHandler(role, 50)
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role, ClientExt])
    sh.closeAll()
