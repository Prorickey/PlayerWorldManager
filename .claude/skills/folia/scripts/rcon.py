#!/usr/bin/env python3
"""
Simple RCON client for Minecraft servers.
No external dependencies required.

Usage:
    ./rcon.py <command>                    # Uses defaults (localhost:25575, password: test)
    ./rcon.py -H host -P port -p pass <command>
    echo "command" | ./rcon.py             # Read from stdin

Environment variables:
    RCON_HOST     - Server host (default: localhost)
    RCON_PORT     - Server port (default: 25575)
    RCON_PASSWORD - Server password (default: test)
"""

import socket
import struct
import sys
import os
import argparse

# RCON packet types
SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0

class RCONError(Exception):
    pass

class RCON:
    def __init__(self, host: str, port: int, password: str, timeout: float = 10.0):
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self.socket = None
        self.request_id = 0

    def _next_request_id(self) -> int:
        self.request_id += 1
        return self.request_id

    def _send_packet(self, packet_type: int, payload: str) -> int:
        """Send an RCON packet and return the request ID."""
        request_id = self._next_request_id()
        payload_bytes = payload.encode('utf-8') + b'\x00\x00'
        packet = struct.pack('<ii', request_id, packet_type) + payload_bytes
        length = struct.pack('<i', len(packet))
        self.socket.sendall(length + packet)
        return request_id

    def _recv_packet(self) -> tuple:
        """Receive an RCON packet. Returns (request_id, packet_type, payload)."""
        # Read length (4 bytes)
        length_data = self._recv_exact(4)
        length = struct.unpack('<i', length_data)[0]

        # Read packet
        packet_data = self._recv_exact(length)
        request_id, packet_type = struct.unpack('<ii', packet_data[:8])
        payload = packet_data[8:-2].decode('utf-8')  # Strip null terminators

        return request_id, packet_type, payload

    def _recv_exact(self, num_bytes: int) -> bytes:
        """Receive exactly num_bytes from socket."""
        data = b''
        while len(data) < num_bytes:
            chunk = self.socket.recv(num_bytes - len(data))
            if not chunk:
                raise RCONError("Connection closed by server")
            data += chunk
        return data

    def connect(self):
        """Connect to the RCON server."""
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.settimeout(self.timeout)
        try:
            self.socket.connect((self.host, self.port))
        except socket.error as e:
            raise RCONError(f"Failed to connect to {self.host}:{self.port}: {e}")

    def authenticate(self):
        """Authenticate with the RCON server."""
        request_id = self._send_packet(SERVERDATA_AUTH, self.password)

        # Server sends an empty response first, then auth response
        resp_id, resp_type, _ = self._recv_packet()

        # Check for auth response
        if resp_type == SERVERDATA_AUTH_RESPONSE:
            if resp_id == -1:
                raise RCONError("Authentication failed: invalid password")
        else:
            # Sometimes there's another packet
            resp_id, resp_type, _ = self._recv_packet()
            if resp_id == -1:
                raise RCONError("Authentication failed: invalid password")

    def command(self, cmd: str) -> str:
        """Execute a command and return the response."""
        self._send_packet(SERVERDATA_EXECCOMMAND, cmd)
        _, _, response = self._recv_packet()
        return response

    def close(self):
        """Close the connection."""
        if self.socket:
            self.socket.close()
            self.socket = None

    def __enter__(self):
        self.connect()
        self.authenticate()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


def main():
    parser = argparse.ArgumentParser(description='RCON client for Minecraft servers')
    parser.add_argument('-H', '--host', default=os.environ.get('RCON_HOST', 'localhost'),
                        help='Server host (default: localhost)')
    parser.add_argument('-P', '--port', type=int, default=int(os.environ.get('RCON_PORT', '25575')),
                        help='Server port (default: 25575)')
    parser.add_argument('-p', '--password', default=os.environ.get('RCON_PASSWORD', 'test'),
                        help='RCON password (default: test)')
    parser.add_argument('-t', '--timeout', type=float, default=10.0,
                        help='Connection timeout in seconds (default: 10)')
    parser.add_argument('command', nargs='*', help='Command to execute')

    args = parser.parse_args()

    # Get command from args or stdin
    if args.command:
        cmd = ' '.join(args.command)
    elif not sys.stdin.isatty():
        cmd = sys.stdin.read().strip()
    else:
        parser.print_help()
        sys.exit(1)

    if not cmd:
        print("Error: No command provided", file=sys.stderr)
        sys.exit(1)

    try:
        with RCON(args.host, args.port, args.password, args.timeout) as rcon:
            response = rcon.command(cmd)
            if response:
                print(response)
    except RCONError as e:
        print(f"RCON Error: {e}", file=sys.stderr)
        sys.exit(1)
    except socket.timeout:
        print("Error: Connection timed out", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)


if __name__ == '__main__':
    main()
