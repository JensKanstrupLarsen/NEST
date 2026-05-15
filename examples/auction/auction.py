#!/usr/bin/env python3
import argparse
import random
import socket
import time
from threading import Thread

from main import AuctionSessionHandler, Auction, BuyerA, BuyerB, Init, Bid, Confirm, Resend, Winner, Loser, Pay, SendItem

def protocol(s):
    s.sendMsg(BuyerA, Init("A"))
    s.sendMsg(BuyerB, Init("B"))
    rounds = 1
    while (True):
        s.recvMsg(BuyerA, Bid)
        s.recvMsg(BuyerA, Confirm)
        s.recvMsg(BuyerB, Bid)
        s.recvMsg(BuyerB, Confirm)
        rounds += 1
        if (rounds < 5):
            s.sendMsg(BuyerA, Resend("A"))
            s.sendMsg(BuyerB, Resend("A"))
        else:
            s.sendMsg(BuyerA, Winner())
            s.sendMsg(BuyerB, Loser())
            s.recvMsg(BuyerA, Pay)
            s.sendMsg(BuyerA, SendItem("item"))
            return

if __name__ == '__main__':
    role = Auction
    sh = AuctionSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role])
        time.sleep(0.1)
    sh.closeAll()
