#!/usr/bin/env python3
# Derived from https://github.com/p4lang/tutorials/blob/master/exercises/basic_tunnel/send.py
import os
import sys
import argparse
import random
import socket
import time

from session_header import SessionHeader
from threading import Thread, Condition
from scapy.all import IP, TCP, Ether, get_if_hwaddr, get_if_list, sendp, Packet, XByteField, XIntField, sniff, bind_layers

class Role:
    name = ""
    id = 0
    ip = ""

class Message:
    id = 0
    label = ""

    def __init__(self, text=""):
        self.text = text

def sessionWrapper(sh, s, session_id, protocol):
    sh.session_locks[session_id].acquire()
    print("Starting session " + str(session_id))
    sys.stdout.flush()
    sh.sessions[session_id] = s
    protocol(s)
    s.closeSession()

def sessionWrapperNoID(sh, s, protocol):
    print("Starting session <no id>")
    sys.stdout.flush()
    protocol(s)
    s.closeSession()

def receivePackets(sh):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((sh.role.ip, 8888))
    bytes_read = 0
    connectionActive = True
    while connectionActive:
        msg = sock.recv(1024)
        with sh.packet_buffer_lock:
            sh.packet_buffer += [SessionHeader.from_bytes(msg)]
            bytes_read += len(msg)
            sh.packet_buffer_lock.notify_all() 
    socket.close()
    print("Finished reading ("+str(bytes_read)+")")
    return bytes_read

class SessionHandler:
    def __init__(self, role, app_header, max_sessions):
        self.role = role
        self.max_sessions = max_sessions
        self.app_header = app_header
        self.threads = []
        self.sessions = {}
        self.session_locks = {}
        self.packet_buffer = []
        self.packet_buffer_lock = Condition()
        for session_id in range(1,max_sessions+1):
            self.session_locks[session_id] = Condition()
        thread = Thread(target=receivePackets, args=(self,))
        thread.daemon = True
        thread.start()

    def startSession(self, protocol, session_id=0, terminates=[]):
        if session_id != 0:
            with self.session_locks[session_id]:
                while session_id in self.sessions:
                    self.session_locks[session_id].wait()
                s = Session(self, session_id, terminates)
                thread = Thread(target=sessionWrapper, args=(self, s, session_id, protocol))
                thread.daemon = True
                self.threads += [(s, thread)]
                thread.start()
        else:
            s = Session(self, 0, terminates)
            thread = Thread(target=sessionWrapperNoID, args=(self, s, protocol))
            thread.daemon = True
            self.threads += [(s, thread)]
            thread.start()
        time.sleep(0.01) # Ugly, but necessary to prevent buffer overflow
        

    def sendMessage(self, session_id, seq_num, message, receiver):
        receiver_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        session_message = SessionHeader(
            session_id,
            seq_num,
            self.role.id,
            message.id,
            receiver.id,
            message.text)
        receiver_socket.sendto(session_message.to_bytes(), (receiver.ip, 8888))
        print("["+str(session_id)+"] Sent message successfully!")
        receiver_socket.close() 

    def removeSession(self, session_id):
        del self.sessions[session_id]

    def toMessage(self, session_header):
        return Message()

    def closeAll(self, timeout=10):
        total_timeouts = 0
        timed_out_threads = []
        for t in self.threads:
            if total_timeouts == 0:
                if timeout == 0:
                    t[1].join()
                else:
                    t[1].join(timeout)
            else:
                t[1].join(0.1)
            if t[1].is_alive():
                print("Session "+str(t[0].session_id)+" timed out after 10 seconds")
                sys.stdout.flush()
                total_timeouts += 1
                timed_out_threads += [t[0].session_id]
        print("All done! ("+str(len(self.threads) - total_timeouts)+"/"+str(len(self.threads))+" theads finished successfully)")
        print("Threads timed out: "+str(timed_out_threads))
        sys.stdout.flush()

class Session:
    def __init__(self, sh, session_id=0, terminates=[]):
        self.sh = sh
        self.session_id = session_id
        self.seq_nums = {sh.role.id : 0}
        self.terminates = terminates
        
    def recvMsg(self, sender, message=None):
        cycles = 0
        target_packet = None
        with self.sh.packet_buffer_lock:
            while (target_packet == None):
                for pkt in self.sh.packet_buffer:
                    check_msg = self.sh.toMessage(pkt)
                    if ((pkt.session_id == self.session_id or (self.session_id == 0 and not pkt.session_id in self.sh.sessions))
                        and (message == None or pkt.message_id == message.id)
                        and pkt.sender_id == sender.id
                        and pkt.receiver_id == self.sh.role.id
                        and target_packet == None
                        and (pkt.sender_id not in self.seq_nums or pkt.seq_num > self.seq_nums[pkt.sender_id])):
                        target_packet = pkt
                if target_packet == None:
                    self.sh.packet_buffer_lock.wait()
            self.sh.packet_buffer.remove(target_packet)
            if self.session_id == 0:
                self.session_id = target_packet.session_id
                self.sh.session_locks[self.session_id].acquire()
                self.sh.sessions[self.session_id] = self
                print("Using session number " + str(self.session_id))
            msg = self.sh.toMessage(target_packet)
            print("["+str(self.session_id)+"] Received \"" + msg.label + "\" from " + sender.name)
            sys.stdout.flush()
            self.seq_nums[target_packet.sender_id] = target_packet.seq_num
            return msg

    def sendMsg(self, receiver, message):
        self.seq_nums[self.sh.role.id] += 1
        seq_num = self.seq_nums[self.sh.role.id]
        print("["+str(self.session_id)+"] Sending \"" + message.label + "\" to " + receiver.name)
        sys.stdout.flush()
        self.sh.sendMessage(self.session_id, seq_num, message, receiver)

    def closeSession(self):
        time.sleep(2) # Making sure that all retransmissions have gone through
        for role in self.terminates:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            session_message = SessionHeader(self.session_id, 0, role.id, 0, 0, "")
            sock.sendto(session_message.to_bytes(), ("10.0.0.0", 8888))
            sock.close()
        self.sh.removeSession(self.session_id)
        print("["+str(self.session_id)+"] Closing session")
        sys.stdout.flush()
        self.sh.session_locks[self.session_id].notify()
        self.sh.session_locks[self.session_id].release()
