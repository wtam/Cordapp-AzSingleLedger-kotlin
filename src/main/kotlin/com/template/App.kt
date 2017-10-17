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
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker.Step




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
    class Create : Commands
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
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "com.template.IOUContract"
    }

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

            // Constraints on the signers. Note: Now Change from one to 2 party to sign
            /*
            "There must only be one signer." using (command.signers.toSet().size == 1)
            "The signer must be the lender." using (command.signers.contains(out.lender.owningKey))
            */
            // Constraints on the signers.
            "There must be two signers." using (command.signers.toSet().size == 2)
            "The borrower and lender must be signers." using (command.signers.containsAll(listOf(
                    out.borrower.owningKey, out.lender.owningKey)))
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
    //override val progressTracker = ProgressTracker()
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction based on new IOU.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }
    override val progressTracker = tracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // We create a transaction builder
        //val txBuilder = TransactionBuilder(notary = notary)
        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.
        val iouState = IOUState(iouValue, serviceHub.myInfo.legalIdentities.first(), otherParty)
        val txCommand = Command(IOUContract.Create(), iouState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(iouState, IOUContract.IOU_CONTRACT_ID), txCommand)

        /*
        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val outputContract = IOUContract::class.jvmName
        val outputContractAndState = StateAndContract(outputState, outputContract)
        val cmd = Command(IOUContract.Create(), ourIdentity.owningKey)

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)
        */

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Stage 4.
        // Now Update the lender’s side of the flow to request the borrower’s signature
        // Creating a session with the other party.
        val otherpartySession = initiateFlow(otherParty)
        progressTracker.currentStep = GATHERING_SIGS
        // Obtaining the counterparty's signature.
        //val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherpartySession), CollectSignaturesFlow.tracker()))
        ///val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, setOf(otherpartySession), GATHERING_SIGS.childProgressTracker()))
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, setOf(otherpartySession), CollectSignaturesFlow.tracker()))

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Finalising the transaction.
        ///subFlow(FinalityFlow(SignedTx))
        //issue: https://stackoverflow.com/questions/45890569/m14-finalityflow-signature-verification-failed
        //subFlow(FinalityFlow(fullySignedTx)
        subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
    }
}
/*
class Initiator : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return Unit
    }
}*/

/*
//@InitiatedBy(Initiator::class)
@InitiatedBy(IOUFlow::class)
class Responder(val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return Unit
    }
}
*/

//@InitiatedBy(Initiator::class)
@InitiatedBy(IOUFlow::class)
//class Responder(val otherParty: Party) : FlowLogic<Unit>() {
//updating the responder, borrower to sign the Tx too
//class IOUFlowResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
class Responder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        //lender’s flow, which will respond to the borrower’s attempt to gather our signature
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction." using (output is IOUState)
                val iou = output as IOUState
                "The IOU's value can't be too high." using (iou.value < 100)
            }
        }
        subFlow(signTransactionFlow)
    }
}


// ***********
// * Plugins *
// ***********
// Serialization whitelist (only needed for 3rd party classes, but we use a local example here).
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// Not annotated with @CordaSerializable just for use with manual whitelisting above.
data class TemplateData(val payload: String)

class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    //override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::TemplateApi))
    //using the WebAPI that hookup to the templateWeb
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::HTTPApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/template
            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )
}
