#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import DNSSessionHandler, Client, LocalDNS, RootDNS, TLDDNS, AuthDNS, LocalDNS, RqsIP, RspIP, RqsRoot, RspRoot, RqsTLD, RspTLD, RqsAuth, RspAuth

def protocol(s):
    s.recvMsg(Client,  RqsIP)
    s.sendMsg(RootDNS, RqsRoot())
    s.recvMsg(RootDNS, RspRoot)
    s.sendMsg(TLDDNS,  RqsTLD())
    s.recvMsg(TLDDNS,  RspTLD)
    s.sendMsg(AuthDNS, RqsAuth())
    s.recvMsg(AuthDNS, RspAuth)
    s.sendMsg(Client,  RspIP())

if __name__ == '__main__':
    role = LocalDNS
    sh = DNSSessionHandler(role, 50)
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
