#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import POP3SessionHandler, POPClient, POPServer, USER, PASS, LIST, MSGS, RETR, OK, ERR, DELE, NOOP, QUIT

def listMessages(s):
    s.sendMsg(POPServer, LIST())
    s.recvMsg(POPServer, MSGS)

def retransmit(s):
    s.sendMsg(POPServer, RETR())
    status = s.recvMsg(POPServer)
    return (type(status) == OK)

def delete(s):
    s.sendMsg(POPServer, DELE())
    status = s.recvMsg(POPServer)
    return (type(status) == OK)

def noop(s):
    s.sendMsg(POPServer, NOOP())
    s.recvMsg(POPServer, OK)

def logout(s):
    s.sendMsg(POPServer, QUIT())
    s.recvMsg(POPServer)

def protocol(s):
    loggedIn = False
    while (not loggedIn):
        s.sendMsg(POPServer, USER("name"))
        usernameStatus = s.recvMsg(POPServer)
        if (type(usernameStatus) == OK):
            s.sendMsg(POPServer, PASS("password"))
            passwordStatus = s.recvMsg(POPServer)
            if (type(passwordStatus) == OK):
                loggedIn = True

    s.sendMsg(POPServer, USER("name"))
    s.sendMsg(POPServer, PASS("password"))
    listMessages(s)
    s.sendMsg(POPServer, USER("name"))
    s.sendMsg(POPServer, PASS("password"))
    retransmit(s)
    s.sendMsg(POPServer, USER("name"))
    s.sendMsg(POPServer, PASS("password"))
    retransmit(s)
    s.sendMsg(POPServer, USER("name"))
    s.sendMsg(POPServer, PASS("password"))
    delete(s)
    s.sendMsg(POPServer, USER("name"))
    s.sendMsg(POPServer, PASS("password"))
    logout(s)

if __name__ == '__main__':
    role = POPClient
    sh = POP3SessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role, POPServer])
    sh.closeAll()
