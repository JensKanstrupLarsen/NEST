#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import POP3SessionHandler, POPClient, POPServer, USER, PASS, LIST, MSGS, RETR, OK, ERR, DELE, NOOP, QUIT

def listMessages(s):
    s.sendMsg(POPClient, MSGS("data"))

def retransmit(s, firstRETR):
    if (firstRETR):
        s.sendMsg(POPClient, OK())
    else:
        s.sendMsg(POPClient, ERR())

def delete(s):
    s.sendMsg(POPClient, OK())

def noop(s):
    s.sendMsg(POPClient, OK())

def protocol(s):
    retryUSER = True
    retryPASS = True
    loggedIn = False
    while (not loggedIn):
        s.recvMsg(POPClient, USER)
        if (retryUSER):
            retryUSER = False
            s.sendMsg(POPClient, ERR())
        else:
            s.sendMsg(POPClient, OK())
            s.recvMsg(POPClient, PASS)
            if (retryPASS):
                retryPASS = False
                s.sendMsg(POPClient, ERR())
            else:
                s.sendMsg(POPClient, OK())
                loggedIn = True
    firstRETR = True
    while (loggedIn):
        query = type(s.recvMsg(POPClient))
        if (query == LIST):
            listMessages(s)
        elif (query == RETR):
            retransmit(s, firstRETR)
            firstRETR = False
        elif (query == DELE):
            delete(s)
        elif (query == NOOP):
            noop(s)
        elif (query == QUIT):
            loggedIn = False
            s.sendMsg(POPClient, OK())

if __name__ == '__main__':
    role = POPServer
    sh = POP3SessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [])
    sh.closeAll()
