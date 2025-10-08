// SPDX-License-Identifier: BSD-3-Clause
/*
 * kcoro protocol — control/observability frames (transport‑agnostic)
 * ---------------------------------------------------------------
 *
 * Purpose
 * - A small, versioned command set and TLV attribute space for channel RPCs.
 * - Transport‑agnostic: works over POSIX UDS today; TCP/QUIC adapters later.
 *
 * Framing
 * - Request/response correlation uses `KCORO_ATTR_REQ_ID` (32‑bit). Servers
 *   echo the client’s req_id in responses so clients can complete the correct
 *   awaiter.
 */
#pragma once

// Protocol version - used for compatibility checking between kcoro implementations
#define KCORO_PROTO_ABI_MAJOR 1  // Major version - breaks compatibility on changes
#define KCORO_PROTO_ABI_MINOR 0  // Minor version - additive features only

/* Commands (transport maps these to its own message types) */
enum kcoro_cmd {
    KCORO_CMD_UNSPEC = 0,  // Unspecified command - used as default/invalid value
    
    /* Connection bootstrap: peers exchange ABI/version using ATTR_ABI_* */
    KCORO_CMD_HELLO = 1,   // Handshake command to exchange protocol version information
    
    /* Channel operations (in use) */
    KCORO_CMD_CHAN_MAKE   = 10,   // Create a new channel with specified parameters
    KCORO_CMD_CHAN_SEND   = 11,   // Send data to a channel (blocking)
    KCORO_CMD_CHAN_TRY_SEND = 12, // Send data to a channel (non-blocking)
    KCORO_CMD_CHAN_RECV   = 13,   // Receive data from a channel (blocking)
    KCORO_CMD_CHAN_TRY_RECV = 14, // Receive data from a channel (non-blocking)
    KCORO_CMD_CHAN_CLOSE  = 15,   // Close a channel (graceful shutdown)
    KCORO_CMD_CHAN_DESTROY= 16,   // Destroy a channel (immediate cleanup)

    /* Reserved for future (not implemented yet) */
    KCORO_CMD_GET_INFO    = 2,    // Retrieve information about the system or channel
    KCORO_CMD_GET_STATS   = 3,    // Get statistics about channel operations
    KCORO_CMD_SET_CONFIG  = 4,    // Set runtime configuration parameters
    KCORO_CMD_RESET_STATS = 5,    // Reset accumulated statistics
    KCORO_CMD_LIST        = 6,    // List available channels or resources
    KCORO_CMD_ACTOR_START = 20,   // Start an actor (coroutine-based task)
    KCORO_CMD_ACTOR_STOP  = 21,   // Stop an actor
    KCORO_CMD_SELECT_SEND = 30,   // Select on send operations across multiple channels
    KCORO_CMD_SELECT_RECV = 31,   // Select on receive operations across multiple channels

    __KCORO_CMD_MAX
};
#define KCORO_CMD_MAX (__KCORO_CMD_MAX - 1)

/* Attributes (TLV-style, transport chooses encoding) */
enum kcoro_attr {
    KCORO_ATTR_UNSPEC = 0,  // Unspecified attribute - used as default/invalid value
    
    /* Handshake (version/capability negotiation) */
    KCORO_ATTR_ABI_MAJOR = 1,  // Protocol major version (breaks on change)
    KCORO_ATTR_ABI_MINOR = 2,  // Protocol minor version (additive features)

    /* Channel creation/ops (in use today) */
    KCORO_ATTR_KIND       = 5,  // Channel kind (RENDEZVOUS/BUFFERED/…)
    KCORO_ATTR_ELEM_SIZE  = 6,  // Element size in bytes for channel data
    KCORO_ATTR_CAPACITY   = 7,  // Ring capacity for buffered channels
    KCORO_ATTR_TIMEOUT_MS = 8,  // Operation timeout in milliseconds (0=try, <0=infinite)
    KCORO_ATTR_CHAN_ID    = 20, // Logical channel identifier for correlation
    
    /* Data attributes for operations */
    KCORO_ATTR_ELEMENT    = 21, // Operation payload (send/recv element data)
    KCORO_ATTR_RESULT     = 22, // Operation result code (0 or negative KC_* error codes)
    KCORO_ATTR_REQ_ID     = 26, // 32-bit request correlation ID; echoed by server for response matching

    /* Reserved for future (not implemented yet) */
    KCORO_ATTR_CAPS       = 3,  // Capabilities or feature flags
    KCORO_ATTR_ID         = 4,  // Generic identifier attribute
    KCORO_ATTR_SEND_OPS   = 9,  // Count of send operations performed
    KCORO_ATTR_RECV_OPS   = 10, // Count of receive operations performed
    KCORO_ATTR_WAITERS_S  = 11, // Number of send waiters (threads blocked waiting to send)
    KCORO_ATTR_WAITERS_R  = 12, // Number of receive waiters (threads blocked waiting to receive)
    KCORO_ATTR_DROPS      = 13, // Number of dropped messages due to buffer overflow
    KCORO_ATTR_LAT_MIN    = 14, // Minimum latency measurement for operations
    KCORO_ATTR_LAT_MAX    = 15, // Maximum latency measurement for operations
    KCORO_ATTR_LAT_AVG    = 16, // Average latency measurement for operations
    KCORO_ATTR_FIFO_MAX   = 17, // Maximum FIFO queue depth observed
    KCORO_ATTR_ACTOR_ID   = 23, // Identifier for actor (coroutine-based task)
    KCORO_ATTR_MSG_SIZE   = 24, // Size of message being transmitted
    KCORO_ATTR_SELECT_MASK= 25, // Bitmask for select operations on multiple channels

    __KCORO_ATTR_MAX
};
#define KCORO_ATTR_MAX (__KCORO_ATTR_MAX - 1)
