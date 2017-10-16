package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.LedgerTransaction
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import net.corda.core.contracts.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.flows.*
import kotlin.reflect.jvm.jvmName


// *****************
// * API Endpoints *
// *****************
@Path("template")
class TemplateApi(val services: CordaRPCOps) {
    // Accessible at /api/template/templateGetEndpoint.
    @GET
    @Path("templateGetEndpoint")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok(mapOf("message" to "Template GET endpoint.")).build()
    }
}

//Step1: start with the contract state interface. https://docs.corda.net/hello-world-state.html
interface ContractState {
    // The list of entities considered to have a stake in this state.
    val participants: List<AbstractParty>
}

// /Step2 define the contract state class and this case is the IOUState
class IOUState(val value: Int,
               val lender: Party,
               val borrower: Party) : ContractState {
    override val participants get() = listOf(lender, borrower)
}

//Step3 define the contract interface
interface Contract {
    // Implements the contract constraints in code.
    @Throws(IllegalArgumentException::class)
    fun verify(tx: LedgerTransaction)
}

//Step4a define the
//interface CommandData  //To avoid the CommandData ...not within its bound during build, better specify the commandDataType
// Ref: https://docs.corda.net/tutorial-contract.html

interface Commands : CommandData {
    /*class Move : TypeOnlyCommandData(), Commands
    class Redeem : TypeOnlyCommandData(), Commands
    class Issue : TypeOnlyCommandData(), Commands*/
    class Action : Commands
}

//Step4b Now implement the Contract Class by replacing the TemplateContract to below IOUContract
// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val TEMPLATE_CONTRACT_ID = "com.template.TemplateContract"
/*
open class TemplateContract : Contract {
    // The verify() function of the contract for each of the transaction's input and output states must not throw an
    // exception for a transaction to be considered valid.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
    }
} */
class IOUContract : Contract {
    // Our Create command.
    //class Create : CommandData
    class Create: Commands

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Create>()

        requireThat {
            // Constraints on the shape of the transaction.
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "There should be one output state of type IOUState." using (tx.outputs.size == 1)

            // IOU-specific constraints.
            val out = tx.outputs.single().data as IOUState
            "The IOU's value must be non-negative." using (out.value > 0)
            "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)

            // Constraints on the signers.
            "There must only be one signer." using (command.signers.toSet().size == 1)
            "The signer must be the lender." using (command.signers.contains(out.lender.owningKey))
        }
    }
}

// *********
// * State *
// *********
//Replace this templateState with the step 1 contractState above and also replace its in the Client.kt!!
/*
class TemplateState(val data: String) : ContractState {

    override val participants: List<AbstractParty> get() = listOf()
} */

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
//Step5 not implement the FlowLogic class
class IOUFlow(val iouValue: Int,
              val otherParty: Party) : FlowLogic<Unit>() {

    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // We create a transaction builder
        val txBuilder = TransactionBuilder(notary = notary)

        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val outputContract = IOUContract::class.jvmName
        val outputContractAndState = StateAndContract(outputState, outputContract)
        val cmd = Command(IOUContract.Create(), ourIdentity.owningKey)

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Finalising the transaction.
        subFlow(FinalityFlow(signedTx))
    }
}
/*
class Initiator : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return Unit
    }
}*/

//@InitiatedBy(Initiator::class)
@InitiatedBy(IOUFlow::class)
class Responder(val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return Unit
    }
}

// Serialization whitelist (only needed for 3rd party classes, but we use a local example here).
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// Not annotated with @CordaSerializable just for use with manual whitelisting above.
data class TemplateData(val payload: String)

class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::TemplateApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/template
            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )
}
