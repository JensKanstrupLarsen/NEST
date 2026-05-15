#!/usr/bin/env python3
import argparse
import random
import socket
import time
from threading import Thread

from main import AuctionSessionHandler, Auction, BuyerA, BuyerB, Init, Bid, Confirm, Resend, Winner, Loser, Pay, SendItem

def protocol(s):
    s.recvMsg(Auction, Init)
    auction_over = False
    while (not auction_over):
        s.sendMsg(Auction, Bid("amount"))
        time.sleep(0.01)
        s.sendMsg(Auction, Confirm())
        reply = s.recvMsg(Auction)
        if (type(reply) == Winner):
            s.sendMsg(Auction, Pay("amount"))
            s.recvMsg(Auction, SendItem)
            auction_over = True
        elif (type(reply) == Loser):
            auction_over = True

if __name__ == '__main__':
    role = BuyerA
    sh = AuctionSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role, BuyerB])
    sh.closeAll()
