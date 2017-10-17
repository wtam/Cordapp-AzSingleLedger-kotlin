![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# This sample is based on CorDapp Kotlin Template release-V1.0 and running on Azure
- include 2 party Sign Txn
- IOUFlow with Web API 

## Getting Set Up

To get started, clone this repository with:

     git clone https://github.com/corda/cordapp-template-kotlin.git

​     
## Building the CorDapp template:

**Unix:** 

     ./gradlew deployNodes

**Windows:**

     gradlew.bat deployNodes

Note: You'll need to re-run this build step after making any changes to
the template for these to take effect on the node.

## Running the Nodes

Once the build finishes, change directories to the folder where the newly
built nodes are located:

     cd build/nodes [Notary, HSBC, BankOfChina, DBS]

The Gradle build script will have created a folder for each node. You'll
see three folders, one for each node and a `runnodes` script. You can
run the nodes with:

**Unix:**

     ./runnodes --log-to-console --logging-level=DEBUG

**Windows:**

    runnodes.bat --log-to-console --logging-level=DEBUG

OR haead into individua and run the jar e.g. java -jar corda.jar, xxx-webserver.jar

Note: NotaryNode is corda74ji-not0.eastasia.cloudapp.azure.com and also use for the NetwrokMAp and I don't run the NetworkMapNode created from the Azure template

## Run the test [Using the command line]
create an IOU of 100 with HSBC->DBS
on HSBC corda console: >start IOUFlow iouValue: 99, otherParty: "O=DBS,L=Singapore,C=SG"
then 		       >run vaultQuery contractStateType: com.template.IOUState

## Interacting with the CorDapp via HTTP [Select the "static template"!! to run the IOU 2 parties sign demo]

The CorDapp defines a couple of HTTP API end-points and also serves some
static web content. Initially, these return generic template responses.

The nodes can be found using the following port numbers, defined in 
`build.gradle`, as well as the `node.conf` file for each node found
under `build/nodes/partyX`:

     HSBC-node: 		http://corda74ji-node0.eastasia.cloudapp.azure.com:10007
     BankOfChin-node: 	http://corda74ji-node1.eastasia.cloudapp.azure.com:10010
	 DBS-node: 			http://corda74ji-node2.eastasia.cloudapp.azure.com:10013


## Using the Example RPC Client

The `ExampleClient.kt` file is a simple utility which uses the client
RPC library to connect to a node and log its transaction activity.
It will log any existing states and listen for any future states. To build 
the client use the following Gradle task:

     ./gradlew runTemplateClient

To run the client:

**Via IntelliJ:**

Select the 'Run Template RPC Client'
run configuration which, by default, connect to PartyA (RPC port 10006). Click the
Green Arrow to run the client.

**Via the command line:**

Run the following Gradle task:

     ./gradlew runTemplateClient
     
Note that the template rPC client won't output anything to the console as no state 
objects are contained in either PartyA's or PartyB's vault.

