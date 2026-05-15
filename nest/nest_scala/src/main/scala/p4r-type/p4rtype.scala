package p4rtype

import p4.v1.p4runtime.FieldMatch
import p4.v1.p4runtime.TableAction
import com.google.protobuf.ByteString
import p4.v1.p4runtime.FieldMatch.FieldMatchType
import p4.v1.p4runtime.Action
import p4.v1.p4runtime.Action.Param
import p4.v1.p4runtime.P4RuntimeGrpc.P4RuntimeStub
import io.grpc.stub.StreamObserver
import p4.v1.p4runtime.ReadRequest
import p4.v1.p4runtime.ReadResponse
import p4.v1.p4runtime.Entity
import p4.v1.p4runtime.WriteRequest
import p4.v1.p4runtime.Update
import p4.v1.p4runtime.Uint128
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS
import io.grpc.ManagedChannel
import p4.config.v1.p4info.Register
import p4.v1.p4data.P4Data
import p4.v1.p4data.P4Data.Data
import scala.util.Failure
import scala.util.Success

case class Wildcard private ()
case object Wildcard

case class Exact(v : ByteString)
case class LPM(v : ByteString, pl : Int)
case class Range(l : ByteString, h : ByteString)
case class Ternary(v : ByteString, m : ByteString)
case class Optional(v : ByteString)

type MatchFieldType = Exact | LPM | Range | Ternary | Optional


/*
x : List[TableEntry Tm Ta Tp "Firewall" "*"]

TableEntry Tm Ta Tp "Firewall" "*" ::=
  {name : "Firewall", matches : Tm["Firewall"],
   action : Ta["Firewall"], params : Tp["*"]}

*/


def matchFieldToProto(fieldId : Int, mf : MatchFieldType) : FieldMatch =
  mf match
    case Exact(v)      => FieldMatch(fieldId, FieldMatchType.Exact(FieldMatch.Exact(value = v)))
    case LPM(v, pl)    => FieldMatch(fieldId, FieldMatchType.Lpm(FieldMatch.LPM(value = v, prefixLen = pl)))
    case Range(l, h)   => FieldMatch(fieldId, FieldMatchType.Range(FieldMatch.Range(low = l, high = h)))
    case Ternary(v, m) => FieldMatch(fieldId, FieldMatchType.Ternary(FieldMatch.Ternary(value = v, mask = m)))
    case Optional(v)   => FieldMatch(fieldId, FieldMatchType.Optional(FieldMatch.Optional(value = v)))

// def matchFieldToJson(mf : MatchFieldType) : String =
//   mf match
//     case Exact(v)      => "["+v.toString()+"]"
//     case LPM(v, pl)    => "["+v.toString()"]"
//     case Range(l, h)   => 
//     case Ternary(v, m) => 
//     case Optional(v)   => 

case class MatchFields[XM] (matches : XM)
case class Action[XA, XP] (action : XA, params : XP)

case class Param[Name, Value] (name : Name, value : Value)
//object Param:
//  def apply[Value] (name : String, value : Value) : Param[name.type,value.type] =
//    Param[name.type,value.type](name,value)


sealed abstract case class TableEntry[TN, TM[_], TA[_], XN <: TN](table : XN, matches : TM[XN], action : TA[XN], priority : Int)

private class TableEntryImpl[TN, TM[_], TA[_], XN <: TN](table : XN, matches : TM[XN], action : TA[XN], priority : Int) extends TableEntry[TN, TM, TA, XN](table, matches, action, priority){}

/** Represents a table entry in a control plane table.
  */
object TableEntry:
  def apply[TN, TM[_], TA[_]] (table: TN, matches : TM[table.type], action : TA[table.type], priority : Int) : TableEntry[TN, TM, TA, table.type] =
    new TableEntryImpl[TN, TM, TA, table.type](table, matches, action, priority)

case class CounterEntry (counter_id : Int, index : Option[Long], data : Option[(Long, Long)])
case class RegisterEntry (register_id : Int, index : Long, data : ByteString)

type P4Entity[TN, TM[_], TA[_], XN <: TN] = TableEntry[TN, TM, TA, XN] | CounterEntry | RegisterEntry

type W[M[_]] = [X] =>> "*" | M[X]

/** Represents a connection channel to a target device.
  */
abstract class Chan[TN, TM[_], TA[_]] (deviceId : Int, socket : P4RuntimeStub, channel : ManagedChannel):
  /** Returns the device ID of the target device.
    * This is assigned by the controller (i.e. the caller),
    * and should be unique for each controller.
    */
  def getDeviceId() : Int = deviceId
  /** Returns the socket used to communicate with the target device.
    */
  def getSocket() : P4RuntimeStub = socket
  /** Converts a `TableEntry` from the P4R-Type representation
    * to the underlying protobuf representation.
    */
  def toProto(te : TableEntry[TN, W[TM], W[TA], _]) : p4.v1.p4runtime.TableEntry
  /** Converts a `CounterEntry` from the P4R-Type representation
    * to the underlying protobuf representation.
    */
  def toProto(c : CounterEntry) : p4.v1.p4runtime.CounterEntry =
    p4.v1.p4runtime.CounterEntry(
      counterId = c.counter_id,
      index = c.index.map(l => p4.v1.p4runtime.Index(l)),
      data = c.data.map((byteCount, packetCount) => p4.v1.p4runtime.CounterData(byteCount, packetCount))
  )
  /** Converts a `TableEntry` from the underlying protobuf representation
    * to the P4R-Type representation.
    */
  def fromProto[TN, TM[_], TA[_], XN <: TN](te : p4.v1.p4runtime.TableEntry) : TableEntry[TN, TM, TA, XN]
  /** Converts a `CounterEntry` from the underlying protobuf representation
    * to the P4R-Type representation.
    */
  def fromProto(c : p4.v1.p4runtime.CounterEntry) : CounterEntry =
  CounterEntry(
    counter_id = c.counterId,
    index = c.index.map(i => i.index),
    data = c.data.map(d => (d.byteCount, d.packetCount))
  )
  /** Disconnects the channel, shutting down the connection to the target.
    */
  def disconnect() : Unit =
    channel.shutdownNow()

class P4RTypeRuntimeObserver[O](lock : Object) extends StreamObserver[O] {
  private var log : Seq[O] = Seq()
  def getLog() : Seq[O] = log
  override def onNext(value: O): Unit =
    log = log :+ value
  override def onCompleted(): Unit =
    lock.synchronized {
      lock.notify()
    }
  override def onError(t: Throwable): Unit =
    print("[ERROR] " + t.toString() + "\n")
}

/** Internal function that reads by matching entities with a 'querying' entity.
  *
  * @param c The channel used to communicate with the target device.
  * @param entity The entity to match on. For details on how the matching works, see the P4Runtime specification.
  * @return A list of entities that match the given entity.
  */
private def readAny[TN, TM[_], TA[_], XN <: TN]
  (c : Chan[TN, TM, TA], entity : P4Entity[TN, W[TM], W[TA], XN]) : Seq[P4Entity[TN, TM, TA, XN]] =
  val lock = Object()
  val read_observer = new P4RTypeRuntimeObserver[p4.v1.p4runtime.ReadResponse](lock)
  c.getSocket().read(
    request = ReadRequest(
      deviceId = c.getDeviceId(),
      entities = List(
        Entity (
          entity match
            case te@p4rtype.TableEntry(_,_,_,_) => Entity.Entity.TableEntry(c.toProto(te))
            case ce@CounterEntry(_,_,_) => Entity.Entity.CounterEntry(c.toProto(ce))
            case _ => Entity.Entity.Empty
        )
      )
    ),
    read_observer
  )
  lock.synchronized {
    lock.wait()
  }
  read_observer.getLog().last.entities.foldLeft(List[P4Entity[TN, TM, TA, XN]]())((acc, e) => {
    e.entity match
      case Entity.Entity.TableEntry(te) => acc :+ c.fromProto[TN, TM, TA, XN](te)
      case Entity.Entity.CounterEntry(ce) => acc :+ c.fromProto(ce)
      case _ => acc
  })

/** Reads the contents of a table by matching entries with a given table entry.
  *
  * @param c The channel used to communicate with the target device.
  * @param entity The table entry to match on. For details on how the matching works, see the P4Runtime specification.
  * @return A list of table entries that match the given table entry.
  */

def read[TN, TM[_], TA[_], XN <: TN]
  (c : Chan[TN, TM, TA], entity : TableEntry[TN, W[TM], W[TA], XN]) : Seq[TableEntry[TN, TM, TA, XN]] =
  readAny(c, entity).asInstanceOf[Seq[TableEntry[TN, TM, TA, XN]]]

/** Reads the contents of one or more counter entries.
  *
  * @param c The channel used to communicate with the target device.
  * @param entity The 'querying' counter entry to match on. For details on how the matching works, see the P4Runtime specification.
  * @return A list of counter entries that match the given entry.
  */
def readCounterEntry
  (c : Chan[_, _, _], entity : CounterEntry) : Seq[CounterEntry] =
  readAny(c, entity).asInstanceOf[Seq[CounterEntry]]

/** Writes a table entry to a table.
  * API users should not call this method directly, but instead use the `insert`, `modify`, and `delete` methods.
  *
  * @param c The channel used to communicate with the target device.
  * @param tableEntry The table entry to write.
  * @param ut The type of the update. See the P4Runtime specification for a list and description of update types.
  * @return A boolean indicating whether or not the write was successful (true if successful, false otherwise).
  */
def write [TN, TM[_], TA[_]]
  (c : Chan[TN, TM, TA], entity : P4Entity[TN, W[TM], W[TA], _], ut : Update.Type) : Boolean =
  val resp : concurrent.Future[p4.v1.p4runtime.WriteResponse] = c.getSocket().write(WriteRequest(
    deviceId = c.getDeviceId(),
    electionId = Some(Uint128(high=0,low=1)),
    updates = List(Update(
      `type` = ut,
      entity = Some(Entity(
        entity match
          case te@TableEntry(_,_,_,_) => Entity.Entity.TableEntry(c.toProto(te.asInstanceOf[TableEntry[TN,W[TM],W[TA],_]]))
          case ce@CounterEntry(_,_,_) => Entity.Entity.CounterEntry(c.toProto(ce))
          case re@RegisterEntry(regId,regIndex,data) =>
              Entity.Entity.RegisterEntry(
                p4.v1.p4runtime.RegisterEntry(
                  registerId = regId,
                  index = Some(p4.v1.p4runtime.Index(regIndex)),
                  data = Some(P4Data(data = p4.v1.p4data.P4Data.Data.Bitstring(data)))
                )
              )
          case _ => Entity.Entity.Empty
      ))
    ))
  ))
  try
    val response = Await.ready(resp, FiniteDuration(10, SECONDS))
    response.value match
      case Some(v) =>
        v match
          case Failure(exception) =>
            println(exception.getMessage())
            println(exception.toString())
            throw exception
          case Success(value) =>
            true
      case _ =>
        println("Timeout...?")
        false
    catch
      case e =>
        false

/** Inserts a table entry into a table.
  *
  * @param c The channel used to communicate with the target device.
  * @param tableEntry The table entry to insert. For details on how the operation works, see the P4Runtime specification.
  * @return A boolean indicating whether or not the insertion was successful (true if successful, false otherwise).
  */
def insert [TN, TM[_], TA[_], XN <: TN]
  (c : Chan[TN, TM, TA], entity : P4Entity[TN, TM, TA, XN]) : Boolean =
  write(c, entity.asInstanceOf[P4Entity[TN, W[TM],W[TA],XN]], Update.Type.INSERT)

/** Modifies a table entry in a table.
  *
  * @param c The channel used to communicate with the target device.
  * @param tableEntry The table entry to modify. For details on how the operation works, see the P4Runtime specification.
  * @return A boolean indicating whether or not the modification was successful (true if successful, false otherwise).
  */
def modify [TN, TM[_], TA[_], XN <: TN]
  (c : Chan[TN, TM, TA], entity : P4Entity[TN, TM, TA, XN]) : Boolean =
  write(c, entity.asInstanceOf[P4Entity[TN, W[TM],W[TA],XN]], Update.Type.MODIFY)

/** Deletes a table entry from a table.
  *
  * @param c The channel used to communicate with the target device.
  * @param tableEntry The table entry to delete. For details on how the operation works, see the P4Runtime specification.
  * @return A boolean indicating whether or not the deletion was successful (true if successful, false otherwise).
  */
def delete [TN, TM[_], TA[_], XN <: TN]
  (c : Chan[TN, TM, TA], entity : P4Entity[TN, W[TM], W[TA], XN]) : Boolean =
  write(c, entity, Update.Type.DELETE)

/** A helper function for generating bytestrings from sequences of bytes.
  *
  * @param a A sequence of bytes (which may be represented as integers).
  * @return A bytestring containing the given bytes.
  */
def bytes (a : Byte*) : ByteString = ByteString.copyFrom(a.toArray)
