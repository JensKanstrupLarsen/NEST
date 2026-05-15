# Documentation

This file provides a more detailed description of this artifact and documents
the usage and internals of NEST.

## Artifact structure

The artifact contains the following directories:

 * `nest/`, which further contains:
   * `nest_scala/`, containing the Scala 3 implementation of NEST itself (which
     performs the synthesis from session types to table entries and API
     generation) as well as the session types and NEST instantiation of each
     example described in the paper;
   * `nest_p4/`, containing the P4 switch configuration files that are deployed
     on P4-enabled switches. These files set up the P4 tables that are then
     populated by NEST-generated entries that enforce session monitoring;
   * `nest_python/`, containing library files for the NEST-generated session
     API.
 * `examples/`, which contains subdirectories with network configurations for
   [Mininet](https://mininet.org/) and the Python implementation of each role in
   each example (using the NEST-generated session APIs).
 * `testing/`, which contains support files for running the artifact evaluation,
   summarizing results, and generating plots.
 * `vm/`, which contains the artifact virtual machine (as an Open Virtual
   Appliance (OVA) package) as well as support files for generating the VM image
   using [`Vagrant`](https://developer.hashicorp.com/vagrant).

We now describe the usage of NEST focusing on the `bookreview` example. This
description can be easily adapted to other examples, by (1) inspecting the
subdirectory names under `examples/` and
`nest/nest_scala/src/main/scala/examples/`, and (2) adjusting the instructions
below to use different example directories. All 10 examples mentioned in Table
1 of the companion paper are available in this artifact, and the code listings
in Appendix B can be found in their respective files in
`nest/nest_scala/src/main/scala/examples/`.

The process described below is summarised with the following steps:

  0. Set up the network topology.
  1. Define all roles (a.k.a. "agents") and labels used in the session type
     using `newAgent(...)` and `newLabel(...)` respectively.
  2. Define the session type used by each agent as a `LocalSessionType`.
  3. Use `constructSwitch(...)` to create a `MonitorSwitch` object that
     establishes a connection to each P4-enabled switch.
  4. Upload suitable forwarding rules to each switch by using the
     `addForwardingRule` method.
  5. If session monitoring is enabled:
     1. Define the border ports for each switch using the `addPortCheck` method.
     2. Synthesize a state machine monitor from a local session type using
        `LST_to_SM`.
     3. Define the P4 monitor for each switch (from one or more state machines).
     4. Upload each composite monitor to its respective switch using the
        `addMonitor` method.

## Preliminary network topology setup

The programs discussed below (in Scala and Python) assume to be executed with a
basic network topology in place, end hosts running at known IP addresses, and
also P4-enabled switches at known MAC addresses configured with one of the P4
files in the directory `nest/nest_p4/`. This preliminary network setup is
performed by the artifact evaluation scripts using
[Mininet](https://mininet.org/) using configuration files under the directory
`examples/` -- see e.g. `examples/bookreview/topology.json`.

## Generating and deploying NEST monitors on switches

We now describe how to use NEST to generate deploy session monitors on
P4-enabled network switches.

_**NOTE:** all file paths in this section assume
`nest/nest_scala/src/main/scala/` as the current directory -- unless we
explicitly write `<ROOT>` at the beginning of the path to indicate the root
directory of this artifact._

### Session type definition

NEST generates monitors from session types. An example of how session types are
defined in NEST using a Scala 3 domain-specific language. More precisely, each
session type is an instance of a dedicated data type. This can be seen in the
top of the BookInfo example in `examples/01_bookreview.scala`:

```scala
    val a_BookClient = newAgent("Client", 1, bytes(10,0,1,1))
    ...

    val l_Request = newLabel("Request", 1)
    ...

    val lst_BookClient =
        IntChoice(Map((a_BookInfo, l_Request) ->
        ExtChoice(a_BookInfo, Map(l_Response ->
        End()))))
    ...
```

First, each role in the session type is represented as an "agent" object
`STAgent` containing the role/agent name, ID, and IP address of the end host
where the role/agent runs. Likewise, all message labels (and their ID) are
represented as an `STLabel` object. These objects are instantiated using
`newAgent(...)` and `newLabel(...)`, respectively.

The session types themselves are defined as instances of the `enum` data type
`LocalSessionType`, defined in `monitors/sessiontypes.scala`. This definition
mirrors Definition 4.1 of the companion paper (except for input queues, that are
only used for the formal semantics, hence they are not part of the NEST session
types syntax).

### Basic P4 switch setup

In `examples/01_bookreview.scala`, the function `setup(isTCP, useMonitors)`
deploys the NEST-generated session monitors on P4-enabled switches. First, the
function sets up the forwarding rules of the switch (which depend on the network
topology used for the example):

```scala
    def setup(isTCP : Boolean, useMonitors : Boolean) =
        val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
        ...

        val ipv4_rules = List(
            (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
            ...)

        for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
            switch.addForwardingRule(dst_IP, dst_MAC, p_out)
```

Here, `constructSwitch(...)` establishes a connection to the switch with the
given IP/port, then generates a `MonitorSwitch` object that maintains the
connection and is used to update the switch itself. The `MonitorSwitch` uses
the [P4R-Type](https://github.com/JensKanstrupLarsen/P4R-Type) library to
connect to the switch and guarantee type-safe P4 table updates. 

The `ipv4_rules` list contains lists of four pairs: the `MonitorSwitch` to
update, the destination IP to match on, the MAC address corresponding to that
IP, and the outgoing port. These values depend on the network topology in place
for the example (see _"Preliminary network topology setup"_ above). The
switches are then updated using the `addForwardingRule` method from their
respective `MonitorSwitch`.

### Session monitor generation and deployment

At this point, the example is ready to run in a network without session
monitors. However, if the `useMonitors` argument of the `setup(...)` function is
`true`, then the example will perform additional P4 switch setup:

```scala
    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1,2,3), List(1,2,3,4))
            ++ ...

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_client = LST_to_SM(lst_Client, a_Client)
        ...

        val monitor_switches = List(
            (s1, restrict(List(a_Client, a_Bank, a_Email),
                    parallel(List(
                        monitor(a_Client, msa_client),
                        monitor(a_Bank, msa_bank),
                        monitor(a_Email, msa_email))))),
            (s2, monitor(a_HTTP, msa_http)),
            ...)

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)
```

First, the program specifies which ports of the switch are "border ports" that
should be monitored, and which ports are connected to parts of the network whose
boundaries are already guarded by NEST monitors. The function `genPortPairs`
takes three arguments: the `MonitorSwitch` object to update, a list of border
ports for that switch, and a list of all ports for that switch. The switches are
updated using the `addPortCheck` method from their `MonitorSwitch` object.

After this, the program proceeds with the actual monitor generation. This is
done in the `LST_to_SM(...)` function, which takes a local session type and
produces a  state machine object of type `StateMachine`. The function is based
on Definition 4.10 in the companion paper, essentially allowing the state
machine to accept messages that are expected in the current state of the session
type, or in a possible future branch of the session type. Otherwise, the state machine rejects all messages it does not explicitly accept.

Next, the generated state machines are associated with their respective
switches, possibly combining multiple state machines into _composite monitors_
if the switch is connected to multiple end hosts. (See Section 3.2 of the
companion paper for further details.)

Finally, the `addMonitor` method synthesizes P4 table entries from the composite
state machine (essentially converting one transition into one table entry), then
uploads those table entries to the P4-enable switch to via the `switch` object.

## Session API generation and usage

We now give a description of how NEST generates session APIs for Python from a
given session type, and how to use such APIs.

### API generation

The session API generation can be performed using the `genMainFile` function
defined in `nest/nest_scala/src/main/scala/monitors/controlPlane.scala`. All the
examples provided in the artifact can be used to generate a session API. For
instance, the BookInfo example in
`nest/nest_scala/src/main/scala/examples/01_bookreview.scala` contains:

```scala
    @main def genMainFile() = println(controlplane.genMainFile(
        "BookReview",
        List(a_BookClient, ...),
        List(l_Request, ...)))
```

Therefore, running the following command in the directory `nest/nest_scala`:

```
sbt "runMain bookreview.genMainFile"
```

will print out a Python file that declares the session API:

```python
    #!/usr/bin/env python3
    import argparse
    ...

    sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
    from monitor import SessionHandler, Role, Message

    class Client(Role):
        name = "Client"
        id = 1
        ip = "10.0.1.1"
    ...

    class Request(Message):
        id = 1
        label = "Request"

        def __init__(self, text=""):
        super().__init__(text)
    ...

    class BookReviewSessionHandler(SessionHandler):
        def __init__(self, role, max_sessions):
            super().__init__(role, "session", max_sessions)

        def toMessage(self, session_header):
            if (session_header.message_id == 1):
                return Request(session_header.message_text)
            elif (session_header.message_id == 8):
                ...
```

This essentially defines a custom class for each agent/role and message in the
session type, as well as a `SessionHandler` class that can be used for running
multiple sessions simultaneously.

The full version of the generated API above can be seen in
`examples/bookreview/main.py`. Note the path reference:

```python
    sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
```

Ths should point to the folder containing the files in `nest/nest_python/`. Note
that:

  * When running the examples by using `make`, the folder is copied to the
    `examples/` directory, and
  * Either `nest/nest_python/udp_monitor.py` or
    `nest/nest_python/tcp_monitor.py` must be renamed to `monitor.py`, depending
    on which type of protocol should be used.

Both of these steps can be performed by running either `testing/set_udp.sh` or
`testing/set_tcp.sh` (depending on which protocol is wanted); the artifact
scripts perform these steps automatically as needed.

### Using the session API

Consider the implementation of the `Info` end host in the BookInfo example, as
shown in `examples/bookreview/info.py`:

```python
    ...

    from main import BookReviewSessionHandler, Client, Info, ...

    def protocol(s):
        s.recvMsg(Client,  Request)
        s.sendMsg(Review,  RqsReview("isbn"))
        s.sendMsg(Details, RqsDetail("isbn"))
        s.recvMsg(Review,  RspReview)
        s.recvMsg(Details, RspDetail)
        s.sendMsg(Client,  Response("result"))

    if __name__ == '__main__':
        role = Info
        sh = BookReviewSessionHandler(role, 50)
        for i in range(50):
            sh.startSession(protocol, 0, [role])
        sh.closeAll()
```

Here, the `protocol(s)` implements the session type as described in `nest/nest_scala/src/main/scala/examples/01_bookreview.scala`:

```scala
    val lst_BookInfo =
        ExtChoice(a_BookClient, Map(l_Request ->
        IntChoice(Map((a_BookReview, l_ReviewRequest) ->
        IntChoice(Map((a_BookDetails, l_DetailRequest) ->
        ExtChoice(a_BookReview, Map(l_ReviewResponse ->
        ExtChoice(a_BookDetails, Map(l_DetailResponse ->
        IntChoice(Map((a_BookClient, l_Response) ->
        End()))))))))))))
```

The `s.sendMsg(...)` and `s.recvMsg(...)` method calls are to the `Session`
object `s`, which is provided by the `SessionHandler` when running the
`protocol` (we will see shortly how the session handler instantiates and runs
the sessions).

Each label takes an optional string parameter as payload; in this example, the
`RqsReview("isbn")` label takes `"isbn"` as the string payload.

In the `__main__` function, a `BookReviewSessionHandler` object is instantiated.
The parameters `Info` and `50` indicate that all sessions created by it use the
`Info` role, and that it can have at most 50 concurrent sessions at a time.

For evaluation purposes, 50 concurrent sessions are then instantiated using `sh.startSession(protocol, 0, [role])`. The reason for these arguments are as
follows:

 * `protocol=protocol` determines the behaviour of the session.
 * `session_id=0` indicates that the sessions should receive their session ID
   through propagation (as explained in Section 3.4 of the companion paper). To
   provide the session with an initial, fixed ID, the number must be 1 or
   greater.
 * `terminates=[role]` is used for session termination. When a session
   terminates, (i.e. the `protocol` finishes running), the session may broadcast
   a termination message to its monitoring switch, causing the switch to reset
   the state for that particular session ID and `role`. Because multiple end
   hosts may be monitored by the same switch, only the "last" end host
   sending/receiving messages on that switch should send a terminating message.
   In the BookInfo example, each monitoring switch monitors exactly one end
   host, so all end hosts only terminate their own role, but in cases with
   multiple connected end hosts, one end host may provide no roles to terminate
   (`terminates=[]`) while another may provide multiple (e.g.
   `terminates=[role1, role2]`).

Finally, `sh.closeAll()` waits for all sessions to terminate before exiting the
program.

## Demonstrating reusability
This section describes ways of using NEST and its testing framework
in more detail, for users aiming to use or possibly extend NEST.

### Introducing errors
To show concretely how the monitor drops packets, we now try to adjust one of
the correct examples such that it sends faulty packets. Consider the `Review`
role part of the BookInfo example that is implemented in
`examples/bookreview/review.py`:

    ...

    def protocol(s):
        s.recvMsg(Info,    RqsReview)
        s.sendMsg(Ratings, RqsRatings("isbn"))
        s.recvMsg(Ratings, RspRatings)
        s.sendMsg(Info,    RspReview("result"))

    if __name__ == '__main__':
        role = Review
        sh = BookReviewSessionHandler(role, 50)
        for i in range(50):
            sh.startSession(protocol, 0, [role])
        sh.closeAll()

Now, let's introduce an error such that the `Review` sends an additional,
unwanted message with the label `RspRatings` back to `Info`:

    ...

    def protocol(s):
        s.recvMsg(Info,    RqsReview)
        s.sendMsg(Ratings, RqsRatings("isbn"))
        s.recvMsg(Ratings, RspRatings)
        s.sendMsg(Info,    RspReview("result"))
        s.sendMsg(Info,    RspRatings()) # <-- Unwanted message

    if __name__ == '__main__':
        role = Review
        sh = BookReviewSessionHandler(role, 50)
        for i in range(50):
            sh.startSession(protocol, 0, [role])
        sh.closeAll()

After introducing the error, try to run the BookInfo example with the following
command:

    make test_single \
        EX=bookreview \
        FLAGS="--protocol UDP" \
        GATHER=gatherdataUDP.sh \
        DATA="udp mon good" \
        NUM=42

This will run it the example over a UDP network with monitors enabled.

Once it has finished, examine the test results by running
`less .testdata/bookreview_udp_mon_good_42_result.txt`. You should see:

    Total packets accepted:
    800
    Total packets rejected:
    50

If you want to inspect the specific monitor logs, run
`less .testdata/bookreview_udp_mon_good_42_monitorlogs.txt`, then look for
lines that contain:

    ... Bad message, rejecting. Message contents: [SND:3,ID:5,SSQN:3,RCV:2]

This line indicates that the rejected message was sent from `Review` (`SND:3`),
to be received by `Info` (`RCV:2`) and with label `RspRatings` (`ID:5`).

### Running examples manually
If you want to run examples manually, perhaps because you want to
see the message passing in real time, it is possible to start a
mininet network without automatically running example code.

First, determine which transport protocol you want to use (UDP or
TCP), then run either

    make build_udp
    sh testing/set_udp.sh

or

    make build_tcp
    sh testing/set_tcp.sh

Then, you can start a network with a predefined topology by using
the following command:

    sudo python3 utils/run_exercise.py \
        -t examples/<example>/topology.json \
        -b simple_switch_grpc

where `<example>` is one of the subfolders in the `examples/`
directory. Ths will start a mininet network with the topology of the
given example, and will also launch a CLI for mininet. From this CLI, we can access each end host individually and run different programs on either one.

Before we can run these programs however, we must set up the
switches to enable monitoring. Create a new terminal, then navigate
to `nest/nest_scala`. Now, suppose you choose the `bookreview`
example topology. Then you can run ___one___ of these four commands, depending on which configuration you want (UDP/TCP, unmonitored/monitored):

    sbt "runMain bookreview.setupUDP_NoMonitor"
    sbt "runMain bookreview.setupUDP_Monitored"
    sbt "runMain bookreview.setupTCP_NoMonitor"
    sbt "runMain bookreview.setupTCP_Monitored"

Once you've run the command, the monitoring switches in the network will be set up and ready to monitor packets.

Return to the previous terminal with the mininet CLI, then write the following:

    xterm h1 h2 h3 h4 h5

This will launch five small terminal windows, one for each end host. You can now proceed to run each end host program in the corresponding window. For example, in the window for `h1`, you can write:

    python3 examples/bookreview/client.py

___Before you run the program, be aware___ that by default, sessions
time out after 10 seconds of not sending/receiving messages. You can
adjust this in the `closeAll()` calls by supplying `timeout=0` as an
argument. ___Furthermore___, the end host responsible for
propagating session IDs (in this case, the `Client`) must be started
_last_, as otherwise it may try to connect to other end hosts that are not yet ready to receive messages.

In summary, you should run the following commands in the respective windows in the sequence below, and within 10 seconds of each other:

    [h2] python3 examples/bookreview/info.py
    [h3] python3 examples/bookreview/review.py
    [h4] python3 examples/bookreview/details.py
    [h5] python3 examples/bookreview/ratings.py
    [h1] python3 examples/bookreview/client.py

If all goes well, you should see each program printing messages
showing that communication is taking place, and terminate with

    All done! (50/50 threads finished successfully)
    Threads timed out: []