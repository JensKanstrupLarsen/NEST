package sessiontypes

import p4rtype._
import com.google.protobuf.ByteString
import scala.io.StdIn.readLine

type AgentID = ByteString
type IPAddress = ByteString
case class STAgent(name : String, id : AgentID, ip : IPAddress):
    override def toString(): String = name
def newAgent(name : String, id : Byte, address : ByteString) : STAgent =
    STAgent(name, bytes(id), address)

// type LabelID = ByteString
case class STLabel(text : String, fields : Map[String, ByteString]):
    override def toString(): String =
        return "[" + text + "]"
        // return "[" + id.byteAt(0) + ": " + text + "]"
def newLabel(text : String, value : Byte) : STLabel =
    STLabel(text, Map("hdr.sessionH.messageID" -> bytes(value)))

case class TrsLabel(sender : STAgent, label : STLabel, receiver : STAgent):
    override def toString(): String =
        "(" + sender + " --" + label + "-> " + receiver + ")"

type STState = String
//case class STState(name : String)

enum LocalSessionType:
    case End() extends LocalSessionType
    case Var(x : String) extends LocalSessionType
    case Rec(x : String, cont : LocalSessionType) extends LocalSessionType
    case IntChoice(branches : Map[(STAgent, STLabel), LocalSessionType]) extends LocalSessionType
    case ExtChoice(sender : STAgent, branches : Map[(STLabel), LocalSessionType]) extends LocalSessionType
import LocalSessionType._

enum InputQueue:
    case Q_Nil() extends InputQueue
    case Q_Msg(from : STAgent, to : STAgent, label : STLabel, queue : InputQueue) extends InputQueue
import InputQueue._

enum ExternalLabel:
    case EL_Send(receiver : STAgent, label : STLabel) extends ExternalLabel
    case EL_Recv(sender : STAgent, label : STLabel) extends ExternalLabel
import ExternalLabel._

enum SessionTypeLabel:
    case STL_External(label : ExternalLabel) extends SessionTypeLabel
    case STL_Internal(from : STAgent, to : STAgent, label : STLabel)
import SessionTypeLabel._

// Unfolds a session type with a given variable and continuation
def unfold(lst : LocalSessionType, x : String, rec : LocalSessionType) : LocalSessionType =
    lst match
        case End() => End()
        case Var(y) => if y == x then rec else Var(y)
        case Rec(y, cont) => if y == x then Rec(y, cont) else Rec(y, unfold(cont, x, rec))
        case IntChoice(branches) => IntChoice(branches.map((br,cont) => (br,unfold(cont, x, rec))))
        case ExtChoice(sender, branches) => ExtChoice(sender, branches.map((br,cont) => (br,unfold(cont, x, rec))))

// 
def foldBranches[B](branches : Map[B, LocalSessionType], extLabel : ExternalLabel, seenStates : Set[LocalSessionType]) : Map[B, LocalSessionType] =
    branches.foldLeft(Map[B, LocalSessionType]())((acc,br) =>
        val (branchCase, cont) = br
        takeBranch(cont, extLabel, seenStates) match
            case None => acc
            case Some(newCont) =>
                acc + (branchCase -> newCont))

/** Computes the maximal residual of `lst` under `extLabel`, if accepted.
 *
 *  A send is accepted only by a direct branch hit (internal choice); a receive
 *  is accepted either by a direct hit (external choice from the same sender)
 *  or by keeping exactly the branches whose targets accept it (prefix), which
 *  additionally requires the sender to differ from the choice owners.
 */
def takeBranch(lst : LocalSessionType, extLabel : ExternalLabel, seenStates : Set[LocalSessionType]) : Option[LocalSessionType] =
    if !seenStates.contains(lst) then
        lst match
            case End() => None
            case Var(x) => None
            case Rec(x, cont) => takeBranch(unfold(cont, x, Rec(x, cont)), extLabel, seenStates + lst)
            case IntChoice(branches) =>
                extLabel match
                    // Direct hit yields the branch target; a miss is rejected.
                    case EL_Send(receiver, label) =>
                        branches.get((receiver, label)) match
                            case None => None
                            case Some(cont) => Some(cont)
                    case EL_Recv(sender, label) =>
                        // Prefix keeps the branches whose targets accept the receive,
                        // provided the sender differs from every branch receiver.
                        if branches.keys.forall((receiver, _) => receiver != sender) then
                            val newBranches = foldBranches(branches, extLabel, seenStates + lst)
                            if newBranches.isEmpty then None else Some(IntChoice(newBranches))
                        else None
            case ExtChoice(choiceSender, branches) =>
                extLabel match
                    case EL_Send(_, _) =>
                        // External choices never accept sends.
                        None
                    case EL_Recv(recvSender, label) =>
                        if recvSender == choiceSender then
                            // Direct hit yields the branch target; a miss is rejected.
                            branches.get(label) match
                                case None => None
                                case Some(cont) => Some(cont)
                        else
                            // Prefix keeps the branches whose targets accept the receive.
                            val newBranches = foldBranches(branches, extLabel, seenStates + lst)
                            if newBranches.isEmpty then None else Some(ExtChoice(choiceSender, newBranches))
    else
        None

// Get all possible transitions from the given session type
// hasSentTo and hasRcvdFrom should be empty initially
def getContextTrs(lst : LocalSessionType, hasSentTo : List[STAgent], hasRcvdFrom : List[STAgent], seenStates : Set[LocalSessionType])
    : Set[(LocalSessionType, ExternalLabel)] =
    if !seenStates.contains(lst) then
        lst match
            case End() => Set()
            case Var(x) => throw Exception("Open type!")
            case Rec(x, cont) =>
                // Unfold the recursion and try again, but remember the old type before unfolding
                getContextTrs(unfold(cont, x, Rec(x, cont)), hasSentTo, hasRcvdFrom, seenStates + lst)
            case IntChoice(branches) =>
                branches.foldLeft(Set[(LocalSessionType, ExternalLabel)]())((acc, kv) =>
                    val ((receiver, label), cont) = kv
                    acc
                    ++ (if  hasRcvdFrom.isEmpty && hasSentTo.isEmpty // If we allow outgoing reordering, instead use !hasSentTo.contains(receiver)
                        then Set((cont, EL_Send(receiver, label)))
                        else Set())
                    ++ getContextTrs(cont, hasSentTo :+ receiver, hasRcvdFrom, seenStates + lst).map((contNew,l) =>
                            val newBranches = foldBranches(branches - ((receiver, label)), l, seenStates + lst)
                            (IntChoice(newBranches + ((receiver, label) -> contNew)), l)))
            case ExtChoice(sender, branches) =>
                branches.foldLeft(Set[(LocalSessionType, ExternalLabel)]())((acc, kv) =>
                    val (label, cont) = kv
                    acc
                    ++ (if !hasRcvdFrom.contains(sender) && !hasSentTo.contains(sender) // <- half duplex only
                        then Set((cont, EL_Recv(sender, label)))
                        else Set())
                    ++ getContextTrs(cont, hasSentTo, hasRcvdFrom :+ sender, seenStates + lst).map((contNew,l) =>
                            val newBranches = foldBranches(branches - label, l, seenStates + lst)
                            (ExtChoice(sender, newBranches + (label -> contNew)), l)))
    else
        Set()

/** Collects every external label observable from `lst`.
 *
 *  Sends come from internal-choice branches and receives come from
 *  external-choice branches (paired with the offering sender); recursion
 *  is unfolded once per visited type so the result stays finite.
 */
def alphabetOf(lst : LocalSessionType) : Set[ExternalLabel] =
    def go(t : LocalSessionType, seen : Set[LocalSessionType]) : Set[ExternalLabel] =
        if seen.contains(t) then Set()
        else
            t match
                case End() => Set()
                case Var(_) => Set()
                case Rec(x, cont) =>
                    go(unfold(cont, x, Rec(x, cont)), seen + t)
                case IntChoice(branches) =>
                    branches.foldLeft(Set[ExternalLabel]())((acc, kv) =>
                        val ((receiver, label), cont) = kv
                        acc + EL_Send(receiver, label) ++ go(cont, seen + t))
                case ExtChoice(sender, branches) =>
                    branches.foldLeft(Set[ExternalLabel]())((acc, kv) =>
                        val (label, cont) = kv
                        acc + EL_Recv(sender, label) ++ go(cont, seen + t))
    go(lst, Set())

/** Computes the accepted transitions out of `lst`.
 *
 *  Each element pairs a maximal residual with the label that reaches it;
 *  labels with no residual are dropped.
 */
def monitorSteps(lst : LocalSessionType) : Set[(LocalSessionType, ExternalLabel)] =
    alphabetOf(lst).flatMap(label =>
        takeBranch(lst, label, Set()) match
            case None => Set()
            case Some(next) => Set((next, label)))

/** Builds the monitor state machine for agent `a` observing `lst`.
 *
 *  Each reachable session type becomes one state. Every state is expanded once:
 *  each accepted external label yields one transition carrying the label, and
 *  the transition targets the maximal residual under that label. Residuals not
 *  seen before join the work set until no new states remain.
 */
def LST_to_SM(lst : LocalSessionType, a : STAgent) : StateMachine =
    // Mapping session types to (integer) states
    var stateMap = Map[LocalSessionType, Int]()
    // Tracking all transitions between session types
    var trs = Set[(LocalSessionType, ExternalLabel, LocalSessionType)]()
    var currentStateID = 0
    var workingStates = Set(lst)
    while (workingStates.nonEmpty) do
        val workingState = workingStates.head
        workingStates = workingStates.tail
        // If this session type isn't already mapped to a state
        if (!stateMap.contains(workingState)) then
            // Accepted outgoing transitions of this state, each with its residual.
            val newTrs = monitorSteps(workingState)
            // Add the session type to the state map
            stateMap += (workingState -> currentStateID)
            currentStateID += 1
            // Explore every residual reached from this state.
            workingStates ++= newTrs.map((next, _) => next)
            // Record one transition per accepted label.
            trs ++= newTrs.map((next, label) => (workingState, label, next))
            if (stateMap.size > 1000) then
                throw Exception("Kablooie (too many states)")
    return StateMachine(stateMap.values.toSet, trs.map((s,l,t) =>
            l match
                case EL_Send(receiver, label) => Transition(stateMap(s), TrsLabel(a, label, receiver), stateMap(t))
                case EL_Recv(sender, label) => Transition(stateMap(s), TrsLabel(sender, label, a), stateMap(t))))

type StateMachineState = Set[STState]

type State = Int
case class Transition(prevState : State, trsLabel : TrsLabel, nextState : State):
    override def toString(): String =
        "(" + prevState + ", " + trsLabel.toString() + ", " + nextState + ")"
case class StateMachine(states : Set[State], transitions : Set[Transition])

enum CompositeMonitor:
    case Monitor(id : AgentID, msa : StateMachine) extends CompositeMonitor
    case Parallel(n1 : CompositeMonitor, n2 : CompositeMonitor)
    case Restrict(a1 : STAgent, a2 : STAgent, n : CompositeMonitor)
import CompositeMonitor._
def monitor(a : STAgent, msa : StateMachine) : CompositeMonitor =
    Monitor(a.id, msa)
def parallel(ms : List[CompositeMonitor]) : CompositeMonitor =
    ms match
        case m :: Nil =>
            m
        case m :: next =>
            Parallel(m, parallel(next))
        case Nil =>
            throw Exception("Can't have 0 monitors in parallel")
def restrict(as : List[STAgent], m : CompositeMonitor) : CompositeMonitor =
    var cm = m
    for a1 <- as do
        for a2 <- as do
            if (a1 != a2) then
                cm = Restrict(a1, a2, cm)
    return cm

enum NetTransition:
    case TrsInternal(trsLabel : TrsLabel) extends NetTransition
    case TrsExternal(trsLabel : TrsLabel) extends NetTransition
import NetTransition._

case class HelperState(
    currentState : Int,
    netStates : Map[Int, Map[AgentID, State]],
    trs : Map[(Int, NetTransition), Int], // Left: External, Right: Internal
    openTrs : List[(AgentID, Transition)],
    restricted : List[(AgentID, AgentID)])

def getInitAgentState(n : CompositeMonitor) : Map[AgentID, State] =
    n match
        case Monitor(a, msaDot) =>
            Map(a -> 0)
        case Parallel(n1, n2) =>
            getInitAgentState(n1) ++ getInitAgentState(n2)
        case Restrict(a1, a2, n) =>
            getInitAgentState(n)

def isRestricted(sender : AgentID, receiver : AgentID, restricted : List[(AgentID, AgentID)]) : Boolean =
    restricted.exists((snd, rcv) => sender == snd && receiver == rcv)

def getMatchingTr(agentID : AgentID, trsLabel : TrsLabel, openTrs : List[(AgentID, Transition)]) : Option[(AgentID, Transition)] =
    openTrs.find((a2, tr2) =>
        trsLabel == tr2.trsLabel && agentID != a2)

def getStateID(netStates : Map[Int, Map[AgentID, State]], netState : Map[AgentID, State]) : Either[Int, Int] =
    netStates.foldLeft(Left(0) : Either[Int,Int])((acc, kv) =>
        var (otherStateID, otherNetState) = kv
        acc match
            case Left(stateID) =>
                if netState == otherNetState then
                    Right(otherStateID)
                else if stateID <= otherStateID then
                    Left(otherStateID+1)
                else
                    Left(stateID)
            case Right(stateID) =>
                Right(stateID))

def stateAsInt(s : State) : Int = s

def CM_to_MSA_step(m : CompositeMonitor, s : HelperState) : HelperState =
    m match
        case Monitor(a, msa) =>
            msa.transitions
                .filter((tr) =>
                    tr.prevState == s.netStates(s.currentState)(a))
                .foldLeft(s)((accState, tr) =>
                    val trLabel = tr.trsLabel
                    if isRestricted(trLabel.sender.id, trLabel.receiver.id, accState.restricted) then
                        getMatchingTr(a, trLabel, s.openTrs) match
                            case None =>
                                HelperState(
                                    accState.currentState,
                                    accState.netStates,
                                    accState.trs,
                                    accState.openTrs :+ (a, tr),
                                    accState.restricted)
                            case Some((a2,tr2)) =>
                                val newState =
                                    s.netStates(s.currentState)
                                    + (a -> tr.nextState)
                                    + (a2 -> tr2.nextState)
                                getStateID(accState.netStates, newState) match
                                    case Left(newStateID) =>
                                        HelperState(
                                            accState.currentState,
                                            accState.netStates + (newStateID -> newState),
                                            accState.trs + ((s.currentState, TrsInternal(trLabel)) -> newStateID),
                                            accState.openTrs,
                                            accState.restricted)
                                    case Right(stateID) =>
                                        HelperState(
                                            accState.currentState,
                                            accState.netStates,
                                            accState.trs + ((s.currentState, TrsInternal(trLabel)) -> stateID),
                                            accState.openTrs,
                                            accState.restricted)
                    else
                        val newState =
                            s.netStates(s.currentState)
                            + (a -> tr.nextState)
                        getStateID(accState.netStates, newState) match
                            case Left(newStateID) =>
                                HelperState(
                                    accState.currentState,
                                    accState.netStates + (newStateID -> newState),
                                    accState.trs + ((s.currentState, TrsExternal(trLabel)) -> newStateID),
                                    accState.openTrs,
                                    accState.restricted)
                            case Right(stateID) =>
                                HelperState(
                                    accState.currentState,
                                    accState.netStates,
                                    accState.trs + ((s.currentState, TrsExternal(trLabel)) -> stateID),
                                    accState.openTrs,
                                    accState.restricted))
        case Parallel(n1, n2) =>
            val s1 = CM_to_MSA_step(n1, s)
            CM_to_MSA_step(n2, s1)
        case Restrict(a1, a2, n) =>
            val s_new = HelperState(
                s.currentState,
                s.netStates,
                s.trs,
                s.openTrs,
                s.restricted :+ (a1.id, a2.id))
            CM_to_MSA_step(n, s_new)

def CM_to_MSA(m : CompositeMonitor) : StateMachine =
    var lastState = HelperState(
        0,
        Map(0 -> getInitAgentState(m)),
        Map(),
        List(),
        List())
    var stateToCheck = 0
    while (lastState.netStates.exists((stateID,_) => stateID == stateToCheck))
        val nextState = CM_to_MSA_step(m, lastState)
        stateToCheck += 1
        lastState = HelperState(
            stateToCheck,
            nextState.netStates,
            nextState.trs,
            List(),
            List())
    StateMachine(
        lastState.netStates.keys.toSet,
        lastState.trs.map((k, nextState) =>
            val (prevState, netLabel) = k
            val trsLabel =
                netLabel match
                    case TrsInternal(l) => l
                    case TrsExternal(l) => l
            Transition(prevState, trsLabel, nextState)).toSet)

def makeUnique[A](ls : List[A]) : List[A] =
    ls match
        case head :: next =>
            head :: makeUnique(next.filter(_ != head))
        case Nil => Nil

type PacketField = (String, ByteString)
type MonitorTableEntry = ((STAgent, PacketField, STAgent), State, State)

def MSA_to_TableEntries[XM, XA](msa : StateMachine, toTableEntry : Transition => (XM, XA)) : List[(XM, XA)] =
    msa.transitions.map(toTableEntry(_)).toList

def printDotFromMSA(msa : StateMachine) : Unit =
    println("digraph M {")
    for (s <- msa.states)
        val stateInt = s
        if (stateInt == 0)
            println("  " + stateInt + "[shape=circle, peripheries=2]")
        else
            println("  " + stateInt + "[shape=circle]")
    for (tr <- msa.transitions)
        var label =
            tr match
                case Transition(prevState, trsLabel, nextState) =>
                    println("  " + prevState + " -> " + nextState + " [label=\"" + trsLabel.toString() + "\"]")
    println("}\n")

def printDotFromStateMachine(StateMachine : StateMachine) : Unit =
    println("digraph M {")
    for (s <- StateMachine.states)
        println("  " + s.toString() + " [shape=circle, peripheries=2]")
    for (tr <- StateMachine.transitions)
        val trsLabel = tr.trsLabel
        println("  " + tr.prevState + " -> " + tr.nextState
            + " [label=\"" + trsLabel.sender + "--" + trsLabel.label + "->" + trsLabel.receiver + "\"]")
    println("}\n")