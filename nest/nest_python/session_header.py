#!/usr/bin/env python3
# Derived from https://github.com/p4lang/tutorials/blob/master/exercises/basic_tunnel/send.py
import os
import sys
# import argparse
# import random
import socket
# import time

# from scapy.all import IP, TCP, Ether, get_if_hwaddr, get_if_list, sendp, Packet, XByteField, XIntField, sniff, bind_layers

class SessionHeader():
    def __init__(self, session_id, seq_num, sender_id, message_id, receiver_id, message_text):
        self.session_id = session_id     # 10 bits
        self.message_id = message_id     # 6 bits
        self.sender_id = sender_id       # 4 bits
        self.receiver_id = receiver_id   # 4 bits
        self.checksum = 0                # 8 bits
        self.seq_num = seq_num           # 16 bits / 2 bytes
        self.message_text = message_text

    def to_bytes(self):
        payload = (((self.session_id << 6) + self.message_id).to_bytes(2, "big")
                   + ((self.sender_id << 4) + self.receiver_id).to_bytes(1, "big")
                   + self.checksum.to_bytes(1, "big")
                   + self.seq_num.to_bytes(2, "big")
                   + self.message_text.encode("utf-8"))
        msg_len = len(payload)
        for x in range(msg_len, 127):
            payload += b'\x00'
        return msg_len.to_bytes(1,"big") + payload

    def to_string(self):
        return "[["+str(self.session_id)+"] SND:"+str(self.sender_id)+",MSG:"+str(self.message_id)+",RCV:"+str(self.receiver_id)+"]"

    @staticmethod
    def from_bytes(data):
        msg_len = data[0]
        session_id_message_id = int.from_bytes(data[1:3], "big")
        session_id = session_id_message_id >> 6
        message_id = session_id_message_id % 64
        sender_id_receiver_id = data[3]
        sender_id = sender_id_receiver_id >> 4
        receiver_id = sender_id_receiver_id % 16
        checksum = data[4]
        seq_num = int.from_bytes(data[5:7], "big")
        message_text = data[7:(msg_len+1)].decode("utf-8")
        header = SessionHeader(session_id, seq_num, sender_id, message_id, receiver_id, message_text)
        header.checksum = checksum
        return header

    def toString(self):
        return("SessionHeader("
              + str(self.session_id) + ", "
              + str(self.message_id) + ", "
              + str(self.sender_id) + ", "
              + str(self.message_id)+ ", "
              + str(self.receiver_id) + ", "
              + str(self.checksum) + ", "
              + str(self.message_text) + ")")
