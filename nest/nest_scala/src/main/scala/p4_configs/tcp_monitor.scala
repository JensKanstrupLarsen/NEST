package tcp_monitor

import p4rtype.{Exact, LPM, Optional, Range, Ternary, P4RTypeRuntimeObserver}
import com.google.protobuf.ByteString
import p4.v1.p4runtime.FieldMatch
import p4.v1.p4runtime.FieldMatch.FieldMatchType
import p4.v1.p4runtime.TableAction
import p4.v1.p4runtime.Action
import p4.v1.p4runtime.P4RuntimeGrpc.P4RuntimeStub
import p4.v1.p4runtime.TableEntry
import io.grpc.ManagedChannelBuilder
import p4.v1.p4runtime.StreamMessageRequest
import p4.v1.p4runtime.MasterArbitrationUpdate
import p4.v1.p4runtime.Uint128
import io.grpc.CallOptions
import p4.v1.p4runtime.StreamMessageResponse
import p4.v1.p4runtime.Action.Param

type TableName = "Process.receiver_table" | "Process.monitor_table" | "Process.ipv4_lpm" | "Process.check_ports"

type TableMatchFields[TN] =
  TN match
    case "Process.receiver_table" => p4rtype.MatchFields[(("hdr.sessionH.senderRole", Option[Optional]), ("hdr.sessionH.receiverRole", Option[Optional]), ("monitorState", Exact))]
    case "Process.monitor_table" => p4rtype.MatchFields[(("hdr.sessionH.senderRole", Option[Optional]), ("hdr.sessionH.messageID", Option[Optional]), ("hdr.sessionH.receiverRole", Option[Optional]), ("monitorState", Exact))]
    case "Process.ipv4_lpm" => p4rtype.MatchFields[("hdr.ipv4.dstAddr", Option[LPM])]
    case "Process.check_ports" => p4rtype.MatchFields[(("standard_metadata.ingress_port", Exact), ("standard_metadata.egress_spec", Exact))]

type TableAction[TN] =
  TN match
    case "Process.receiver_table" => p4rtype.Action["Process.approve",Unit] | p4rtype.Action["Process.disapprove",Unit]
    case "Process.monitor_table" => p4rtype.Action["Process.approve_next_state",(p4rtype.Param["nextStateTmp", ByteString])] | p4rtype.Action["Process.disapprove",Unit]
    case "Process.ipv4_lpm" => p4rtype.Action["Process.ipv4_forward",(p4rtype.Param["dstAddr", ByteString], p4rtype.Param["port", ByteString])] | p4rtype.Action["Process.drop",Unit]
    case "Process.check_ports" => p4rtype.Action["Process.set_ingress",Unit] | p4rtype.Action["Process.set_egress",Unit] | p4rtype.Action["NoAction",Unit]

class Chan (deviceId : Int, socket : P4RuntimeStub, channel : io.grpc.ManagedChannel, log : P4RTypeRuntimeObserver[StreamMessageResponse]) extends p4rtype.Chan[TableName, TableMatchFields, TableAction](deviceId, socket, channel):
  override def toProto(te : p4rtype.TableEntry[TableName, p4rtype.W[TableMatchFields], p4rtype.W[TableAction], _]) : TableEntry =
    val tableId =
      te.table match
        case "Process.receiver_table" => 39893881
        case "Process.monitor_table" => 44540466
        case "Process.ipv4_lpm" => 50014192
        case "Process.check_ports" => 40455705

    val matchFields =
      (te.table, te.matches) match
        case (_, "*") => Seq.empty
        case ("Process.receiver_table", p4rtype.MatchFields((("hdr.sessionH.senderRole", t0), ("hdr.sessionH.receiverRole", t1), ("monitorState", t2)))) => t0.asInstanceOf[Option[Optional]].map(t => p4rtype.matchFieldToProto(1, t)).toSeq ++ t1.asInstanceOf[Option[Optional]].map(t => p4rtype.matchFieldToProto(2, t)).toSeq ++ Seq(p4rtype.matchFieldToProto(3, t2.asInstanceOf[Exact]))
        case ("Process.monitor_table", p4rtype.MatchFields((("hdr.sessionH.senderRole", t0), ("hdr.sessionH.messageID", t1), ("hdr.sessionH.receiverRole", t2), ("monitorState", t3)))) => t0.asInstanceOf[Option[Optional]].map(t => p4rtype.matchFieldToProto(1, t)).toSeq ++ t1.asInstanceOf[Option[Optional]].map(t => p4rtype.matchFieldToProto(2, t)).toSeq ++ t2.asInstanceOf[Option[Optional]].map(t => p4rtype.matchFieldToProto(3, t)).toSeq ++ Seq(p4rtype.matchFieldToProto(4, t3.asInstanceOf[Exact]))
        case ("Process.ipv4_lpm", p4rtype.MatchFields((("hdr.ipv4.dstAddr", t0)))) => t0.asInstanceOf[Option[LPM]].map(t => p4rtype.matchFieldToProto(1, t)).toSeq
        case ("Process.check_ports", p4rtype.MatchFields((("standard_metadata.ingress_port", t0), ("standard_metadata.egress_spec", t1)))) => Seq(p4rtype.matchFieldToProto(1, t0.asInstanceOf[Exact])) ++ Seq(p4rtype.matchFieldToProto(2, t1.asInstanceOf[Exact]))
        case (_, _) => Seq.empty

    val actionId =
      te.action.asInstanceOf["*" | TableAction[te.table.type]] match
        case "*" => 0
        case p4rtype.Action("NoAction",_) => 21257015
        case p4rtype.Action("Process.drop",_) => 22338797
        case p4rtype.Action("Process.ipv4_forward",_) => 26706864
        case p4rtype.Action("Process.approve_next_state",_) => 32536123
        case p4rtype.Action("Process.approve",_) => 26339307
        case p4rtype.Action("Process.disapprove",_) => 33398173
        case p4rtype.Action("Process.set_ingress",_) => 18205648
        case p4rtype.Action("Process.set_egress",_) => 32871913
        case p4rtype.Action("Process.end_session",_) => 19072783
        case _ => 0
    val params =
      te.action.asInstanceOf["*" | TableAction[te.table.type]] match
        case "*" => Seq.empty
        case p4rtype.Action("NoAction", _) => Seq.empty
        case p4rtype.Action("Process.drop", _) => Seq.empty
        case p4rtype.Action("Process.ipv4_forward", (p4rtype.Param("dstAddr", p0), p4rtype.Param("port", p1))) => Seq(Param(paramId = 1, value = p0.asInstanceOf[ByteString])) ++ Seq(Param(paramId = 2, value = p1.asInstanceOf[ByteString]))
        case p4rtype.Action("Process.approve_next_state", (p4rtype.Param("nextStateTmp", p0))) => Seq(Param(paramId = 1, value = p0.asInstanceOf[ByteString]))
        case p4rtype.Action("Process.approve", _) => Seq.empty
        case p4rtype.Action("Process.disapprove", _) => Seq.empty
        case p4rtype.Action("Process.set_ingress", _) => Seq.empty
        case p4rtype.Action("Process.set_egress", _) => Seq.empty
        case p4rtype.Action("Process.end_session", _) => Seq.empty
        case _ => Seq.empty

    TableEntry(
      tableId = tableId,
      `match` = matchFields,
      action =
        if actionId != 0 then
          Some(TableAction(
            `type` = TableAction.Type.Action(
              value = Action(
                actionId = actionId,
                params = params
              )
            )
          ))
        else
          None,
      priority = te.priority
    )

  override def fromProto[TN, TM[_], TA[_], XN <: TN](te : TableEntry): p4rtype.TableEntry[TN, TM, TA, XN] =
    val actionId = te.action.get.`type`.action.get.actionId
    val teParams = te.action.get.`type`.action.get.params
    val table =
      te.tableId match
        case 39893881 => "Process.receiver_table"
        case 44540466 => "Process.monitor_table"
        case 50014192 => "Process.ipv4_lpm"
        case 40455705 => "Process.check_ports"
        case 0 => "*"
    val matches =
      te.tableId match
        case 39893881 => p4rtype.MatchFields(("hdr.sessionH.senderRole", te.`match`.find(_.fieldId == 1).map(fm => Optional(fm.fieldMatchType.optional.get.value))), ("hdr.sessionH.receiverRole", te.`match`.find(_.fieldId == 2).map(fm => Optional(fm.fieldMatchType.optional.get.value))), ("monitorState", te.`match`.find(_.fieldId == 3).map(fm => Exact(fm.fieldMatchType.exact.get.value)).get))
        case 44540466 => p4rtype.MatchFields(("hdr.sessionH.senderRole", te.`match`.find(_.fieldId == 1).map(fm => Optional(fm.fieldMatchType.optional.get.value))), ("hdr.sessionH.messageID", te.`match`.find(_.fieldId == 2).map(fm => Optional(fm.fieldMatchType.optional.get.value))), ("hdr.sessionH.receiverRole", te.`match`.find(_.fieldId == 3).map(fm => Optional(fm.fieldMatchType.optional.get.value))), ("monitorState", te.`match`.find(_.fieldId == 4).map(fm => Exact(fm.fieldMatchType.exact.get.value)).get))
        case 50014192 => p4rtype.MatchFields(("hdr.ipv4.dstAddr", te.`match`.find(_.fieldId == 1).map(fm => LPM(fm.fieldMatchType.lpm.get.value, fm.fieldMatchType.lpm.get.prefixLen))))
        case 40455705 => p4rtype.MatchFields(("standard_metadata.ingress_port", te.`match`.find(_.fieldId == 1).map(fm => Exact(fm.fieldMatchType.exact.get.value)).get), ("standard_metadata.egress_spec", te.`match`.find(_.fieldId == 2).map(fm => Exact(fm.fieldMatchType.exact.get.value)).get))
        case 0 => "*"
    val action =
      actionId match
        case 21257015 => "NoAction"
        case 22338797 => "Process.drop"
        case 26706864 => "Process.ipv4_forward"
        case 32536123 => "Process.approve_next_state"
        case 26339307 => "Process.approve"
        case 33398173 => "Process.disapprove"
        case 18205648 => "Process.set_ingress"
        case 32871913 => "Process.set_egress"
        case 19072783 => "Process.end_session"
        case 0 => "*"
    val params =
      actionId match
        case 21257015 => ()
        case 22338797 => ()
        case 26706864 => (teParams.find(_.paramId == 1).map(pm => p4rtype.Param("dstAddr", pm.value)).get, teParams.find(_.paramId == 2).map(pm => p4rtype.Param("port", pm.value)).get)
        case 32536123 => (teParams.find(_.paramId == 1).map(pm => p4rtype.Param("nextStateTmp", pm.value)).get)
        case 26339307 => ()
        case 33398173 => ()
        case 18205648 => ()
        case 32871913 => ()
        case 19072783 => ()
        case 0 => "*"
    val myTable : XN = table.asInstanceOf[XN]
    p4rtype.TableEntry[TN, TM, TA](
      table = myTable,
      matches = matches.asInstanceOf[TM[myTable.type]],
      action = p4rtype.Action(action,params).asInstanceOf[TA[myTable.type]],
      priority = te.priority
    ).asInstanceOf[p4rtype.TableEntry[TN, TM, TA, XN]]

  def getLog() = log.getLog()
type P4Entity = p4rtype.P4Entity[TableName, TableMatchFields, TableAction, _]

/** Connect to a P4Runtime server.
  * @param id The device ID, which is assigned by the controller (i.e. the caller), and should be unique for each controller.
  * @param ip IP address of the target device.
  * @param port Port number of the target device.
  * @return A `Chan` object used by the other P4R-Type API functions for communication.
  */
def connect(id : Int, ip : String, port : Int) : Chan =
  val channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build()
  val request = StreamMessageRequest(
      StreamMessageRequest.Update.Arbitration(
        value = MasterArbitrationUpdate(
          deviceId = id,
          electionId = Some(Uint128(high=0,low=1)),
        )
      )
    )
  val stub = P4RuntimeStub.newStub(channel, CallOptions.DEFAULT)
  val response_obs = new P4RTypeRuntimeObserver[StreamMessageResponse](StreamMessageResponse())
  val request_obs = stub.streamChannel(response_obs)
  request_obs.onNext(request)
  Thread.sleep(500) // Making sure the MasterArbitrationUpdate has been received, not elegantly
  // should wait for confirmation somehow using getLog()
  Chan(id, stub, channel, response_obs)