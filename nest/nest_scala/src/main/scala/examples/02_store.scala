package store

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine

val a_Client    = newAgent("ExternalClient", 1, bytes(10,10,1,1))
val a_HTTP      = newAgent("HTTP_Gateway", 2, bytes(10,0,2,2))
val a_Inventory = newAgent("Inventory", 3, bytes(10,20,3,3))
val a_Payment   = newAgent("Payment", 4, bytes(10,20,4,4))
val a_Shipping  = newAgent("Shipping", 5, bytes(10,20,5,5))
val a_Bank      = newAgent("ExternalBank", 6, bytes(10,10,6,6))
val a_Email     = newAgent("ExternalMailserver", 7, bytes(10,10,7,7))

val l_PlaceOrder    = newLabel("PlaceOrder", 1)
val l_GetSKUDetails = newLabel("GetSKUDetails", 2)
val l_SKUDetails    = newLabel("SKUDetails", 3)
val l_ConfirmOrder  = newLabel("ConfirmOrder", 4)
val l_VerifyCC      = newLabel("VerifyCC", 5)
val l_ValidCC       = newLabel("ValidCC", 6)
val l_InvalidCC     = newLabel("InvalidCC", 7)
val l_MakeCharge    = newLabel("MakeCharge", 8)
val l_MakeOrder     = newLabel("MakeOrder", 9)
val l_ShipOrder     = newLabel("ShipOrder", 10)
val l_ValidOrder    = newLabel("ValidOrder", 11)
val l_InvalidOrder  = newLabel("InvalidOrder", 12)
val l_SendEmail     = newLabel("SendOrderShippedEmail", 13)
val l_Success       = newLabel("Success", 14)
val l_CancelOrder   = newLabel("CancelOrder", 15)
val l_Cancel        = newLabel("Cancel", 16)

val lst_Client =
    IntChoice(Map((a_HTTP, l_PlaceOrder) ->
    ExtChoice(a_HTTP, Map(l_SKUDetails ->
    Rec("decideOnOrder",
        IntChoice(Map(
            (a_HTTP, l_ConfirmOrder) ->
                ExtChoice(a_HTTP, Map(
                    l_ValidCC ->
                        End(),
                    l_InvalidCC ->
                        Var("decideOnOrder"))),
            (a_HTTP, l_CancelOrder) ->
                ExtChoice(a_HTTP, Map(l_Cancel ->
                End())))))))))

val lst_HTTP =
    ExtChoice(a_Client, Map(l_PlaceOrder ->
    IntChoice(Map((a_Inventory, l_GetSKUDetails) ->
    ExtChoice(a_Inventory, Map(l_SKUDetails ->
    IntChoice(Map((a_Client, l_SKUDetails) ->
    Rec("decideOnOrder",
        ExtChoice(a_Client, Map(
            l_ConfirmOrder
            ->  IntChoice(Map((a_Payment, l_VerifyCC) ->
                ExtChoice(a_Payment, Map(
                    l_ValidCC ->
                        IntChoice(Map((a_Payment, l_MakeCharge) ->
                        IntChoice(Map((a_Inventory, l_MakeOrder) ->
                        IntChoice(Map((a_Client, l_ValidCC) ->
                        End())))))),
                    l_InvalidCC ->
                        IntChoice(Map((a_Client, l_InvalidCC) ->
                        Var("decideOnOrder"))))))),
            l_CancelOrder ->
                IntChoice(Map((a_Client, l_Cancel) ->
                End())))))))))))))

val lst_Inventory =
    ExtChoice(a_HTTP, Map(l_GetSKUDetails ->
    IntChoice(Map((a_HTTP, l_SKUDetails) ->
    ExtChoice(a_HTTP, Map(l_MakeOrder ->
    IntChoice(Map((a_Shipping, l_ShipOrder) ->
    End()))))))))

val lst_Payment =
    Rec("checkCC",
        ExtChoice(a_HTTP, Map(l_VerifyCC ->
        IntChoice(Map(
            (a_HTTP, l_ValidCC) ->
                ExtChoice(a_HTTP, Map(l_MakeCharge ->
                IntChoice(Map((a_Bank, l_MakeCharge) ->
                End())))),
            (a_HTTP, l_InvalidCC) ->
                Var("checkCC"))))))

val lst_Shipping =
    ExtChoice(a_Inventory, Map(l_ShipOrder ->
    IntChoice(Map((a_Email, l_SendEmail) ->
    ExtChoice(a_Email, Map(l_Success ->
    End()))))))

val lst_Bank =
    ExtChoice(a_Payment, Map(l_MakeCharge ->
    End()))

val lst_Email =
    ExtChoice(a_Shipping, Map(l_SendEmail ->
    IntChoice(Map((a_Shipping, l_Success) ->
    End()))))

@main def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
    val s2 = constructSwitch(1, "127.0.0.1", 50052, isTCP)
    val s3 = constructSwitch(2, "127.0.0.1", 50053, isTCP)
    val s4 = constructSwitch(3, "127.0.0.1", 50054, isTCP)
    val s5 = constructSwitch(4, "127.0.0.1", 50055, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,10,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,10,6,6), bytes(8,0,0,0,6,102), bytes(2)),
        (s1, bytes(10,10,7,7), bytes(8,0,0,0,7,119), bytes(3)),
        (s1, bytes(10), bytes(8,0,0,0,0,0), bytes(4)),

        (s2, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(1)),
        (s2, bytes(10,10), bytes(8,0,0,0,0,0), bytes(2)),
        (s2, bytes(10,20,3,3), bytes(8,0,0,0,3,51), bytes(3)),
        (s2, bytes(10,20,4,4), bytes(8,0,0,0,4,68), bytes(4)),
        (s2, bytes(10,20,5,5), bytes(8,0,0,0,5,85), bytes(5)),

        (s3, bytes(10,20,3,3), bytes(8,0,0,0,3,51), bytes(1)),
        (s3, bytes(10), bytes(8,0,0,0,0,0), bytes(2)),

        (s4, bytes(10,20,4,4), bytes(8,0,0,0,4,68), bytes(1)),
        (s4, bytes(10), bytes(8,0,0,0,0,0), bytes(2)),

        (s5, bytes(10,20,5,5), bytes(8,0,0,0,5,85), bytes(1)),
        (s5, bytes(10), bytes(8,0,0,0,0,0), bytes(2)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1,2,3), List(1,2,3,4))
            ++ genPortPairs(s2, List(1), List(1,2,3,4,5))
            ++ genPortPairs(s3, List(1), List(1,2))
            ++ genPortPairs(s4, List(1), List(1,2))
            ++ genPortPairs(s5, List(1), List(1,2))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_client    = LST_to_SM(lst_Client, a_Client)
        val msa_http      = LST_to_SM(lst_HTTP, a_HTTP)
        val msa_inventory = LST_to_SM(lst_Inventory, a_Inventory)
        val msa_payment   = LST_to_SM(lst_Payment, a_Payment)
        val msa_shipping  = LST_to_SM(lst_Shipping, a_Shipping)
        val msa_bank      = LST_to_SM(lst_Bank, a_Bank)
        val msa_email     = LST_to_SM(lst_Email, a_Email)

        val monitor_switches = List(
            (s1, restrict(List(a_Client, a_Bank, a_Email),
                    parallel(List(
                        monitor(a_Client, msa_client),
                        monitor(a_Bank, msa_bank),
                        monitor(a_Email, msa_email))))),
            (s2, monitor(a_HTTP, msa_http)),
            (s3, monitor(a_Inventory, msa_inventory)),
            (s4, monitor(a_Payment, msa_payment)),
            (s5, monitor(a_Shipping, msa_shipping)))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()
    s2.disconnect()
    s3.disconnect()
    s4.disconnect()
    s5.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "Store",
    List(a_Client, a_HTTP, a_Payment, a_Inventory, a_Shipping, a_Bank, a_Email),
    List(l_PlaceOrder, l_GetSKUDetails, l_SKUDetails, l_ConfirmOrder, l_VerifyCC, l_ValidCC, l_InvalidCC, l_MakeCharge, l_MakeOrder, l_ShipOrder, l_ValidOrder, l_InvalidOrder, l_SendEmail, l_Success, l_CancelOrder, l_Cancel)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)