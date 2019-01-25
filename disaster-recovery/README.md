# Disaster Recovery Reference Architecture

Requirements summary:
[X] Messages cannot be lost
[] Messages must be replicated
[] Zero service downtime
[] Messages cannot be delivered more than once
[] Message throughput ?
[] Eventual consistency (i.e. DR site may not have all messages generated from the main site. They are not lost though)

## Set up

1. We are going to deploy a RabbitMQ cluster in Kubernetes using a [Helm chart](https://github.com/helm/charts/blob/master/stable/rabbitmq).

2. We are going to use an Ubuntu docker image which is not yet available in Docker hub (pending to approve PR). Therefore, we are going to build the docker image locally.

  ```
  git clone git@github.com:rabbitmq/rabbitmq.git
  cd rabbitmq
  git checkout compile-openssl-otp
  git pull
  cd 3.7/ubuntu/management
  docker build --build-arg PGP_KEYSERVER="pgpkeys.co.uk" -t rabbitmq-mgt-ubuntu .
  ```

3. Start RabbitMQ server with a local `data` folder created if it does not exist yet. This is so that we don't loose the messages when we stop RabbitMQ server container.
  ```
  ./start-rabbitmq
  ```

4. Start producer configured with all the guarantees to not lose 100 messages. The second command sends 10 messages per second.
  ```
  ./start-producer
  or
  ./start-producer --rate 10
  ```
5. Start consumer configured with all the guarantees to not lose messages. The first command processes messages at maximum rate. The second command processes 5 messages per second.
  ```
  ./start-consumer
  or
  ./start-consumer --consumer-rate 5
  ```
6. To check the current depth of the `transactions` queue, run the following command:
`curl -s -u guest:guest localhost:15672/api/queues/%2F/transactions | jq .messages`


## Google Cloud Platform

### Get the tools
We are going to operate via command-line, not via the UI. For this reason, we need to install `gcloud`, `kubectl` and `helm`.

To install gcloud and kubectl, perform the following steps:

[] [Install the Google Cloud SDK](https://cloud.google.com/sdk/docs/quickstarts), which includes the gcloud command-line tool.
[] After installing Cloud SDK, install the kubectl command-line tool by running the following command:
  ```
  gcloud components install kubectl
  ```

### Connect to gcloud to your project
At this point, you must have an account in GCP and a default project.

```bash
$ gcloud config set project [your PROJECT_ID]
$ gcloud config set compute/zone [your COMPUTE_ZONE or region such as us-west1-a]
```

To see the current configuration run this command:
```bash
$ gcloud config list
```
```
[compute]
region = europe-west1
zone = europe-west1-b
[core]
account = mrosales@pivotal.io
disable_usage_reporting = True
project = cf-rabbitmq

Your active configuration is: [cf-rabbitmq]
```

> Optionally, you can manage your cluster via the GCP console, e.g. https://console.cloud.google.com/kubernetes/clusters

### Create a cluster if you dont have one yet

```bash
$ gcloud container clusters create [CLUSTER_NAME]
```

```bash
gcloud container clusters list
```
```
NAME       LOCATION        MASTER_VERSION  MASTER_IP      MACHINE_TYPE   NODE_VERSION  NUM_NODES  STATUS
cluster-1  europe-west1-c  1.11.5-gke.5    35.205.181.90  n1-standard-1  1.11.5-gke.5  3          RUNNING
```

After creating your cluster, you need to get authentication credentials to interact with the cluster. It automatically generates `kubeconfig` so that we can interact with the cluster with `kubectl`.
```bash
$ gcloud container clusters get-credentials --region=[COMPUTE_ZONE] [CLUSTER_NAME]
```

### Delete our cluster

```
gcloud container clusters delete [CLUSTER_NAME]
```
