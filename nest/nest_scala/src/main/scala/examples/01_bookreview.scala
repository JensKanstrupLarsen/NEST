package bookreview

import sessiontypes._
import sessiontypes.LocalSessionType._
import controlplane._
import p4rtype._
import scala.io.StdIn.readLine
import com.google.protobuf.ByteString

val a_BookClient  = newAgent("Client", 1, bytes(10,0,1,1))
val a_BookInfo    = newAgent("Info", 2, bytes(10,0,2,2))
val a_BookReview  = newAgent("Review", 3, bytes(10,0,3,3))
val a_BookDetails = newAgent("Details", 4, bytes(10,0,4,4))
val a_BookRatings = newAgent("Ratings", 5, bytes(10,0,5,5))

val l_Request         = newLabel("Request", 1)
val l_ReviewRequest   = newLabel("RqsReview", 2)
val l_DetailRequest   = newLabel("RqsDetail", 3)
val l_RatingsRequest  = newLabel("RqsRatings", 4)
val l_RatingsResponse = newLabel("RspRatings", 5)
val l_ReviewResponse  = newLabel("RspReview", 6)
val l_DetailResponse  = newLabel("RspDetail", 7)
val l_Response        = newLabel("Response", 8)

val lst_BookClient =
    IntChoice(Map((a_BookInfo, l_Request) ->
    ExtChoice(a_BookInfo, Map(l_Response ->
    End()))))

val lst_BookInfo =
    ExtChoice(a_BookClient, Map(l_Request ->
    IntChoice(Map((a_BookReview, l_ReviewRequest) ->
    IntChoice(Map((a_BookDetails, l_DetailRequest) ->
    ExtChoice(a_BookReview, Map(l_ReviewResponse ->
    ExtChoice(a_BookDetails, Map(l_DetailResponse ->
    IntChoice(Map((a_BookClient, l_Response) ->
    End()))))))))))))

val lst_BookReview =
    ExtChoice(a_BookInfo, Map(l_ReviewRequest ->
    IntChoice(Map((a_BookRatings, l_RatingsRequest) ->
    ExtChoice(a_BookRatings, Map(l_RatingsResponse ->
    IntChoice(Map((a_BookInfo, l_ReviewResponse) ->
    End()))))))))

val lst_BookDetails =
    ExtChoice(a_BookInfo, Map(l_DetailRequest ->
    IntChoice(Map((a_BookInfo, l_DetailResponse) ->
    End()))))

val lst_BookRatings =
    ExtChoice(a_BookReview, Map(l_RatingsRequest ->
    IntChoice(Map((a_BookReview, l_RatingsResponse) ->
    End()))))

def setup(isTCP : Boolean, useMonitors : Boolean) =
    val s1 = constructSwitch(0, "127.0.0.1", 50051, isTCP)
    val s2 = constructSwitch(1, "127.0.0.1", 50052, isTCP)
    val s3 = constructSwitch(2, "127.0.0.1", 50053, isTCP)
    val s4 = constructSwitch(3, "127.0.0.1", 50054, isTCP)
    val s5 = constructSwitch(4, "127.0.0.1", 50055, isTCP)
    val s6 = constructSwitch(5, "127.0.0.1", 50056, isTCP)
    val s7 = constructSwitch(6, "127.0.0.1", 50057, isTCP)

    val ipv4_rules = List(
        (s1, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s1, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s1, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(3)),
        (s1, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),
        (s1, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(4)),

        (s2, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(1)),
        (s2, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s2, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(1)),
        (s2, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(3)),
        (s2, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(1)),

        (s3, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s3, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(1)),
        (s3, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(3)),
        (s3, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),
        (s3, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(2)),

        (s4, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s4, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(1)),
        (s4, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)),
        (s4, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(1)),
        (s4, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(2)),

        (s5, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s5, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(3)),
        (s5, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(1)),
        (s5, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),
        (s5, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(2)),

        (s6, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s6, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s6, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)),
        (s6, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(2)),
        (s6, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(1)),

        (s7, bytes(10,0,1,1), bytes(8,0,0,0,1,17), bytes(2)),
        (s7, bytes(10,0,2,2), bytes(8,0,0,0,2,34), bytes(2)),
        (s7, bytes(10,0,3,3), bytes(8,0,0,0,3,51), bytes(2)),
        (s7, bytes(10,0,4,4), bytes(8,0,0,0,4,68), bytes(1)),
        (s7, bytes(10,0,5,5), bytes(8,0,0,0,5,85), bytes(2)))

    for ((switch, dst_IP, dst_MAC, p_out) <- ipv4_rules)
        switch.addForwardingRule(dst_IP, dst_MAC, p_out)

    if useMonitors then
        val port_pairs =
            genPortPairs(s1, List(1), List(1,2,3,4))
            ++ genPortPairs(s2, List(), List(1,2,3))
            ++ genPortPairs(s3, List(1), List(1,2,3))
            ++ genPortPairs(s4, List(), List(1,2))
            ++ genPortPairs(s5, List(1), List(1,2,3))
            ++ genPortPairs(s6, List(1), List(1,2))
            ++ genPortPairs(s7, List(1), List(1,2))

        for ((switch, p_in, p_out, is_in, is_eg) <- port_pairs)
            switch.addPortCheck(p_in, p_out, is_in, is_eg)

        val msa_client  = LST_to_SM(lst_BookClient, a_BookClient)
        val msa_info    = LST_to_SM(lst_BookInfo, a_BookInfo)
        val msa_review  = LST_to_SM(lst_BookReview, a_BookReview)
        val msa_details = LST_to_SM(lst_BookDetails, a_BookDetails)
        val msa_ratings = LST_to_SM(lst_BookRatings, a_BookRatings)

        val monitor_switches = List(
            (s1, monitor(a_BookClient, msa_client)),
            (s3, monitor(a_BookInfo, msa_info)),
            (s5, monitor(a_BookReview, msa_review)),
            (s6, monitor(a_BookRatings, msa_ratings)),
            (s7, monitor(a_BookDetails, msa_details)))

        for ((switch, network) <- monitor_switches)
            switch.addMonitor(network)

    s1.disconnect()
    s3.disconnect()
    s5.disconnect()
    s6.disconnect()
    s7.disconnect()

@main def genMainFile() = println(controlplane.genMainFile(
    "BookReview",
    List(a_BookClient, a_BookInfo, a_BookReview, a_BookDetails, a_BookRatings),
    List(l_Request, l_Response, l_ReviewRequest, l_ReviewResponse, l_DetailRequest, l_DetailResponse, l_RatingsRequest, l_RatingsResponse)))
@main def setupUDP_NoMonitor() = setup(false, false)
@main def setupUDP_Monitored() = setup(false, true)
@main def setupTCP_NoMonitor() = setup(true, false)
@main def setupTCP_Monitored() = setup(true, true)
