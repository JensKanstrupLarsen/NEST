#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import FirewallSessionHandler, ClientInt, ClientExt, Request, Response, Terminate

def protocol(s):
    for i in range(10):
        s.recvMsg(ClientInt, Request)
        s.sendMsg(ClientInt, Response("data"))
    s.recvMsg(ClientInt, Terminate)
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
