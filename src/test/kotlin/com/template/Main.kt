package com.template

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.driver.driver

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 *
 * To debug your CorDapp:
 *
 * 1. Run the "Run Template CorDapp" run configuration.
 * 2. Wait for all the nodes to start.
 * 3. Note the debug ports for each node, which should be output to the console. The "Debug CorDapp" configuration runs
 *    with port 5007, which should be "PartyA". In any case, double-check the console output to be sure.
 * 4. Set your breakpoints in your CorDapp code.
 * 5. Run the "Debug CorDapp" remote debug run configuration.
 */
//Country name must be 2 char ISO, band  word "node, server": https://github.com/corda/corda/pull/1447/commits/a7e3b66f62f42abad27e02fdf4859b1d3443dc6e
fun main(args: Array<String>) {
    // No permissions required as we are not invoking flows.
    val user = User("user1", "test", permissions = setOf())
    driver(isDebug = true) {
        startNode(providedName = CordaX500Name("Controller", "London", "GB"), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
        val (NodeA, NodeB, NodeC) = listOf(
                startNode(providedName = CordaX500Name("HSBC", "Hong Kong", "HK"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("BankOfChina", "Hong Kong", "HK"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("DBS", "Singapore", "SG"), rpcUsers = listOf(user))).map { it.getOrThrow() }

        startWebserver(NodeA)
        startWebserver(NodeB)
        startWebserver(NodeC)

        waitForAllNodesToFinish()
    }
}