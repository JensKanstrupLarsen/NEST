#!/usr/bin/env python3
# Derived from https://github.com/p4lang/tutorials/blob/master/exercises/basic_tunnel/send.py
import os
import sys
import argparse
import random
import socket
import time
import datetime

import error_handling
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

def packet_mismatch():
    error_handling.unexpected_message()

def sessionWrapper(sh, s, session_id, protocol):
    sh.session_locks[session_id].acquire()
    print("Starting session " + str(session_id) + " ("+str(datetime.datetime.now().isoformat())+")")
    sys.stdout.flush()
    sh.sessions[session_id] = s
    protocol(s)
    s.closeSession()

def sessionWrapperNoID(sh, s, protocol):
    print("Starting session without ID ("+str(datetime.datetime.now().isoformat())+")")
    sys.stdout.flush()
    protocol(s)
    s.closeSession()

def receivePackets(sh, socket):
    bytes_read = 0
    connectionActive = True
    while connectionActive:
        msg = socket.recv(128)
        with sh.in_buffer_lock:
            if msg == b'':
                connectionActive = False
            else:
                sh.in_buffer += [SessionHeader.from_bytes(msg)]
                sh.in_buffer_lock.notify_all()
    socket.close()
    return bytes_read

def sendPackets(sh):
    with sh.out_buffer_lock:
        while (not sh.all_done or len(sh.out_buffer) > 0):
            if (len(sh.out_buffer) > 0):
                message, address = sh.out_buffer[0]
                sh.out_buffer = sh.out_buffer[1:]
                receiver_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                receiver_socket.connect((address, 8888))
                receiver_socket.send(message.to_bytes())
                receiver_socket.shutdown(socket.SHUT_RDWR)
                receiver_socket.close()
            else:
                sh.out_buffer_lock.wait()
        

def startServer(sh):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.bind((sh.role.ip, 8888))
    sock.listen(16)
    while True:
        (clientsocket, address) = sock.accept()
        client_thread = Thread(target=receivePackets, args=(sh,clientsocket,))
        client_thread.daemon = True
        client_thread.start()

class SessionHandler:
    def __init__(self, role, app_header, max_sessions):
        self.role = role
        self.max_sessions = max_sessions
        self.app_header = app_header
        self.threads = []
        self.sessions = {}
        self.session_threads = {}
        self.session_locks = {}
        self.in_buffer = []
        self.in_buffer_lock = Condition()
        self.out_buffer = []
        self.out_buffer_lock = Condition()
        self.all_done=False
        for session_id in range(1,max_sessions+1):
            self.session_locks[session_id] = Condition()
        self.listen_thread = Thread(target=startServer, args=(self,))
        self.listen_thread.daemon = True
        self.listen_thread.start()

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
        time.sleep(0.01) # Ugly, but somehow necessary

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
                sys.stderr.flush()
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
        self.connections = {}
        self.terminates = terminates

    def recvMsg(self, sender, message=None):
        cycles = 0
        target_packet = None
        with self.sh.in_buffer_lock:
            while (target_packet == None):
                for pkt in self.sh.in_buffer:
                    # If the packet session ID matches OR the session has no ID yet
                    if ((pkt.session_id == self.session_id or (self.session_id == 0 and not pkt.session_id in self.sh.sessions))
                        # And the message has the expected ID OR can be anything
                        and (message == None or pkt.message_id == message.id)
                        # And the sender role is as expected
                        and pkt.sender_id == sender.id
                        # And the receiver (this session) role is as expected
                        and pkt.receiver_id == self.sh.role.id
                        # And there is no good packet yet
                        and target_packet == None
                        # And the SSN is high enough OR there is no recorded SSN for this sender yet
                        and (pkt.sender_id not in self.seq_nums or pkt.seq_num > self.seq_nums[pkt.sender_id])):
                        target_packet = pkt
                if target_packet == None:
                    self.sh.in_buffer_lock.wait()
            self.sh.in_buffer.remove(target_packet)
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
        print("["+str(self.session_id)+"] Trying to send...")
        self.seq_nums[self.sh.role.id] += 1
        seq_num = self.seq_nums[self.sh.role.id]
        if self.session_id != 0:
            msg = SessionHeader(self.session_id, seq_num, self.sh.role.id, message.id, receiver.id, message.text)
            receiver_socket = None
            if receiver.id in self.connections:
                receiver_socket = self.connections[receiver.id]
            else:
                receiver_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                receiver_socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_MAXSEG, 140)
                receiver_socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                receiver_socket.connect((receiver.ip, 8888))
                self.connections[receiver.id] = receiver_socket
                print("["+str(self.session_id)+"] Connected to "+str(receiver.ip))
            receiver_socket.send(msg.to_bytes())
            print("["+str(self.session_id)+"] Sending \"" + message.label + "\" to " + receiver.name)
            sys.stdout.flush()
            sys.stderr.flush()

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

